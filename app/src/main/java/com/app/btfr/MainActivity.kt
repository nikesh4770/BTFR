package com.app.btfr

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.app.btfr.ui.theme.BTFRTheme

class MainActivity : ComponentActivity() {

    // Receives the EXITAPP broadcast from the service MonitorService
    // when the user clicks the "Stop" notification action,
    // or when the service stops itself after processing a file.
    // In either case, we finish() the activity to exit the app.
    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MonitorService.ACTION_EXIT_APP) {
                finish()
            }
        }
    }

    // Runtime permission launcher
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            checkAllFilesAccessThenStart()
        }
        // If denied, the service simply won't process files — the UI stays visible.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this, exitReceiver,
            IntentFilter(MonitorService.ACTION_EXIT_APP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent {
            BTFRTheme {
                MainScreen(onStop = ::stopApp)
            }
        }
        requestPermissionsAndStart()
    }

    // Picks up the return from the "All files access" settings screen
    override fun onResume() {
        super.onResume()
        if (Environment.isExternalStorageManager()) startMonitorService()
    }

    override fun onDestroy() {
        unregisterReceiver(exitReceiver)
        super.onDestroy()
    }

    private fun stopApp() {
        stopService(Intent(this, MonitorService::class.java))
        finish()
    }

    private fun requestPermissionsAndStart() {
        val needed = buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                // READ_EXTERNAL_STORAGE covers Bluetooth dir on API 31–32
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) checkAllFilesAccessThenStart() else permLauncher.launch(needed.toTypedArray())
    }

    /**
     * On API 33+ the Bluetooth directory is no longer accessible with READ_EXTERNAL_STORAGE.
     * MANAGE_EXTERNAL_STORAGE ("All files access") is required; direct the user to the system
     * settings page if it has not been granted yet.
     */
    private fun checkAllFilesAccessThenStart() {
        if (!Environment.isExternalStorageManager()) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            // startMonitorService() is called from onResume() once the user returns
        } else {
            startMonitorService()
        }
    }

    private fun startMonitorService() {
        // minSdk = 31, so always use startForegroundService
        startForegroundService(Intent(this, MonitorService::class.java))
    }
}

// -------------------------------------------------------------------------
// Compose UI
// -------------------------------------------------------------------------

@Composable
fun MainScreen(onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SEND SMS Monitor in use.\nClick STOP to force exit",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "STOP",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        }
    }
}
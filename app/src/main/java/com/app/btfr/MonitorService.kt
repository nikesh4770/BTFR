package com.app.btfr

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"

        /** Broadcast action sent to the activity when EXITAPP is processed. */
        const val ACTION_EXIT_APP = "com.app.btfr.ACTION_EXIT_APP"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: AppNotificationManager
    private lateinit var commandProcessor: CommandProcessor
    private var fileObserver: FileObserver? = null

    // Guard against starting the observer more than once per service lifecycle
    private var monitoring = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = AppNotificationManager(this)
        commandProcessor    = CommandProcessor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            AppNotificationManager.NOTIFICATION_ID,
            notificationManager.buildForegroundNotification()
        )
        if (!monitoring) {
            monitoring = true
            startMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fileObserver?.stopWatching()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        val btDir = File(Environment.getExternalStorageDirectory(), "Bluetooth")
        if (!btDir.exists()) btDir.mkdirs()
        Log.i(TAG, "Watching for .txt files in: ${btDir.absolutePath}")

        fileObserver = object : FileObserver(btDir, CREATE or CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (!path.endsWith(".txt", ignoreCase = true)) return
                val file = File(btDir, path)
                Log.i(TAG, "New file detected: ${file.absolutePath}")
                serviceScope.launch {
                    commandProcessor.processFile(file.absolutePath) {
                        onExitAppCommand()
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun onExitAppCommand() {
        Log.i(TAG, "EXITAPP — broadcasting and stopping service")
        sendBroadcast(Intent(ACTION_EXIT_APP))
        stopSelf()
    }
}
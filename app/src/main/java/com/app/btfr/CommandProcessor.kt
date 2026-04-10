package com.app.btfr

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/** Commands recognised in a received Bluetooth text file. */
enum class Command(val token: String) {
    SEND_SMS("SENDSMS"),
    SLEEP("SLEEP"),
    EXIT_APP("EXITAPP");

    companion object {
        /** Returns the matching [Command] for [line], or `null` if unrecognised. */
        fun from(line: String): Command? = entries.firstOrNull { line.startsWith(it.token) }
    }
}

class CommandProcessor(private val context: Context) {

    companion object {
        private const val TAG = "CommandProcessor"
    }

    /**
     * Reads [filePath] line-by-line and executes each recognised command in order.
     * Suspends during SLEEP commands so the coroutine remains cancellable.
     * Calls [onExitApp] then returns immediately when EXITAPP is encountered.
     */
    suspend fun processFile(filePath: String, onExitApp: () -> Unit) {
        Log.i(TAG, "Processing file: $filePath")
        try {
            val lines = File(filePath).readLines()
            for (line in lines) {
                if (!currentCoroutineContext().isActive) break

                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                when (Command.from(trimmed)) {
                    Command.SEND_SMS  -> handleSendSms(trimmed)
                    Command.SLEEP     -> handleSleep(trimmed)
                    Command.EXIT_APP  -> {
                        Log.i(TAG, "EXITAPP received")
                        onExitApp()
                        return
                    }
                    null -> Log.w(TAG, "Unknown command ignored: $trimmed")
                }
            }
            Log.i(TAG, "File processing complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file: $filePath", e)
        }
    }

    /**
     * Handles a SENDSMS command of the form: `SENDSMS|<number>|<message>`
     *
     * The pipe delimiter is limited to 3 parts so any `|` characters within
     * the message body are preserved as literal text.
     * Long messages (>160 chars) are split into multipart SMS automatically.
     *
     * @param line The raw command line from the file.
     */
    private fun handleSendSms(line: String) {
        val parts = line.split("|", limit = 3)
        if (parts.size < 3) {
            Log.w(TAG, "Malformed SENDSMS line: $line")
            return
        }
        val number  = parts[1].trim()
        val message = parts[2].trim()

        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
            val segments = smsManager.divideMessage(message)
            if (segments.size == 1) {
                smsManager.sendTextMessage(number, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(number, null, segments, null, null)
            }
            Log.i(TAG, "SMS sent → $number")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $number", e)
        }
    }

    /**
     * Handles a SLEEP command of the form: `SLEEP|<seconds>`
     *
     * Suspends the coroutine for the requested duration, keeping it cancellable
     * so the STOP button can interrupt a sleep immediately.
     * The value is clamped to the range 1–9999 seconds.
     *
     * @param line The raw command line from the file.
     */
    private suspend fun handleSleep(line: String) {
        val parts = line.split("|", limit = 2)
        if (parts.size < 2) {
            Log.w(TAG, "Malformed SLEEP line: $line")
            return
        }
        val seconds = parts[1].trim().toLongOrNull()?.coerceIn(1L, 9999L) ?: run {
            Log.w(TAG, "Invalid SLEEP value: ${parts[1]}")
            return
        }
        Log.i(TAG, "Sleeping for $seconds second(s)")
        delay(seconds * 1_000L)
    }
}
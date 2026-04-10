package com.app.btfr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Single source of truth for all notification concerns in the app.
 *
 * Responsibilities:
 * - Defines the notification channel (created once on init).
 * - Builds the foreground [Notification] shown while the monitor is running.
 * - Exposes the stable [NOTIFICATION_ID] used by [MonitorService.startForeground].
 */
class AppNotificationManager(private val context: Context) {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID      = "btfr_monitor_channel"
    }

    private val systemManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    init {
        createChannel()
    }

    /**
     * Builds the persistent foreground notification displayed while the
     * monitor service is running. The notification is non-dismissible ([ongoing]).
     */
    fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    /**
     * Creates the notification channel required for API 26+.
     * Safe to call multiple times — the system ignores duplicate registrations.
     */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        systemManager.createNotificationChannel(channel)
    }
}
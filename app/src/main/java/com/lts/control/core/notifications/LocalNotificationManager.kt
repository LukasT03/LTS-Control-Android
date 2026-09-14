package com.lts.control.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lts.control.MainActivity
import com.lts.control.R

class LocalNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS_ID = "respooler_alerts"
        const val CHANNEL_PROGRESS_ID = "respooler_progress"

        const val NOTIFICATION_ID_DONE = 1001
        const val NOTIFICATION_ID_AUTO_STOP = 1002
        const val NOTIFICATION_ID_PROGRESS = 2001

        const val ACTION_START = "com.lts.control.action.START"
        const val ACTION_PAUSE = "com.lts.control.action.PAUSE"
        const val ACTION_STOP = "com.lts.control.action.STOP"

        var isAppInForeground: Boolean = false
    }

    init {
        createChannels()
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.set_footer_notifications)
                enableVibration(true)
            }

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                context.getString(R.string.notif_channel_progress),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.set_footer_ongoing_notification)
                setShowBadge(false)
            }

            nm.createNotificationChannel(alertChannel)
            nm.createNotificationChannel(progressChannel)
        }
    }

    fun notifyDone(enabled: Boolean) {
        if (!enabled || isAppInForeground) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_done_title))
            .setContentText(context.getString(R.string.notif_done_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DONE, notification)
        } catch (_: SecurityException) {
        }
    }

    fun notifyAutoStop(enabled: Boolean) {
        if (!enabled || isAppInForeground) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val startIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_START
        }
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_autostop_title))
            .setContentText(context.getString(R.string.notif_autostop_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_start), startPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AUTO_STOP, notification)
        } catch (_: SecurityException) {
        }
    }
}

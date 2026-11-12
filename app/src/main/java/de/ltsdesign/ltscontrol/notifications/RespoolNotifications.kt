package de.ltsdesign.ltscontrol.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.ltsdesign.ltscontrol.MainActivity
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.data.AppPreferences

private const val TAG = "Notifications"

/**
 * Notifies when the Respooler finishes or stops on its own, like the iOS app. While the board
 * respools, [RespoolService] keeps the app running in the background, so its status still arrives.
 */
class RespoolNotifications(context: Context, private val preferences: AppPreferences) {
    companion object {
        private const val ALERT_CHANNEL = "alerts"
        private const val ONGOING_CHANNEL = "ongoing"
        const val ONGOING_ID = 1
        const val DONE_ID = 2
        const val AUTO_STOP_ID = 3
    }

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Set by the activity; in the foreground the UI already shows the state. */
    var isAppVisible = false

    private var isConnected = false
    private var state = DeviceState.Idle
    private var isServiceRunning = false

    init {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(ALERT_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(this.context.getString(R.string.channel_alerts))
                    .build(),
                NotificationChannelCompat.Builder(ONGOING_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(this.context.getString(R.string.channel_ongoing))
                    .setShowBadge(false)
                    .build(),
            ),
        )
    }

    /** Whether the system lets the app post notifications at all (on Android 13+ the runtime permission). */
    fun isAllowed(): Boolean = manager.areNotificationsEnabled()

    val isEnabled: Boolean get() = preferences.notificationsEnabled && isAllowed()

    fun setEnabled(enabled: Boolean) {
        preferences.notificationsEnabled = enabled
        sync(isConnected, state)
    }

    /** Re-checks after the user may have changed the permission in the system settings. */
    fun refresh() = sync(isConnected, state)

    fun notifyDone() = post(
        DONE_ID,
        alert(R.string.notification_done_title, R.string.notification_done_text).build(),
    )

    fun notifyAutoStop() {
        val start = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, StartActionReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            AUTO_STOP_ID,
            alert(R.string.notification_auto_stop_title, R.string.notification_auto_stop_text)
                .addAction(0, context.getString(R.string.start_respooler), start)
                .build(),
        )
    }

    /**
     * Keeps the service running while the board respools, pauses or waits after an Auto-Stop,
     * so the connection survives in the background until the result arrives.
     */
    fun sync(isConnected: Boolean, state: DeviceState) {
        this.isConnected = isConnected
        this.state = state
        // A new run makes the last result obsolete.
        if (isConnected && state == DeviceState.Running) {
            manager.cancel(DONE_ID)
            manager.cancel(AUTO_STOP_ID)
        }

        val shouldRun = isEnabled && isConnected && canRunService() &&
            state in setOf(DeviceState.Running, DeviceState.Paused, DeviceState.AutoStop)
        when {
            shouldRun && !isServiceRunning -> {
                try {
                    ContextCompat.startForegroundService(context, Intent(context, RespoolService::class.java))
                    isServiceRunning = true
                } catch (e: IllegalStateException) {
                    // Android 12+ may refuse to start it from the background.
                    Log.e(TAG, "Service not started", e)
                }
            }
            shouldRun -> post(ONGOING_ID, ongoingNotification(), always = true)
            isServiceRunning -> {
                context.stopService(Intent(context, RespoolService::class.java))
                isServiceRunning = false
            }
        }
    }

    /** A connected device service needs the Bluetooth permission, otherwise Android refuses it with an exception. */
    private fun canRunService(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** The quiet notification of [RespoolService] with the current state. */
    fun ongoingNotification() = NotificationCompat.Builder(context, ONGOING_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.title_respooler))
        .setContentText(
            context.getString(
                when (state) {
                    DeviceState.Paused -> R.string.state_paused
                    DeviceState.AutoStop -> R.string.state_auto_stop
                    else -> R.string.state_running
                },
            ),
        )
        .setContentIntent(openApp())
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()

    private fun alert(title: Int, text: Int) = NotificationCompat.Builder(context, ALERT_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(title))
        .setContentText(context.getString(text))
        .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** Results only notify in the background, the ongoing notification is updated in any case. */
    @SuppressLint("MissingPermission") // isEnabled checks the permission
    private fun post(id: Int, notification: Notification, always: Boolean = false) {
        if (!isEnabled || (!always && isAppVisible)) return
        manager.notify(id, notification)
    }
}

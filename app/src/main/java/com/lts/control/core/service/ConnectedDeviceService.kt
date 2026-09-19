package com.lts.control.core.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lts.control.MainActivity
import com.lts.control.R
import com.lts.control.core.ble.model.DeviceState
import com.lts.control.core.notifications.LocalNotificationManager
import com.lts.control.core.notifications.NotificationActionReceiver
import com.lts.control.core.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class ConnectedDeviceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isStartedAsForeground = false
    private var statusJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Repeated start requests must not stack up extra collectors
        if (statusJob?.isActive == true) return START_STICKY
        val repo = DeviceRepository.getInstance(applicationContext)

        statusJob = serviceScope.launch {
            // Only rebuild the notification when something it shows changes
            repo.status.distinctUntilChanged { a, b ->
                a.deviceState == b.deviceState &&
                    a.progress.toInt() == b.progress.toInt() &&
                    a.remainingTime == b.remainingTime &&
                    a.ongoingNotificationEnabled == b.ongoingNotificationEnabled
            }.collectLatest { status ->
                val state = status.deviceState
                val isConnected = repo.isConnected.value

                if (!isConnected || state == DeviceState.IDLE || !status.ongoingNotificationEnabled) {
                    stopSelf()
                    return@collectLatest
                }

                val notification = buildNotification(status)
                if (!isStartedAsForeground) {
                    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    } else {
                        0
                    }
                    ServiceCompat.startForeground(
                        this@ConnectedDeviceService,
                        LocalNotificationManager.NOTIFICATION_ID_PROGRESS,
                        notification,
                        serviceType
                    )
                    isStartedAsForeground = true
                } else {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(LocalNotificationManager.NOTIFICATION_ID_PROGRESS, notification)
                }
            }
        }

        return START_STICKY
    }

    private fun buildNotification(status: com.lts.control.core.state.DeviceStatus): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remSeconds = status.remainingTime ?: 0
        val mm = remSeconds / 60
        val ss = remSeconds % 60
        val timeString = String.format(Locale.US, "-%02d:%02d", mm, ss)
        val progressInt = status.progress.toInt().coerceIn(0, 100)

        val stateName = when (status.deviceState) {
            DeviceState.RUNNING -> getString(R.string.home_state_running)
            DeviceState.PAUSED -> getString(R.string.home_state_paused)
            DeviceState.AUTO_STOP -> getString(R.string.home_state_auto_stop)
            DeviceState.DONE -> getString(R.string.home_state_done)
            DeviceState.UPDATING -> getString(R.string.home_state_updating)
            else -> getString(R.string.home_state_idle)
        }

        val builder = NotificationCompat.Builder(this, LocalNotificationManager.CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LTS Respooler — $stateName")
            .setContentText("$progressInt %   $timeString")
            .setProgress(100, progressInt, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(this, NotificationActionReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        when (status.deviceState) {
            DeviceState.RUNNING -> {
                builder.addAction(0, getString(R.string.action_pause), createActionPendingIntent(LocalNotificationManager.ACTION_PAUSE, 10))
            }
            DeviceState.PAUSED -> {
                builder.addAction(0, getString(R.string.action_stop), createActionPendingIntent(LocalNotificationManager.ACTION_STOP, 11))
                builder.addAction(0, getString(R.string.action_start), createActionPendingIntent(LocalNotificationManager.ACTION_START, 12))
            }
            DeviceState.AUTO_STOP -> {
                builder.addAction(0, getString(R.string.action_start), createActionPendingIntent(LocalNotificationManager.ACTION_START, 13))
            }
            DeviceState.DONE -> {
                builder.addAction(0, getString(R.string.action_stop), createActionPendingIntent(LocalNotificationManager.ACTION_STOP, 14))
            }
            else -> {}
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isStartedAsForeground = false
    }
}

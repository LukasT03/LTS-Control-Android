package com.lts.control.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lts.control.core.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val repo = DeviceRepository.getInstance(context.applicationContext)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent?.action) {
                    LocalNotificationManager.ACTION_START -> repo.start()
                    LocalNotificationManager.ACTION_PAUSE -> repo.pause()
                    LocalNotificationManager.ACTION_STOP -> repo.stop()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

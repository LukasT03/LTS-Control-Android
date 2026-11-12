package de.ltsdesign.ltscontrol.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import de.ltsdesign.ltscontrol.LtsControlApplication

/**
 * Runs while the board respools, so Android doesn't freeze or end the app in the background and
 * the done and Auto-Stop status still arrive. Started and stopped by [RespoolNotifications].
 */
class RespoolService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = (application as LtsControlApplication).notifications
        try {
            ServiceCompat.startForeground(
                this,
                RespoolNotifications.ONGOING_ID,
                notifications.ongoingNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: RuntimeException) {
            // E.g. the Bluetooth permission was revoked; without it there's no connection to keep anyway.
            Log.e("RespoolService", "Not started in the foreground", e)
            stopSelf()
        }
        // After the process ended there is no connection to keep.
        return START_NOT_STICKY
    }
}

package de.ltsdesign.ltscontrol.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import de.ltsdesign.ltscontrol.LtsControlApplication

/** "Start Respooler" in the Auto-Stop notification; starts the board without opening the app. */
class StartActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(RespoolNotifications.AUTO_STOP_ID)
        val app = context.applicationContext as LtsControlApplication
        val pending = goAsync()
        app.bleManager.perform("START") { pending.finish() }
    }
}

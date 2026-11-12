package de.ltsdesign.ltscontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.ltsdesign.ltscontrol.bluetooth.DeviceState
import de.ltsdesign.ltscontrol.ui.LtsControlApp
import de.ltsdesign.ltscontrol.ui.theme.LtsControlTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as LtsControlApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!showDemoIfRequested(intent)) app.bleManager.restoreConnection()

        setContent {
            LtsControlTheme {
                LtsControlApp(app.bleManager, app.pairing, app.preferences, app.notifications, app.firmwareUpdates)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.notifications.isAppVisible = true
    }

    override fun onStop() {
        super.onStop()
        app.notifications.isAppVisible = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showDemoIfRequested(intent)
    }

    /** Debug builds started with `--es demo running|paused` show a board mid-respool for screenshots. */
    private fun showDemoIfRequested(intent: Intent): Boolean {
        val demoState = if (BuildConfig.DEBUG) intent.getStringExtra("demo") else null
        demoState ?: return false
        app.bleManager.showDemo(if (demoState == "paused") DeviceState.Paused else DeviceState.Running)
        return true
    }
}

package de.ltsdesign.ltscontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.ltsdesign.ltscontrol.ui.LtsControlApp
import de.ltsdesign.ltscontrol.ui.theme.LtsControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LtsControlTheme {
                LtsControlApp()
            }
        }
    }
}

package com.lts.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.lts.control.core.ble.BleViewModel
import com.lts.control.ui.LtsControlApp
import com.example.ltscontrol.ui.theme.LTSControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[BleViewModel::class.java]

        setContent {
            LTSControlTheme {
                LtsControlApp(vm)
            }
        }
    }
}
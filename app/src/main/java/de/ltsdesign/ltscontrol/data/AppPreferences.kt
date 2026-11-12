package de.ltsdesign.ltscontrol.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Settings that only concern the app, not the board. */
class AppPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)

    private var fahrenheit by mutableStateOf(store.getBoolean(FAHRENHEIT_KEY, false))

    var temperatureInFahrenheit: Boolean
        get() = fahrenheit
        set(value) {
            fahrenheit = value
            store.edit { putBoolean(FAHRENHEIT_KEY, value) }
        }

    fun formatTemperature(celsius: Int): String =
        if (temperatureInFahrenheit) "${Math.round(celsius * 9 / 5.0 + 32)} °F" else "$celsius °C"

    private companion object {
        const val FAHRENHEIT_KEY = "temperatureInFahrenheit"
    }
}

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

    private var notifications by mutableStateOf(store.getBoolean(NOTIFICATIONS_KEY, false))

    var notificationsEnabled: Boolean
        get() = notifications
        set(value) {
            notifications = value
            store.edit { putBoolean(NOTIFICATIONS_KEY, value) }
        }

    /** Set once a board was chosen; until then the welcome screen opens on launch. */
    var hasCompletedSetup: Boolean
        get() = store.getBoolean(SETUP_KEY, false)
        set(value) = store.edit { putBoolean(SETUP_KEY, value) }

    /** Android shows the permission dialog at most twice, after that only the system settings help. */
    var askedForNotificationPermission: Boolean
        get() = store.getBoolean(ASKED_NOTIFICATIONS_KEY, false)
        set(value) = store.edit { putBoolean(ASKED_NOTIFICATIONS_KEY, value) }

    fun formatTemperature(celsius: Int): String =
        if (temperatureInFahrenheit) "${Math.round(celsius * 9 / 5.0 + 32)} °F" else "$celsius °C"

    private companion object {
        const val FAHRENHEIT_KEY = "temperatureInFahrenheit"
        const val NOTIFICATIONS_KEY = "notificationsEnabled"
        const val ASKED_NOTIFICATIONS_KEY = "askedForNotificationPermission"
        const val SETUP_KEY = "hasCompletedSetup"
    }
}

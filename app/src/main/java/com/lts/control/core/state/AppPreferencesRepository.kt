package com.lts.control.core.state

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lts.control.core.ble.model.BoardVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lts_preferences")

class AppPreferencesRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    object Keys {
        val SPEED_PERCENT = intPreferencesKey("speedPercent")
        val JINGLE_STYLE = intPreferencesKey("jingleStyle")
        val LED_BRIGHTNESS = intPreferencesKey("ledBrightness")
        val FAN_SPEED = intPreferencesKey("fanSpeed")
        val FAN_ALWAYS = booleanPreferencesKey("fanAlways")
        val USE_FILAMENT_SENSOR = booleanPreferencesKey("useFilamentSensor")
        val DIRECTION_REVERSED = booleanPreferencesKey("directionReversed")
        val MOTOR_STRENGTH = intPreferencesKey("motorStrength")
        val TORQUE_LIMIT = intPreferencesKey("torqueLimit")
        val HIGH_SPEED = booleanPreferencesKey("highSpeed")
        val DURATION_AT_80 = intPreferencesKey("durationAt80")
        val TARGET_WEIGHT = intPreferencesKey("targetWeight")
        val SERVO_ANGLE_R = intPreferencesKey("servoAngleR")
        val SERVO_ANGLE_L = intPreferencesKey("servoAngleL")
        val SERVO_STEP_MM = doublePreferencesKey("servoStepMm")
        val SERVO_HOME = stringPreferencesKey("servoHome")

        val BOARD_VARIANT = stringPreferencesKey("boardVariant")
        val BOARD_VERSION = stringPreferencesKey("boardVersion")
        val BOARD_FIRMWARE_VERSION = stringPreferencesKey("boardFirmwareVersion")

        val LAST_DEVICE_ADDRESS = stringPreferencesKey("lastDeviceAddress")
        val HAS_COMPLETED_SETUP = booleanPreferencesKey("hasCompletedSetup")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notificationsEnabled")
        val ONGOING_NOTIFICATION_ENABLED = booleanPreferencesKey("ongoingNotificationEnabled")
        val TEMPERATURE_IN_FAHRENHEIT = booleanPreferencesKey("temperatureInFahrenheit")

        val LATEST_BOARD_FIRMWARE_VERSION = stringPreferencesKey("latestBoardFirmwareVersion")
        val CHANGELOG_CACHE = stringPreferencesKey("FirmwareChangelog.cache.v1")
        val CHANGELOG_SIGNATURE = intPreferencesKey("FirmwareChangelog.entriesSignature")
        val CHANGELOG_HAS_SEEN = booleanPreferencesKey("FirmwareChangelog.hasSeenEntries")
    }

    val preferencesFlow: Flow<Preferences> = context.dataStore.data

    val initialDeviceStatusFlow: Flow<DeviceStatus> = context.dataStore.data.map { prefs ->
        DeviceStatus(
            speedPercent = prefs[Keys.SPEED_PERCENT] ?: 85,
            jingleStyle = prefs[Keys.JINGLE_STYLE] ?: 0,
            ledBrightness = prefs[Keys.LED_BRIGHTNESS] ?: 50,
            fanSpeed = prefs[Keys.FAN_SPEED] ?: 60,
            fanAlways = prefs[Keys.FAN_ALWAYS] ?: false,
            useFilamentSensor = prefs[Keys.USE_FILAMENT_SENSOR] ?: true,
            directionReversed = prefs[Keys.DIRECTION_REVERSED] ?: false,
            motorStrength = prefs[Keys.MOTOR_STRENGTH] ?: 100,
            torqueLimit = prefs[Keys.TORQUE_LIMIT] ?: 0,
            highSpeed = prefs[Keys.HIGH_SPEED] ?: false,
            durationAt80 = prefs[Keys.DURATION_AT_80] ?: 895,
            targetWeight = prefs[Keys.TARGET_WEIGHT] ?: 0,
            servoAngleR = prefs[Keys.SERVO_ANGLE_R] ?: 5,
            servoAngleL = prefs[Keys.SERVO_ANGLE_L] ?: 175,
            servoStepMm = prefs[Keys.SERVO_STEP_MM] ?: 1.75,
            servoHome = prefs[Keys.SERVO_HOME] ?: "R",
            firmwareVersion = prefs[Keys.BOARD_FIRMWARE_VERSION],
            boardVersion = prefs[Keys.BOARD_VERSION],
            boardVariant = prefs[Keys.BOARD_VARIANT]?.let { BoardVariant.fromWireValue(it) } ?: BoardVariant.UNKNOWN,
            temperatureInFahrenheit = prefs[Keys.TEMPERATURE_IN_FAHRENHEIT] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            ongoingNotificationEnabled = prefs[Keys.ONGOING_NOTIFICATION_ENABLED] ?: true
        )
    }

    val lastDeviceAddressFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DEVICE_ADDRESS] }
    val hasCompletedSetupFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAS_COMPLETED_SETUP] ?: false }

    suspend fun saveDeviceSettings(status: DeviceStatus) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEED_PERCENT] = status.speedPercent
            prefs[Keys.JINGLE_STYLE] = status.jingleStyle
            prefs[Keys.LED_BRIGHTNESS] = status.ledBrightness
            prefs[Keys.FAN_SPEED] = status.fanSpeed
            prefs[Keys.FAN_ALWAYS] = status.fanAlways
            prefs[Keys.USE_FILAMENT_SENSOR] = status.useFilamentSensor
            prefs[Keys.DIRECTION_REVERSED] = status.directionReversed
            prefs[Keys.MOTOR_STRENGTH] = status.motorStrength
            prefs[Keys.TORQUE_LIMIT] = status.torqueLimit
            prefs[Keys.HIGH_SPEED] = status.highSpeed
            prefs[Keys.DURATION_AT_80] = status.durationAt80
            prefs[Keys.TARGET_WEIGHT] = status.targetWeight
            prefs[Keys.SERVO_ANGLE_R] = status.servoAngleR
            prefs[Keys.SERVO_ANGLE_L] = status.servoAngleL
            prefs[Keys.SERVO_STEP_MM] = status.servoStepMm
            prefs[Keys.SERVO_HOME] = status.servoHome

            prefs[Keys.BOARD_VARIANT] = status.boardVariant.wireValue
            status.boardVersion?.let { prefs[Keys.BOARD_VERSION] = it }
            status.firmwareVersion?.let { prefs[Keys.BOARD_FIRMWARE_VERSION] = it }

            prefs[Keys.TEMPERATURE_IN_FAHRENHEIT] = status.temperatureInFahrenheit
            prefs[Keys.NOTIFICATIONS_ENABLED] = status.notificationsEnabled
            prefs[Keys.ONGOING_NOTIFICATION_ENABLED] = status.ongoingNotificationEnabled
        }
    }

    suspend fun setLastDeviceAddress(address: String?) {
        context.dataStore.edit { prefs ->
            if (address != null) {
                prefs[Keys.LAST_DEVICE_ADDRESS] = address
            } else {
                prefs.remove(Keys.LAST_DEVICE_ADDRESS)
            }
        }
    }

    suspend fun setHasCompletedSetup(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_SETUP] = completed
        }
    }

    suspend fun setTemperatureUnit(isFahrenheit: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEMPERATURE_IN_FAHRENHEIT] = isFahrenheit
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setOngoingNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONGOING_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setLatestBoardFirmwareVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LATEST_BOARD_FIRMWARE_VERSION] = version
        }
    }

    suspend fun setChangelogCache(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHANGELOG_CACHE] = json
        }
    }

    suspend fun setChangelogSignature(sig: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHANGELOG_SIGNATURE] = sig
        }
    }

    suspend fun setChangelogHasSeen(seen: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHANGELOG_HAS_SEEN] = seen
        }
    }
}

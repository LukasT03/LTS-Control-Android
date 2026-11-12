package de.ltsdesign.ltscontrol.bluetooth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import de.ltsdesign.ltscontrol.data.FirmwareVersion

/**
 * Last known state of the board. Settings and board info are cached in SharedPreferences
 * so the UI shows sensible values before the board has sent its first status.
 */
class DeviceStatus(context: Context) {
    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences("device_status", Context.MODE_PRIVATE)

    // Live values, only valid while connected
    var hasFilament by mutableStateOf(false)
    var progress by mutableStateOf<Double?>(null)
    var remainingTime by mutableStateOf<Int?>(null)
    var chipTemperature by mutableStateOf<Int?>(null)
    var wifiSsid by mutableStateOf<String?>(null)
    var wifiConnected by mutableStateOf<Boolean?>(null)
    var wifiConnectionResult by mutableStateOf<Boolean?>(null)
    var isFanOn by mutableStateOf(false)
    var otaSuccess by mutableStateOf<Boolean?>(null)

    // Board info
    var firmwareVersion by mutableStateOf(store.getString("boardFirmwareVersion", null))
    var boardVersion by mutableStateOf(store.getString("boardVersion", null))
    var boardVariant by mutableStateOf(BoardVariant.fromStored(store.getString("boardVariant", null)))
    var didReceiveBoardVariant by mutableStateOf(false)

    // Settings mirrored from the board
    var speedPercent by mutableIntStateOf(store.getInt("speedPercent", 85))
    var jingleStyle by mutableIntStateOf(store.getInt("jingleStyle", 0))
    var ledBrightness by mutableIntStateOf(store.getInt("ledBrightness", 50))
    var fanSpeed by mutableIntStateOf(store.getInt("fanSpeed", 60))
    var fanAlways by mutableStateOf(store.getBoolean("fanAlways", false))
    var useFilamentSensor by mutableStateOf(store.getBoolean("useFilamentSensor", true))
    var filamentLossMs by mutableIntStateOf(store.getInt("filamentLossMs", 100))
    var resumeAfterPowerLoss by mutableStateOf(store.getBoolean("resumeAfterPowerLoss", false))
    var directionReversed by mutableStateOf(store.getBoolean("directionReversed", false))
    var motorStrength by mutableIntStateOf(store.getInt("motorStrength", 100))
    var torqueLimit by mutableIntStateOf(store.getInt("torqueLimit", 0))
    var highSpeed by mutableStateOf(store.getBoolean("highSpeed", false))
    var durationAt85 by mutableIntStateOf(store.getInt("durationAt85", 840))
    var targetWeight by mutableIntStateOf(store.getInt("targetWeight", 0))
    var servoAngleR by mutableIntStateOf(store.getInt("servoAngleR", 5))
    var servoAngleL by mutableIntStateOf(store.getInt("servoAngleL", 175))
    var servoStepMm by mutableDoubleStateOf(store.getDouble("servoStepMm", 1.75))
    var servoHome by mutableStateOf(store.getString("servoHome", null) ?: "R")

    /**
     * `FIL_T` and `RES` were added in firmware 1.3.3. Based on the cached version, so the
     * settings stay visible while disconnected.
     */
    val supportsFirmware133Settings: Boolean
        get() = firmwareVersion?.let { FirmwareVersion.compare(it, "1.3.3") >= 0 } ?: false

    private var lastSaved: Map<String, Any?>? = null

    /** Writes the cached values, but only if something changed since the last save. */
    fun save() {
        val values = mapOf(
            "speedPercent" to speedPercent,
            "jingleStyle" to jingleStyle,
            "ledBrightness" to ledBrightness,
            "fanSpeed" to fanSpeed,
            "fanAlways" to fanAlways,
            "useFilamentSensor" to useFilamentSensor,
            "filamentLossMs" to filamentLossMs,
            "resumeAfterPowerLoss" to resumeAfterPowerLoss,
            "directionReversed" to directionReversed,
            "motorStrength" to motorStrength,
            "torqueLimit" to torqueLimit,
            "highSpeed" to highSpeed,
            "durationAt85" to durationAt85,
            "targetWeight" to targetWeight,
            "servoAngleR" to servoAngleR,
            "servoAngleL" to servoAngleL,
            "servoStepMm" to servoStepMm,
            "servoHome" to servoHome,
            "boardVariant" to boardVariant.wireValue,
            "boardVersion" to boardVersion,
            "boardFirmwareVersion" to firmwareVersion,
        )
        if (values == lastSaved) return
        lastSaved = values

        store.edit {
            for ((key, value) in values) {
                when (value) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Double -> putLong(key, value.toRawBits())
                    is String -> putString(key, value)
                    null -> remove(key)
                }
            }
        }
    }

    fun resetLiveValues() {
        hasFilament = false
        progress = 0.0
        remainingTime = null
        chipTemperature = null
        wifiSsid = null
        wifiConnected = null
        wifiConnectionResult = null
        isFanOn = false
    }
}

/** SharedPreferences has no doubles, so they are stored as their raw bits. */
private fun SharedPreferences.getDouble(key: String, default: Double): Double =
    if (contains(key)) Double.fromBits(getLong(key, 0)) else default

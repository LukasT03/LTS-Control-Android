package de.ltsdesign.ltscontrol.bluetooth

import kotlinx.serialization.json.JsonElement

enum class DeviceState(val wireCode: String) {
    Idle("I"),
    Running("R"),
    Paused("P"),
    Updating("U"),
    AutoStop("A"),
    Done("D");

    companion object {
        fun fromWireCode(code: String?): DeviceState? = entries.firstOrNull { it.wireCode == code }
    }
}

enum class BoardVariant(val wireValue: String) {
    Unknown("UNK"),
    Standard("STD"),
    Pro("PRO");

    companion object {
        fun fromStored(value: String?): BoardVariant = entries.firstOrNull { it.wireValue == value } ?: Unknown

        /** The board sends the variant as "STD"/"PRO" or, on some firmware versions, as 1/2. */
        fun fromWire(value: JsonElement?): BoardVariant {
            value.string?.let { return fromStored(it.uppercase()) }
            return when (value.double?.let { Math.round(it) }) {
                1L -> Standard
                2L -> Pro
                else -> Unknown
            }
        }
    }
}

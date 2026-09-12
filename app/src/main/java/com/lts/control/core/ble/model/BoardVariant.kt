package com.lts.control.core.ble.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable(with = BoardVariantSerializer::class)
enum class BoardVariant(val wireValue: String) {
    UNKNOWN("UNK"),
    STANDARD("STD"),
    PRO("PRO");

    companion object {
        fun fromWireValue(value: Any?): BoardVariant = when (value) {
            is String -> when (value.uppercase()) {
                "PRO" -> PRO
                "STD" -> STANDARD
                else -> UNKNOWN
            }
            is Number -> when (value.toInt()) {
                2 -> PRO
                1 -> STANDARD
                else -> UNKNOWN
            }
            else -> UNKNOWN
        }
    }
}

object BoardVariantSerializer : KSerializer<BoardVariant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BoardVariant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BoardVariant) {
        encoder.encodeString(value.wireValue)
    }

    override fun deserialize(decoder: Decoder): BoardVariant {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                element.intOrNull?.let { return BoardVariant.fromWireValue(it) }
                BoardVariant.fromWireValue(element.content)
            } else {
                BoardVariant.UNKNOWN
            }
        } else {
            BoardVariant.fromWireValue(decoder.decodeString())
        }
    }
}

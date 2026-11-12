package de.ltsdesign.ltscontrol.bluetooth

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// The firmware sends some booleans as 0/1 (e.g. DIR) and numbers as int or float,
// so values are read as leniently as JSONSerialization does on iOS.

private val JsonElement?.number: JsonPrimitive?
    get() = (this as? JsonPrimitive)?.takeIf { !it.isString && it.content != "null" }

internal val JsonElement?.string: String?
    get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal val JsonElement?.double: Double?
    get() = number?.doubleOrNull

internal val JsonElement?.int: Int?
    get() {
        val value = double ?: return null
        return value.toInt().takeIf { it.toDouble() == value }
    }

internal val JsonElement?.bool: Boolean?
    get() {
        val primitive = number ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.doubleOrNull) {
            0.0 -> false
            1.0 -> true
            else -> null
        }
    }

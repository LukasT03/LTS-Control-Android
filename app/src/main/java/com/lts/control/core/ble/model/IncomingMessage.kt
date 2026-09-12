package com.lts.control.core.ble.model

import kotlinx.serialization.json.*

sealed interface IncomingMessage {
    data class Status(val payload: StatusPayload): IncomingMessage
    data class WifiScan(val ssids: List<String>): IncomingMessage
    data class WifiConnectResult(val ok: Boolean): IncomingMessage
    data class OtaResult(val ok: Boolean): IncomingMessage
    data class Raw(val json: String): IncomingMessage

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(jsonText: String): IncomingMessage {
            return try {
                val element = json.parseToJsonElement(jsonText)
                if (element is JsonObject) {
                    when {
                        "SSID_LIST" in element -> {
                            val list = element["SSID_LIST"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                            WifiScan(list)
                        }
                        "WIFI_CONN_RESULT" in element -> {
                            val ok = element["WIFI_CONN_RESULT"]!!.jsonPrimitive.booleanOrNull == true
                            WifiConnectResult(ok)
                        }
                        "OTA_OK" in element && element.size == 1 -> {
                            val ok = element["OTA_OK"]!!.jsonPrimitive.booleanOrNull == true
                            OtaResult(ok)
                        }
                        else -> {
                            val payload = json.decodeFromJsonElement(StatusPayload.serializer(), element)
                            Status(payload)
                        }
                    }
                } else {
                    Raw(jsonText)
                }
            } catch (_: Throwable) {
                Raw(jsonText)
            }
        }
    }
}

package com.lts.control.core.ble.model

sealed interface IncomingMessage {
    data class Status(val payload: StatusPayload): IncomingMessage
    data class WifiScan(val ssids: List<String>): IncomingMessage
    data class WifiConnectResult(val ok: Boolean): IncomingMessage
    data class OtaResult(val ok: Boolean): IncomingMessage
    data class Raw(val json: String): IncomingMessage
}
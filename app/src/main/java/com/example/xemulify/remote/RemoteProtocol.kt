package com.example.xemulify.remote

import java.security.SecureRandom

object RemoteProtocol {
    const val DEFAULT_PORT = 8888
    const val MAGIC_BYTE: Byte = 0x58 // 'X'

    // Tipos de mensaje
    const val TYPE_INPUT: Byte = 0x01
    const val TYPE_CONNECT_REQ: Byte = 0x02
    const val TYPE_CONNECT_ACK: Byte = 0x03
    const val TYPE_CONNECT_NAK: Byte = 0x04
    const val TYPE_PING: Byte = 0x05
    const val TYPE_PONG: Byte = 0x06
    const val TYPE_DISCONNECT: Byte = 0x07
    const val TYPE_RUMBLE: Byte = 0x08

    // Máscaras de bits para botones (16 bits)
    const val BTN_DPAD_UP: Short    = (1 shl 0).toShort()
    const val BTN_DPAD_DOWN: Short  = (1 shl 1).toShort()
    const val BTN_DPAD_LEFT: Short  = (1 shl 2).toShort()
    const val BTN_DPAD_RIGHT: Short = (1 shl 3).toShort()
    const val BTN_CROSS: Short      = (1 shl 4).toShort()
    const val BTN_CIRCLE: Short     = (1 shl 5).toShort()
    const val BTN_SQUARE: Short     = (1 shl 6).toShort()
    const val BTN_TRIANGLE: Short   = (1 shl 7).toShort()
    const val BTN_L1: Short         = (1 shl 8).toShort()
    const val BTN_R1: Short         = (1 shl 9).toShort()
    const val BTN_L2: Short         = (1 shl 10).toShort()
    const val BTN_R2: Short         = (1 shl 11).toShort()
    const val BTN_START: Short      = (1 shl 12).toShort()
    const val BTN_SELECT: Short     = (1 shl 13).toShort()
    const val BTN_L3: Short         = (1 shl 14).toShort()
    const val BTN_R3: Short         = (1 shl 15).toShort()

    // Conjunto de caracteres legibles (sin 0/O ni 1/I para evitar confusiones al teclear)
    private const val PIN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate6CharPin(): String {
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            sb.append(PIN_CHARS[random.nextInt(PIN_CHARS.length)])
        }
        return sb.toString()
    }

    fun makeQrPayload(ip: String, port: Int, pin: String, deviceName: String): String {
        val cleanName = deviceName.replace(" ", "_")
        return "xemulify://pair?ip=$ip&port=$port&pin=$pin&name=$cleanName"
    }

    data class QrInfo(val ip: String, val port: Int, val pin: String, val name: String)

    fun parseQrPayload(payload: String): QrInfo? {
        return try {
            if (!payload.startsWith("xemulify://pair?")) return null
            val queryString = payload.substringAfter("xemulify://pair?")
            val params = queryString.split("&").associate {
                val parts = it.split("=")
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            val ip = params["ip"] ?: return null
            val port = params["port"]?.toIntOrNull() ?: DEFAULT_PORT
            val pin = params["pin"] ?: return null
            val name = (params["name"] ?: "Xemulify-TV").replace("_", " ")
            QrInfo(ip, port, pin.uppercase(), name)
        } catch (_: Exception) {
            null
        }
    }
}

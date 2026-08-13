package com.clipshare.app.util

import org.json.JSONObject

/** Wire protocol matching the Go daemon (JSON over WebSocket). */
object Protocol {
    const val MSG_HELLO = "hello"
    const val MSG_CLIPBOARD = "clipboard"
    const val MSG_PING = "ping"
    const val MSG_PONG = "pong"
    const val MSG_ERROR = "error"

    fun hello(name: String, platform: String, version: String): String = JSONObject()
        .put("type", MSG_HELLO)
        .put("data", JSONObject()
            .put("name", name)
            .put("platform", platform)
            .put("version", version))
        .toString()

    fun clipboard(text: String, from: String): String = JSONObject()
        .put("type", MSG_CLIPBOARD)
        .put("data", JSONObject()
            .put("text", text)
            .put("ts", System.currentTimeMillis())
            .put("from", from))
        .toString()

    fun ping(): String = JSONObject().put("type", MSG_PING).toString()
    fun pong(): String = JSONObject().put("type", MSG_PONG).toString()

    data class Clipboard(val text: String, val from: String)
    data class Error(val code: String, val msg: String)
}

fun parseClipboard(json: String): Protocol.Clipboard? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != Protocol.MSG_CLIPBOARD) return null
        val data = obj.optJSONObject("data") ?: return null
        Protocol.Clipboard(data.optString("text"), data.optString("from"))
    } catch (_: Exception) {
        null
    }
}

fun parseError(json: String): Protocol.Error? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != Protocol.MSG_ERROR) return null
        val data = obj.optJSONObject("data") ?: return null
        Protocol.Error(data.optString("code"), data.optString("msg"))
    } catch (_: Exception) {
        null
    }
}

fun parseType(json: String): String? {
    return try {
        JSONObject(json).optString("type")
    } catch (_: Exception) {
        null
    }
}

/** Extracts the name from a hello message (used for the daemon's reply). */
fun parseHello(json: String): String? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != Protocol.MSG_HELLO) return null
        val data = obj.optJSONObject("data") ?: return null
        data.optString("name").ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

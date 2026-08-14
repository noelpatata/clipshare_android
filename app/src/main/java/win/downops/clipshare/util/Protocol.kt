package win.downops.clipshare.util

import android.util.Base64
import org.json.JSONObject

/** Wire protocol matching the Go daemon (JSON over WebSocket). */
object Protocol {
    fun hello(name: String, platform: String, version: String): String = JSONObject()
        .put("type", Constants.Protocol.Msg.HELLO)
        .put("data", JSONObject()
            .put("name", name)
            .put("platform", platform)
            .put("version", version))
        .toString()

    fun clipboard(text: String, from: String): String = JSONObject()
        .put("type", Constants.Protocol.Msg.CLIPBOARD)
        .put("data", JSONObject()
            .put("type", Constants.Protocol.Content.TEXT)
            .put("text", text)
            .put("ts", System.currentTimeMillis())
            .put("from", from))
        .toString()

    fun clipboardImage(bytes: ByteArray, mime: String, from: String): String = JSONObject()
        .put("type", Constants.Protocol.Msg.CLIPBOARD)
        .put("data", JSONObject()
            .put("type", Constants.Protocol.Content.IMAGE)
            .put("data", Base64.encodeToString(bytes, Base64.DEFAULT))
            .put("mime", mime)
            .put("ts", System.currentTimeMillis())
            .put("from", from))
        .toString()

    fun ping(): String = JSONObject().put("type", Constants.Protocol.Msg.PING).toString()
    fun pong(): String = JSONObject().put("type", Constants.Protocol.Msg.PONG).toString()

    data class Clipboard(
        val text: String?,
        val image: ByteArray?,
        val mime: String?,
        val from: String,
    ) {
        val isEmpty: Boolean
            get() = text.isNullOrBlank() && (image == null || image.isEmpty())
    }

    data class Error(val code: String, val msg: String)
}

fun parseClipboard(json: String): Protocol.Clipboard? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != Constants.Protocol.Msg.CLIPBOARD) return null
        val data = obj.optJSONObject("data") ?: return null
        val from = data.optString("from")
        if (data.optString("type") == Constants.Protocol.Content.IMAGE) {
            val b64 = data.optString("data")
            if (b64.isBlank()) return null
            val bytes = try {
                Base64.decode(b64, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            } ?: return null
            if (bytes.isEmpty()) return null
            Protocol.Clipboard(
                text = null,
                image = bytes,
                mime = data.optString("mime").ifBlank { Constants.Mime.IMAGE_PNG },
                from = from,
            )
        } else {
            val text = data.optString("text")
            if (text.isBlank()) return null
            Protocol.Clipboard(text = text, image = null, mime = null, from = from)
        }
    } catch (_: Exception) {
        null
    }
}

fun parseError(json: String): Protocol.Error? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != Constants.Protocol.Msg.ERROR) return null
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
        if (obj.optString("type") != Constants.Protocol.Msg.HELLO) return null
        val data = obj.optJSONObject("data") ?: return null
        data.optString("name").ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

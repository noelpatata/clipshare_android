package win.downops.clipshare.ws

import android.util.Base64
import org.json.JSONObject
import win.downops.clipshare.util.Constants

/**
 * Wire protocol matching the Go daemon (JSON over WebSocket).
 *
 * Only message *construction* lives here. Decoding the other direction is
 * handled by [ProtocolParser].
 */
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

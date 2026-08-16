package win.downops.clipshare.ws

import android.util.Base64
import org.json.JSONObject
import win.downops.clipshare.util.Constants

/**
 * A decoded inbound message produced by [ProtocolParser.parse]. Both the
 * WebSocket client and server switch on this instead of re-implementing the
 * JSON parsing and type routing themselves.
 */
sealed interface ProtocolMessage {
    data class Hello(val name: String) : ProtocolMessage
    data class Clipboard(val clip: Protocol.Clipboard) : ProtocolMessage
    data class Error(val error: Protocol.Error) : ProtocolMessage
    data object Ping : ProtocolMessage
    data object Pong : ProtocolMessage

    /** Anything that is not a well-formed message of a known type. */
    data object Unknown : ProtocolMessage
}

/**
 * Decodes raw WebSocket frames into typed [ProtocolMessage]s. All parsing is
 * lenient: malformed or unknown payloads become [ProtocolMessage.Unknown]
 * rather than throwing, so a bad frame can never crash a connection.
 */
object ProtocolParser {

    /** Routes a raw frame to a typed [ProtocolMessage]. */
    fun parse(raw: String): ProtocolMessage = when (parseType(raw)) {
        Constants.Protocol.Msg.HELLO -> parseHello(raw)?.let { ProtocolMessage.Hello(it) }
            ?: ProtocolMessage.Unknown
        Constants.Protocol.Msg.CLIPBOARD -> parseClipboard(raw)?.let { ProtocolMessage.Clipboard(it) }
            ?: ProtocolMessage.Unknown
        Constants.Protocol.Msg.PING -> ProtocolMessage.Ping
        Constants.Protocol.Msg.PONG -> ProtocolMessage.Pong
        Constants.Protocol.Msg.ERROR -> parseError(raw)?.let { ProtocolMessage.Error(it) }
            ?: ProtocolMessage.Unknown
        else -> ProtocolMessage.Unknown
    }

    fun parseType(raw: String): String? {
        return try {
            JSONObject(raw).optString("type")
        } catch (_: Exception) {
            null
        }
    }

    fun parseClipboard(raw: String): Protocol.Clipboard? {
        return try {
            val obj = JSONObject(raw)
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

    fun parseError(raw: String): Protocol.Error? {
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("type") != Constants.Protocol.Msg.ERROR) return null
            val data = obj.optJSONObject("data") ?: return null
            Protocol.Error(data.optString("code"), data.optString("msg"))
        } catch (_: Exception) {
            null
        }
    }

    /** Extracts the name from a hello message (used for the daemon's reply). */
    fun parseHello(raw: String): String? {
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("type") != Constants.Protocol.Msg.HELLO) return null
            val data = obj.optJSONObject("data") ?: return null
            data.optString("name").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
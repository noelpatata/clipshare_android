package win.downops.clipshare.clipboard

import android.content.ClipData
import android.content.Context
import android.net.Uri
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.sync.SyncService
import win.downops.clipshare.util.Constants

/**
 * Pushes clipboard content to the connected daemon, shared by [ClipboardPusher]
 * (foreground capture) and the background accessibility service.
 *
 * Centralizes the connected check, the byte-hash deduplication including loop
 * protection ([ClipboardDedup]), image compression, and the actual send — so
 * both capture paths behave identically.
 *
 * Use [payloadOf] to turn a [ClipData] into a [ClipPayload] without callers
 * having to detect image vs. text themselves.
 */
object ClipboardSender {

    /** Claims and sends text. Returns true when it was actually sent. */
    fun pushText(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val service = connected() ?: return false
        if (!ClipboardDedup.claim(text.toByteArray(Charsets.UTF_8))) {
            Log.d("Clipboard", "skipping already-handled text")
            return false
        }
        val ok = service.send(text)
        if (ok) {
            Log.i("Clipboard", "sent ${text.length} chars")
        } else {
            Log.w("Clipboard", "failed to send text")
        }
        return ok
    }

    /** Reads and compresses a clipboard image URI, then claims and sends it. */
    fun sendImage(context: Context, uri: Uri, mime: String): Boolean {
        if (ClipboardDedup.isLastUri(uri)) {
            val cached = ClipboardDedup.cachedUriBytes()
            if (cached != null) return pushImage(cached, mime)
            if (ClipboardDedup.uriFailed()) return false
        }

        val bytes = ImageCompressor.compress(context, uri, Prefs.maxImagePayloadKb(context))
        if (bytes == null || bytes.isEmpty()) {
            ClipboardDedup.setUriResult(uri, null, true)
            Log.w("Clipboard", "could not read/compress image from $uri")
            return false
        }
        ClipboardDedup.setUriResult(uri, bytes, false)

        return pushImage(bytes, mime)
    }

    /** Claims and sends already-compressed image bytes. */
    fun pushImage(bytes: ByteArray, mime: String): Boolean {
        if (bytes.isEmpty()) return false
        val service = connected() ?: return false
        if (!ClipboardDedup.claim(bytes)) {
            Log.d("Clipboard", "skipping already-handled image")
            return false
        }

        val actualMime = mime.takeIf { it.startsWith("image/") } ?: Constants.Mime.IMAGE_JPEG
        val ok = service.sendImage(bytes, actualMime)
        if (ok) {
            Log.i("Clipboard", "sent ${bytes.size} byte $actualMime image")
        } else {
            Log.w("Clipboard", "failed to send image")
        }
        return ok
    }

    private fun connected(): SyncService? {
        if (!AppState.connected.value) {
            Log.d("Clipboard", "not connected, skipping")
            return null
        }
        return AppState.service
    }

    /**
     * Detects whether the clip holds an image or text and returns a [ClipPayload]
     * that knows how to send it, or null when there is nothing sendable. Callers
     * no longer need to branch on `ClipData.Item.uri` themselves.
     */
    fun payloadOf(context: Context, clip: ClipData?): ClipPayload? {
        if (clip == null || clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        return if (item.uri != null) {
            val mime = clip.description.getMimeType(0) ?: Constants.Mime.GENERIC
            ImagePayload(item.uri, mime)
        } else {
            val text = runCatching { item.coerceToText(context)?.toString() }.getOrNull()
            if (text.isNullOrBlank()) null else TextPayload(text)
        }
    }
}

/** A clipboard payload (image or text) that knows how to send itself. */
sealed interface ClipPayload {
    /** Stable identity used to deduplicate unchanged clipboard content. */
    val fingerprint: String
    fun send(context: Context): Boolean
}

private class TextPayload(private val text: String) : ClipPayload {
    override val fingerprint get() = "text:$text"
    override fun send(context: Context) = ClipboardSender.pushText(context, text)
}

private class ImagePayload(private val uri: Uri, private val mime: String) : ClipPayload {
    override val fingerprint get() = "uri:$uri"
    override fun send(context: Context) = ClipboardSender.sendImage(context, uri, mime)
}
package win.downops.clipshare.clipboard

import android.content.Context
import android.net.Uri
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.sync.SyncService
import win.downops.clipshare.util.Constants

/**
 * Pushes clipboard content to the connected daemon, shared by [ClipboardSync]
 * (foreground capture) and the background accessibility service.
 *
 * Centralizes the connected check, the byte-hash deduplication including loop
 * protection ([ClipboardDedup]), image compression, and the actual send — so
 * both capture paths behave identically.
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
}
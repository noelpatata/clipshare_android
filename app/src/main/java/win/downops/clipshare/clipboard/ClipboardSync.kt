package win.downops.clipshare.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the clipboard while the app is in the foreground and pushes changes
 * to the connected daemon. Android 10+ blocks background clipboard reads, so
 * this is started/stopped with the activity's onResume/onPause.
 */
object ClipboardSync {

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var scope: CoroutineScope? = null
    private var lastTextHash = 0L
    private var lastImageHash = 0L
    private var lastUri: Uri? = null
    private var lastUriBytes: ByteArray? = null
    private var lastUriFailed = false

    fun start(context: Context) {
        if (scope != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        lastTextHash = hash(currentText(cm, context) ?: "")
        lastImageHash = 0L
        lastUri = null
        lastUriBytes = null
        lastUriFailed = false
        Log.i("ClipboardSync", "started")

        listener = ClipboardManager.OnPrimaryClipChangedListener {
            s.launch { handleChange(context, cm) }
        }
        cm.addPrimaryClipChangedListener(listener)

        s.launch {
            while (isActive) {
                handleChange(context, cm)
                delay(Constants.Clipboard.SYNC_POLL_MS)
            }
        }
    }

    fun stop(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        listener?.let { cm.removePrimaryClipChangedListener(it) }
        listener = null
        scope?.cancel()
        scope = null
        Log.i("ClipboardSync", "stopped")
    }

    private suspend fun handleChange(context: Context, cm: ClipboardManager) {
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return
        val description = cm.primaryClip?.description

        if (clip.uri != null) {
            sendImage(context, clip.uri, description?.getMimeType(0) ?: Constants.Mime.GENERIC)
            return
        }

        val text = clip.coerceToText(context)?.toString() ?: return
        if (text.isBlank()) return
        val h = hash(text)
        if (h == lastTextHash) return
        lastTextHash = h
        // skip content the service itself wrote from a remote peer
        if (text == AppState.lastRemoteWritten) {
            Log.d("ClipboardSync", "skipping loopback text")
            return
        }
        if (!AppState.connected.value) {
            Log.d("ClipboardSync", "not connected, skipping text")
            return
        }
        val ok = AppState.service?.send(text) == true
        if (ok) {
            Log.i("ClipboardSync", "sent ${text.length} chars")
        } else {
            Log.w("ClipboardSync", "failed to send text")
        }
    }

    private fun sendImage(context: Context, uri: Uri, mime: String) {
        if (uri == lastUri) {
            if (lastUriFailed) {
                // already logged once for this URI; don't spam
                return
            }
            val cached = lastUriBytes
            if (cached != null) {
                dispatchImage(cached, mime)
                return
            }
        }

        val bytes = ImageCompressor.compress(context, uri, Prefs.maxImagePayloadKb(context))
        lastUri = uri
        if (bytes == null || bytes.isEmpty()) {
            lastUriBytes = null
            lastUriFailed = true
            Log.w("ClipboardSync", "could not read/compress image from $uri")
            return
        }
        lastUriBytes = bytes
        lastUriFailed = false

        dispatchImage(bytes, mime)
    }

    private fun dispatchImage(bytes: ByteArray, mime: String) {
        val h = hash(bytes)
        if (h == lastImageHash) {
            return
        }
        lastImageHash = h

        // skip content the service itself wrote from a remote peer
        val lastRemote = AppState.lastRemoteWrittenImage
        if (lastRemote != null && lastRemote.contentEquals(bytes)) {
            Log.d("ClipboardSync", "skipping loopback image")
            return
        }

        if (!AppState.connected.value) {
            Log.d("ClipboardSync", "not connected, skipping image")
            return
        }

        val actualMime = mime.takeIf { it.startsWith("image/") } ?: Constants.Mime.IMAGE_JPEG
        val ok = AppState.service?.sendImage(bytes, actualMime) == true
        if (ok) {
            Log.i("ClipboardSync", "sent ${bytes.size} byte $actualMime image")
        } else {
            Log.w("ClipboardSync", "failed to send image")
        }
    }

    private fun currentText(cm: ClipboardManager, context: Context): String? {
        return cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    private fun hash(s: String): Long {
        var h = 1125899906842597L
        for (c in s) {
            h = 31 * h + c.code.toLong()
        }
        return h
    }

    private fun hash(bytes: ByteArray): Long {
        var h = 1125899906842597L
        for (b in bytes) {
            h = 31 * h + b.toLong()
        }
        return h
    }
}

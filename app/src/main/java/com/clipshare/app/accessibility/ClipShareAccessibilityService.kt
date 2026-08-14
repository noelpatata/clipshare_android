package com.clipshare.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper

import android.view.accessibility.AccessibilityEvent
import com.clipshare.app.clipboard.ImageCompressor
import com.clipshare.app.logs.Log
import com.clipshare.app.settings.Prefs
import com.clipshare.app.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background clipboard capture. Android 10+ forbids clipboard reads by
 * background apps, but an enabled accessibility service is exempt, so this
 * service can detect copy actions from any app and push them to the desktop.
 *
 * The service does nothing unless the sync service is connected (it only
 * pushes when [AppState.connected] is true). Enable it from Settings ->
 * Accessibility -> ClipShare.
 */
class ClipShareAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastTextHash = 0L
    private var lastImageHash = 0L
    private var lastUri: Uri? = null
    private var lastUriBytes: ByteArray? = null
    private var lastUriFailed = false
    private val readRunnable = Runnable { pushClipboard() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.init(this)
        lastTextHash = 0L
        lastImageHash = 0L
        lastUri = null
        lastUriBytes = null
        lastUriFailed = false
        Log.i("Accessibility", "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Debounce: give the clipboard a moment to settle after the copy.
                handler.removeCallbacks(readRunnable)
                handler.postDelayed(readRunnable, 400)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun pushClipboard() {
        if (!AppState.connected.value) {
            Log.d("Accessibility", "not connected, skipping clipboard")
            return
        }
        val service = AppState.service ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        }.getOrNull() ?: return

        if (clip.uri != null) {
            val mime = cm.primaryClip?.description?.getMimeType(0) ?: "application/octet-stream"
            scope.launch { sendImage(service, clip.uri, mime) }
            return
        }

        val text = runCatching { clip.coerceToText(this)?.toString() }.getOrNull() ?: return
        if (text.isBlank()) return
        if (text == AppState.lastRemoteWritten) return
        val h = text.hashCode().toLong()
        if (h == lastTextHash) return
        lastTextHash = h
        val ok = service.send(text)
        if (ok) {
            Log.i("Accessibility", "pushed ${text.length} chars")
        } else {
            Log.w("Accessibility", "push failed")
        }
    }

    private fun sendImage(service: com.clipshare.app.sync.SyncService, uri: Uri, mime: String) {
        if (uri == lastUri) {
            if (lastUriFailed) {
                return
            }
            val cached = lastUriBytes
            if (cached != null) {
                dispatchImage(service, cached, mime)
                return
            }
        }

        val bytes = ImageCompressor.compress(this, uri, Prefs.maxImagePayloadKb(this))
        lastUri = uri
        if (bytes == null || bytes.isEmpty()) {
            lastUriBytes = null
            lastUriFailed = true
            Log.w("Accessibility", "could not read/compress image from $uri")
            return
        }
        lastUriBytes = bytes
        lastUriFailed = false

        dispatchImage(service, bytes, mime)
    }

    private fun dispatchImage(service: com.clipshare.app.sync.SyncService, bytes: ByteArray, mime: String) {
        val h = hash(bytes)
        if (h == lastImageHash) {
            return
        }
        lastImageHash = h

        val lastRemote = AppState.lastRemoteWrittenImage
        if (lastRemote != null && lastRemote.contentEquals(bytes)) {
            Log.d("Accessibility", "skipping loopback image")
            return
        }

        val actualMime = mime.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val ok = service.sendImage(bytes, actualMime)
        if (ok) {
            Log.i("Accessibility", "pushed ${bytes.size} byte $actualMime image")
        } else {
            Log.w("Accessibility", "push image failed")
        }
    }

    private fun hash(bytes: ByteArray): Long {
        var h = 1125899906842597L
        for (b in bytes) {
            h = 31 * h + b.toLong()
        }
        return h
    }
}

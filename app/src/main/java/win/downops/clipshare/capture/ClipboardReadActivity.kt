package win.downops.clipshare.capture

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import win.downops.clipshare.clipboard.ClipboardSender
import win.downops.clipshare.logs.Log
import win.downops.clipshare.util.Constants
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transparent activity used to capture background copies that have no text
 * selection — images, or text copied via a copy button. Android only grants
 * clipboard access to the currently focused app, so this activity briefly takes
 * focus, reads the clipboard and sends its content, then finishes immediately.
 */
class ClipboardReadActivity : Activity() {

    private val handled = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private val timeout = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Safety net: never linger on screen if focus is not granted quickly.
        handler.postDelayed(timeout, 1500)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({ readAndFinish() }, 150)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) readAndFinish()
    }

    private fun readAndFinish() {
        if (!handled.compareAndSet(false, true)) return
        handler.removeCallbacks(timeout)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        }.getOrNull()

        when {
            clip?.uri != null -> {
                val mime = cm.primaryClip?.description?.getMimeType(0) ?: Constants.Mime.GENERIC
                Log.i("Capture", "captured ${clip.uri} ($mime)")
                ClipboardSender.sendImage(this, clip.uri, mime)
            }
            clip != null -> {
                val text = runCatching { clip.coerceToText(this)?.toString() }.getOrNull()
                if (!text.isNullOrBlank()) {
                    Log.i("Capture", "captured text (${text.length} chars)")
                    ClipboardSender.pushText(this, text)
                } else {
                    Log.d("Capture", "clipboard content could not be read")
                }
            }
            else -> Log.d("Capture", "clipboard empty, nothing to capture")
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        super.onDestroy()
    }
}
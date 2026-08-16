package win.downops.clipshare.clipboard

import android.content.ClipboardManager
import android.content.Context
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
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
 *
 * Capturing logic (dedup, loop protection, compression, sending) lives in
 * [ClipboardSender] / [ClipboardDedup] and is shared with the background
 * accessibility service, so the same copy is never sent twice even when the app
 * moves between foreground and background.
 */
object ClipboardSync {

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var scope: CoroutineScope? = null

    /** Fingerprint of the last clip handled, so the poll loop stays quiet when
     * the clipboard has not changed (avoids flooding the logs every 700 ms). */
    @Volatile
    private var lastSeenKey: String? = null

    fun start(context: Context) {
        if (scope != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        // Seed with the current clipboard so it is not re-pushed when the app
        // comes to the foreground; only NEW copies are sent.
        currentText(cm, context)?.let {
            ClipboardDedup.claim(it.toByteArray(Charsets.UTF_8))
            lastSeenKey = "text:$it"
        }
        Log.i("ClipboardSync", "started")

        listener = ClipboardManager.OnPrimaryClipChangedListener {
            s.launch { handleChange(context, cm) }
        }
        cm.addPrimaryClipChangedListener(listener)

        s.launch {
            while (isActive) {
                handleChange(context, cm)
                delay(Prefs.clipboardPollMs(context))
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

    @Synchronized
    private fun handleChange(context: Context, cm: ClipboardManager) {
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return

        if (clip.uri != null) {
            // Poll loop runs every 700 ms; keep it quiet while the clip is unchanged.
            val key = "uri:${clip.uri}"
            if (key == lastSeenKey) return
            lastSeenKey = key
            val mime = cm.primaryClip?.description?.getMimeType(0) ?: Constants.Mime.GENERIC
            ClipboardSender.sendImage(context, clip.uri, mime)
            return
        }

        val text = clip.coerceToText(context)?.toString() ?: return
        if (text.isBlank()) return
        val key = "text:$text"
        if (key == lastSeenKey) return
        lastSeenKey = key

        ClipboardSender.pushText(context, text)
    }

    private fun currentText(cm: ClipboardManager, context: Context): String? {
        return cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
    }
}
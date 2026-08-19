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
 * Foreground *send* path: watches the local clipboard while the app is in the
 * foreground and pushes changes to the connected daemon. Android 10+ blocks
 * background clipboard reads, so this is started/stopped with the activity's
 * onResume/onPause. (The background equivalent is the accessibility service.)
 *
 * Capturing logic (dedup, loop protection, compression, sending) lives in
 * [ClipboardSender] / [ClipboardDedup] and is shared with the background
 * accessibility service, so the same copy is never sent twice even when the app
 * moves between foreground and background. Writing received content back into
 * the clipboard is the job of [ClipboardWriter].
 */
object ClipboardPusher {

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var scope: CoroutineScope? = null

    /** Fingerprint of the last clip handled, so the poll loop stays quiet when
     * the clipboard has not changed (avoids flooding the logs every 700 ms). */
    @Volatile
    private var lastSeenKey: String? = null

    fun start(context: Context) {
        if (scope != null) return
        val cm = ClipboardWriter.manager(context)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        // Seed with the current clipboard so it is not re-pushed when the app
        // comes to the foreground; only NEW copies are sent.
        currentText(cm, context)?.let {
            ClipboardDedup.claim(it.toByteArray(Charsets.UTF_8))
            lastSeenKey = "text:$it"
        }
        Log.i("ClipboardPusher", "started")

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
        val cm = ClipboardWriter.manager(context)
        listener?.let { cm.removePrimaryClipChangedListener(it) }
        listener = null
        scope?.cancel()
        scope = null
        Log.i("ClipboardPusher", "stopped")
    }

    @Synchronized
    private fun handleChange(context: Context, cm: ClipboardManager) {
        val payload = ClipboardSender.payloadOf(context, cm.primaryClip) ?: return
        // Poll loop runs frequently; keep it quiet while the clip is unchanged.
        if (payload.fingerprint == lastSeenKey) return
        lastSeenKey = payload.fingerprint
        payload.send(context)
    }

    private fun currentText(cm: ClipboardManager, context: Context): String? {
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 } ?: return null
        if (clip.description.label?.toString() == Constants.Clipboard.INTERNAL_CLIP_LABEL) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}
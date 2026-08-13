package com.clipshare.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.clipshare.app.state.AppState
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
    private var lastHash = 0L

    fun start(context: Context) {
        if (scope != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        lastHash = hash(currentText(cm, context) ?: "")

        listener = ClipboardManager.OnPrimaryClipChangedListener {
            s.launch { handleChange(context, cm) }
        }
        cm.addPrimaryClipChangedListener(listener)

        s.launch {
            while (isActive) {
                handleChange(context, cm)
                delay(700)
            }
        }
    }

    fun stop(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        listener?.let { cm.removePrimaryClipChangedListener(it) }
        listener = null
        scope?.cancel()
        scope = null
    }

    private suspend fun handleChange(context: Context, cm: ClipboardManager) {
        val text = currentText(cm, context) ?: return
        if (text.isBlank()) return
        val h = hash(text)
        if (h == lastHash) return
        lastHash = h
        // skip content the service itself wrote from a remote peer
        if (text == AppState.lastRemoteWritten) return
        if (!AppState.connected.value) return
        AppState.service?.send(text)
    }

    private fun currentText(cm: ClipboardManager, context: Context): String? {
        return cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    private fun hash(s: String): Long {
        var h = 1125899906842597L
        for (c in s) {
            h = 31 * h + c.toLong()
        }
        return h
    }
}

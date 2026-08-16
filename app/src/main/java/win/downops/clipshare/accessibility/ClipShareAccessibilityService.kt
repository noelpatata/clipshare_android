package win.downops.clipshare.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

import android.view.accessibility.AccessibilityEvent
import win.downops.clipshare.clipboard.ClipboardDedup
import win.downops.clipshare.clipboard.ClipboardSender
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
 * Background clipboard capture.
 *
 * Android 10+ forbids clipboard reads by background apps, and since Android 11
 * the accessibility service is no longer exempt (ClipboardService only allows
 * the focused app, the default IME, or apps with privileged permissions). So on
 * modern Android this service cannot read the clipboard in the background.
 *
 * Instead it watches for the *copy* action itself:
 *
 *  - **Android 13+**: a copy is detected by matching the system's localized
 *    "copied to clipboard" pill labels ("Text kopiert", "Copy saved", ...) or an
 *    app's copy-button labels ("Kopieren", "Copy", ...) in a range of languages.
 *    The content comes from the text selection tracked via accessibility
 *    selection events — the clipboard itself cannot be read in the background
 *    and the pill does not expose the copied text on many devices. Consequently
 *    only *select-then-copy* works in the background; copies without a prior
 *    selection (copy link/URL, images) cannot be captured.
 *  - **Android 9 and below**: background clipboard reads are unrestricted, so a
 *    periodic poll of the clipboard is used instead, which also covers images.
 *
 * Merely *selecting* text never sends anything — a copy must be detected. The
 * service only pushes when the sync service is connected and the app is not in
 * the foreground (ClipboardSync already covers the foreground case). Enable it
 * from Settings -> Accessibility -> ClipShare.
 */
@SuppressLint("AccessibilityPolicy")
class ClipShareAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val readRunnable = Runnable { scope.launch { pushClipboard() } }

    /** Text captured from a11y selection events, sent only when a copy is detected. */
    @Volatile
    private var pendingSelection: String? = null

    @Volatile
    private var lastSelectionAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.init(this)
        Log.i("Accessibility", "service connected")
        AppState.onAccessibilityConnected()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Legacy path: background clipboard reads are allowed on Android 9 and below.
            seedClipboard()
            scope.launch {
                while (isActive) {
                    pushClipboard()
                    delay(Prefs.clipboardPollMs(this@ClipShareAccessibilityService))
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val text = e.text.lastOrNull()?.toString()
                val from = e.fromIndex
                val to = e.toIndex
                if (text != null && from >= 0 && to >= 0 && to > from && to <= text.length) {
                    pendingSelection = text.substring(from, to)
                    lastSelectionAt = System.currentTimeMillis()
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val node = e.source
                    val label = node?.let { it.text?.toString() ?: it.contentDescription?.toString() }
                    if (isCopyButton(label)) pushCopied()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                if (e.packageName == "com.android.systemui" &&
                    isCopyConfirmation(e.source?.contentDescription?.toString())
                ) {
                    pushCopied()
                }
            }
            else -> {
                // Legacy path: debounce a clipboard poll after activity events.
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    handler.removeCallbacks(readRunnable)
                    handler.postDelayed(readRunnable, 400)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        AppState.onAccessibilityDisconnected()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun seedClipboard() {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                ?.coerceToText(this)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { ClipboardDedup.claim(it.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Returns the selected text if it is fresh enough to pair with a copy. The
     * copy pill itself does not expose the copied text through accessibility on
     * many devices (Android 15/16 hide it), so the selection is the only reliable
     * source of content.
     */
    private fun takePendingSelection(): String? {
        val text = pendingSelection
        val age = System.currentTimeMillis() - lastSelectionAt
        pendingSelection = null
        lastSelectionAt = 0L
        if (text == null || age > SELECTION_STALE_MS) return null
        return text
    }

    /**
     * True when a systemui accessibility event is the "copied to clipboard"
     * confirmation pill. The pill's content descriptions are localized labels
     * ("Text kopiert", "Copy saved", ...), which vary by device locale; they are
     * only used as a copy *signal* — the actual content always comes from the
     * selection.
     */
    private fun isCopyConfirmation(desc: String?): Boolean {
        return desc == "Text kopiert" || desc == "Copy saved" || desc == "Text copied" ||
            desc == "Copied" || desc == "Kopiert" || desc == "Zwischenablage" || desc == "Clipboard"
    }

    /**
     * True when a clicked view is a "copy" button, matched against its
     * label/content description in a range of languages. The label is only a copy
     * *signal* — the content still comes from the tracked selection.
     */
    private fun isCopyButton(label: String?): Boolean {
        if (label == null) return false
        return COPY_BUTTON_LABELS.any { it.equals(label, ignoreCase = true) }
    }

    @Synchronized
    private fun pushCopied() {
        if (AppState.appInForeground) return
        val text = takePendingSelection() ?: return
        ClipboardSender.pushText(this, text)
    }

    @Synchronized
    private fun pushClipboard() {
        if (AppState.appInForeground) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        }.getOrNull() ?: return

        if (clip.uri != null) {
            val mime = cm.primaryClip?.description?.getMimeType(0) ?: Constants.Mime.GENERIC
            scope.launch { ClipboardSender.sendImage(this@ClipShareAccessibilityService, clip.uri, mime) }
            return
        }

        val text = runCatching { clip.coerceToText(this)?.toString() }.getOrNull() ?: return
        ClipboardSender.pushText(this, text)
    }

    private companion object {
        const val SELECTION_STALE_MS = 60_000L

        val COPY_BUTTON_LABELS = setOf(
            "Copy", "Copy text", "Copy selected", "Copy selection",
            "Kopieren", "Text kopieren", "Kopieren ausgewählt", "Auswahl kopieren",
            "Copiar", "Copiar texto", "Copia", "Copier", "Copier le texte",
            "Copia", "Copiare", "Copia testo", "Kopírovat", "Zkopírovat",
            "Kopiuj", "Kopieren", "Kopioi", "Kopiera", "Kopiere", "Copiar",
        )
    }
}
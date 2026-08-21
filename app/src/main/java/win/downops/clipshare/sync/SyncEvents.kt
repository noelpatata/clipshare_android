package win.downops.clipshare.sync

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import win.downops.clipshare.MainActivity
import win.downops.clipshare.R
import win.downops.clipshare.clipboard.ClipboardDedup
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.image.ImageHistoryStore
import win.downops.clipshare.logs.Log
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol

/**
 * Shared side-effects a [SyncMode] can trigger on the owning [SyncService]:
 * writing an inbound clipboard item and updating the foreground notification.
 */
class SyncEvents(private val context: Context) {

    fun receive(clip: Protocol.Clipboard) {
        val image = clip.image
        if (image != null) {
            Log.i("SyncService", "received ${image.size} byte ${clip.mime ?: "image"}")
            val mime = clip.mime ?: Constants.Mime.IMAGE_PNG
            ClipboardDedup.markRemoteWritten(image)
            val entry = AppState.onReceivedImage(context, image, mime, clip.from)
            setClipboardFromHistory(entry, mime)
        } else {
            val text = clip.text
            if (text != null) {
                Log.i("SyncService", "received ${text.length} chars")
                ClipboardDedup.markRemoteWritten(text.toByteArray(Charsets.UTF_8))
                writeClipboard(text)
                AppState.onReceived(context, text, clip.from)
            }
        }
    }

    fun updateNotification(text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(Constants.Notification.ID, buildNotification(text))
    }

    /** Builds the foreground notification; also used by [SyncService] at startup. */
    fun buildNotification(text: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, Constants.Notification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clipboard)
            .setContentTitle("ClipShare")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun writeClipboard(text: String) {
        // Label the clip as app-internal so capture paths skip it and we avoid
        // echoing received content back to peers (and avoid duplicate history).
        ClipboardWriter.writeText(context, Constants.Clipboard.INTERNAL_CLIP_LABEL, text)
    }

    /** Puts a received image on the clipboard from its persisted history file. */
    private fun setClipboardFromHistory(entry: HistoryEntry?, mime: String) {
        val clip = entry?.clip as? ClipItem.Image ?: return
        val file = ImageHistoryStore.imageFile(context, clip.imageId) ?: return
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        // Label the clip as app-internal so capture paths skip it and we avoid
        // echoing received content back to peers (and avoid duplicate history).
        ClipboardWriter.writeImage(context, Constants.Clipboard.INTERNAL_CLIP_LABEL, uri, mime)
        Log.i("SyncService", "placed received image on clipboard from history: ${file.name}")
    }
}

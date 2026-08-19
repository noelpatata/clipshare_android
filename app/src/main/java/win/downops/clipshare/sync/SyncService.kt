package win.downops.clipshare.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import win.downops.clipshare.MainActivity
import win.downops.clipshare.R
import win.downops.clipshare.clipboard.ClipboardDedup
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol
import java.io.File

/**
 * Foreground service that runs in either client or server mode.
 *
 * The mode-specific logic lives in [ClientSyncMode] and [ServerSyncMode],
 * chosen here from the configured app mode. This class only coordinates:
 * it starts the active mode, exposes send/switchTo to the UI and tile, and
 * owns the shared clipboard-receive + notification behavior ([SyncEvents]).
 */
class SyncService : Service(), SyncEvents {

    private var mode: SyncMode? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.init(this)
        createChannel()
        startForeground(Constants.Notification.ID, buildNotification("Starting..."))
        Log.i("SyncService", "onCreate")
        AppState.onServiceStarted(this)
        AppState.setAppMode(Prefs.appMode(this))

        val m = if (Prefs.appMode(this) == Prefs.APP_MODE_SERVER) {
            ServerSyncMode(this, this)
        } else {
            ClientSyncMode(this, this)
        }
        mode = m
        scope.launch {
            try {
                m.start()
            } catch (e: Exception) {
                Log.e("SyncService", "mode start failed", e)
                AppState.onError("Service failed to start: ${e.message}")
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("SyncService", "onStartCommand")
        mode?.onStartCommand()
        return START_STICKY
    }

    // ------------------------------------------------------------------
    // Delegation to the active mode
    // ------------------------------------------------------------------

    /** Push local clipboard text to peers. Called from the app UI, the
     * accessibility service or the quick-settings tile while connected. */
    fun send(text: String): Boolean = mode?.send(text) == true

    /** Push a local image to peers. */
    fun sendImage(bytes: ByteArray, mime: String): Boolean =
        mode?.sendImage(bytes, mime) == true

    /**
     * Manually switch to a specific daemon (e.g. user taps a device in the UI).
     * No-op in server mode.
     */
    fun switchTo(host: String, port: Int, tls: Boolean) {
        mode?.switchTo(host, port, tls)
    }

    // ------------------------------------------------------------------
    // SyncEvents: shared inbound handling
    // ------------------------------------------------------------------

    override fun receive(clip: Protocol.Clipboard) {
        val image = clip.image
        if (image != null) {
            Log.i("SyncService", "received ${image.size} byte ${clip.mime ?: "image"}")
            writeClipboardImage(image, clip.mime ?: Constants.Mime.IMAGE_PNG)
            AppState.onReceivedImage(this, image, clip.mime ?: Constants.Mime.IMAGE_PNG, clip.from)
        } else {
            val text = clip.text
            if (text != null) {
                Log.i("SyncService", "received ${text.length} chars")
                writeClipboard(text)
                AppState.onReceived(this, text, clip.from)
            }
        }
    }

    private fun writeClipboard(text: String) {
        // Arm loop protection before making the content visible to listeners/polls.
        ClipboardDedup.markRemoteWritten(text.toByteArray(Charsets.UTF_8))
        ClipboardWriter.writeText(this, "clipshare", text)
    }

    private fun writeClipboardImage(bytes: ByteArray, mime: String) {
        val dir = File(cacheDir, "clipshare_images").apply { mkdirs() }
        val ext = when (mime.lowercase()) {
            Constants.Mime.IMAGE_PNG -> "png"
            Constants.Mime.IMAGE_JPEG, Constants.Mime.IMAGE_JPG -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> "img"
        }
        val file = File(dir, "clip_${System.currentTimeMillis()}.$ext")
        try {
            file.writeBytes(bytes)
        } catch (e: Exception) {
            Log.e("SyncService", "failed to write clipboard image", e)
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        // Arm loop protection before making the content visible to listeners/polls.
        ClipboardDedup.markRemoteWritten(bytes)
        ClipboardWriter.writeImage(this, "clipshare image", uri)
        Log.i("SyncService", "wrote image to clipboard: ${file.name}")
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    override fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.Notification.ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                Constants.Notification.CHANNEL_ID, "ClipShare sync", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Clipboard sync and server"
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.Notification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clipboard)
            .setContentTitle("ClipShare")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        val m = mode
        mode = null
        scope.launch {
            try {
                m?.stop()
            } catch (e: Exception) {
                Log.e("SyncService", "mode stop failed", e)
            }
            scope.cancel()
        }
        AppState.onServiceStopped()
        Log.i("SyncService", "onDestroy")
        super.onDestroy()
    }
}
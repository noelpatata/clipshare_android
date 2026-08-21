package win.downops.clipshare.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import win.downops.clipshare.MainActivity
import win.downops.clipshare.R
import win.downops.clipshare.util.Constants

/**
 * Foreground notification of [SyncService]: creates the channel and posts
 * status updates (connection state, server mode client count) under a single
 * notification id.
 */
class SyncNotifications(private val context: Context) {

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                Constants.Notification.CHANNEL_ID, "ClipShare sync", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Clipboard sync and server"
            nm.createNotificationChannel(channel)
        }
    }

    /** Replaces the notification text, keeping it ongoing. */
    fun update(text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(Constants.Notification.ID, build(text))
    }

    /** Builds the ongoing foreground notification shown by [SyncService]. */
    fun build(text: String): Notification {
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
}

package com.clipshare.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipshare.app.MainActivity
import com.clipshare.app.R
import com.clipshare.app.discover.DiscoveryManager
import com.clipshare.app.settings.Prefs
import com.clipshare.app.state.AppState
import com.clipshare.app.ws.WsClient

/**
 * Foreground service owning the WebSocket connection to the desktop daemon.
 * Also owns network discovery, so found desktops are auto-connected without
 * any manual server address. Writes received clipboard content to the system
 * clipboard (allowed in the background). Reads from the clipboard are done by
 * the app UI while it is in the foreground (Android 10+ restriction).
 */
class SyncService : Service() {

    private val CHANNEL_ID = "clipshare_sync"
    private val NOTIF_ID = 1
    private val VERSION = "0.1.0"

    private var ws: WsClient? = null
    private var discovery: DiscoveryManager? = null
    private val lock = Object()

    /** The daemon we are currently (or last) targeting. */
    @Volatile
    private var currentHost: String? = null

    @Volatile
    private var currentPort: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Starting..."))
        AppState.onServiceStarted(this)
        startDiscovery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ws == null) connect()
        return START_STICKY
    }

    private fun startDiscovery() {
        if (!Prefs.discoveryEnabled(this)) return
        val d = DiscoveryManager(this) { name, host, port, _ ->
            onDeviceFound(name, host, port)
        }
        discovery = d
        d.start()
    }

    /** Called from the discovery threads whenever a daemon announces itself. */
    private fun onDeviceFound(name: String, host: String, port: Int) {
        if (!Prefs.autoConnect(this)) return
        if (currentHost == host && currentPort == port && ws != null) {
            return
        }
        connectTo(host, port)
    }

    /**
     * Manually switch to a specific daemon (e.g. user taps a device in the UI).
     */
    fun switchTo(host: String, port: Int) {
        Prefs.setServerHost(this, host)
        Prefs.setServerPort(this, port)
        connectTo(host, port)
    }

    private fun connectTo(host: String, port: Int) {
        synchronized(lock) {
            if (currentHost == host && currentPort == port && ws != null) {
                return
            }
            currentHost = host
            currentPort = port
            Prefs.setServerHost(this, host)
            Prefs.setServerPort(this, port)
            val old = ws
            ws = null
            old?.stop()
            connect()
        }
    }

    private fun connect() {
        val host = currentHost ?: Prefs.serverHost(this).takeIf { it.isNotBlank() } ?: run {
            AppState.onSearching()
            updateNotification("Searching for desktops...")
            return
        }
        val port = if (currentPort > 0) currentPort else Prefs.serverPort(this)
        val token = Prefs.token(this)
        var url = "ws://$host:$port/ws"
        if (token.isNotBlank()) {
            url += "?token=" + token
        }
        val hello = com.clipshare.app.util.Protocol.hello(
            Prefs.deviceName(this), "android", VERSION
        )
        AppState.onConnecting()
        updateNotification("Connecting to $host...")
        ws = WsClient(
            url = url,
            hello = hello,
            onConnected = { name, _ ->
                AppState.onConnected(name, host)
                updateNotification("Connected to $host")
            },
            onClipboard = { text, from ->
                writeClipboard(text)
                AppState.onReceived(this, text, from)
            },
            onDisconnected = { reason ->
                AppState.onDisconnected(reason)
                updateNotification("Disconnected")
            },
            onReconnecting = { attempt ->
                AppState.onReconnecting(attempt)
                updateNotification("Reconnecting ($host)...")
            },
            onError = { msg -> AppState.onError(msg) },
        ).also { it.start() }
    }

    /** Push local clipboard text to the daemon. Called while app is foreground. */
    fun send(text: String): Boolean {
        val ok = ws?.send(text, Prefs.deviceName(this)) == true
        if (ok) AppState.onSent(this, text)
        return ok
    }

    private fun writeClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("clipshare", text))
        AppState.lastRemoteWritten = text
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "ClipShare sync", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Clipboard sync with desktop"
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clipboard)
            .setContentTitle("ClipShare")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        ws?.stop()
        ws = null
        discovery?.stop()
        discovery = null
        AppState.onServiceStopped()
        super.onDestroy()
    }
}

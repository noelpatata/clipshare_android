package win.downops.clipshare.sync

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
import androidx.core.content.FileProvider
import win.downops.clipshare.MainActivity
import win.downops.clipshare.R
import win.downops.clipshare.certs.CertStore
import win.downops.clipshare.discover.DiscoveryManager
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.util.Protocol
import win.downops.clipshare.ws.WsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service owning the WebSocket connection to the desktop daemon.
 * Also owns network discovery, so found desktops are auto-connected without
 * any manual server address. Writes received clipboard content (text and
 * images) to the system clipboard (allowed in the background).
 *
 * Connection modes (mirror the desktop daemon):
 *  - discover: scan mDNS/UDP, auto-connect to announcing daemons.
 *  - whitelist: no scanning; cycle through the whitelisted IPs and verify the
 *    daemon's hello name against the entry.
 */
class SyncService : Service() {

    private var ws: WsClient? = null
    private var discovery: DiscoveryManager? = null
    private val lock = Object()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    /** The daemon we are currently (or last) targeting. */
    @Volatile
    private var currentHost: String? = null

    @Volatile
    private var currentPort: Int = 0

    @Volatile
    private var currentTls: Boolean = false

    @Volatile
    private var whitelistCandidates: List<Pair<String, Int>> = emptyList()

    @Volatile
    private var whitelistIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.init(this)
        running = true
        createChannel()
        startForeground(Constants.Notification.ID, buildNotification("Starting..."))
        Log.i("SyncService", "onCreate")
        AppState.onServiceStarted(this)
        startDiscovery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("SyncService", "onStartCommand")
        val shouldConnect = synchronized(lock) { ws == null }
        if (shouldConnect) connect()
        return START_STICKY
    }

    private fun startDiscovery() {
        if (Prefs.connectionMode(this) == Prefs.MODE_WHITELIST) {
            Log.i("SyncService", "discovery skipped: whitelist mode")
            return
        }
        if (!Prefs.discoveryEnabled(this)) {
            Log.i("SyncService", "discovery skipped: disabled")
            return
        }
        Log.i("SyncService", "starting discovery")
        val beaconPort = Prefs.discoveryBeaconPort(this)
        val d = DiscoveryManager(this, beaconPort) { name, host, port, tls, _ ->
            Log.i("SyncService", "discovered $name at $host:$port (tls=$tls)")
            onDeviceFound(name, host, port, tls)
        }
        discovery = d
        d.start()
    }

    /** Called from the discovery threads whenever a daemon announces itself. */
    private fun onDeviceFound(name: String, host: String, port: Int, tls: Boolean) {
        if (!running) return
        if (!Prefs.autoConnect(this)) return
        connectTo(host, port, tls, verifyName = false, persist = true)
    }

    /**
     * Manually switch to a specific daemon (e.g. user taps a device in the UI).
     */
    fun switchTo(host: String, port: Int, tls: Boolean) {
        connectTo(host, port, tls, verifyName = false, persist = true)
    }

    private fun connect() {
        if (Prefs.connectionMode(this) == Prefs.MODE_WHITELIST) {
            startWhitelistConnections()
            return
        }
        val host = currentHost ?: Prefs.serverHost(this).takeIf { it.isNotBlank() } ?: run {
            AppState.onSearching()
            updateNotification("Searching for desktops...")
            Log.i("SyncService", "no host configured, searching via discovery")
            return
        }
        val port = if (currentPort > 0) currentPort else Prefs.serverPort(this)
        Log.i("SyncService", "connecting to $host:$port (tls=${Prefs.tlsEnabled(this)})")
        connectTo(host, port, Prefs.tlsEnabled(this), verifyName = false, persist = false)
    }

    private fun connectTo(
        host: String,
        port: Int,
        tls: Boolean,
        verifyName: Boolean,
        persist: Boolean,
    ) {
        synchronized(lock) {
            if (currentHost == host && currentPort == port && currentTls == tls && ws != null) {
                Log.d("SyncService", "already connected/connecting to $host:$port")
                return
            }
            currentHost = host
            currentPort = port
            currentTls = tls
            ws?.stop()
            val socket = buildSocket(host, port, tls, verifyName)
            ws = socket
            if (socket != null) {
                if (persist) {
                    Prefs.setServerHost(this, host)
                    Prefs.setServerPort(this, port)
                }
                AppState.onConnecting()
                updateNotification("Connecting to $host...")
                socket.start()
            }
        }
    }

    private fun buildSocket(host: String, port: Int, tls: Boolean, verifyName: Boolean): WsClient? {
        val token = Prefs.token(this)
        val scheme = if (tls) Constants.Protocol.Scheme.WSS else Constants.Protocol.Scheme.WS
        var url = "$scheme://$host:$port${Constants.Protocol.WS_PATH}"
        if (token.isNotBlank()) {
            url += "?token=" + token
        }
        val hello = Protocol.hello(Prefs.deviceName(this), Constants.Protocol.PLATFORM_ANDROID, Constants.App.VERSION)
        Log.i("SyncService", "opening $url")

        val tlsConfig = if (tls) {
            if (CertStore.hasCert(this)) {
                CertStore.clientTls(this)
            } else {
                val msg = "No client certificate imported (Settings -> TLS)"
                Log.e("SyncService", msg)
                AppState.onError(msg)
                updateNotification("TLS on, but no certificate imported")
                onConnectFailed()
                return null
            }
        } else null

        lateinit var socket: WsClient
        socket = WsClient(
            url = url,
            hello = hello,
            tls = tlsConfig,
            onConnected = conn@{ name, _ ->
                synchronized(lock) { if (ws !== socket) return@conn }
                Log.i("SyncService", "connected to $name at $host")
                if (verifyName && name.isNotBlank() && !whitelistNameMatches(name)) {
                    Log.w("SyncService", "name $name rejected by whitelist")
                    rejectCurrent()
                } else {
                    AppState.onConnected(name, host)
                    updateNotification("Connected to $host")
                }
            },
            onClipboard = clip@{ clip ->
                synchronized(lock) { if (ws !== socket) return@clip }
                receive(clip)
            },
            onDisconnected = disc@{ reason ->
                synchronized(lock) {
                    if (ws !== socket) return@disc
                }
                Log.i("SyncService", "disconnected: ${reason ?: "unknown"}")
                AppState.onDisconnected(reason)
                updateNotification("Disconnected")
            },
            onReconnecting = recon@{ attempt ->
                synchronized(lock) { if (ws !== socket) return@recon }
                Log.i("SyncService", "reconnecting attempt $attempt")
                AppState.onReconnecting(attempt)
                updateNotification("Reconnecting ($host)...")
            },
            onConnectFailed = failed@{
                synchronized(lock) { if (ws !== socket) return@failed }
                Log.w("SyncService", "connect failed")
                onConnectFailed()
            },
            onError = err@{ msg ->
                synchronized(lock) { if (ws !== socket) return@err }
                Log.e("SyncService", "error: $msg")
                AppState.onError(msg)
            },
        )
        return socket
    }

    /** A whitelisted daemon presented the wrong identity: drop and move on. */
    private fun rejectCurrent() {
        stopWs()
        onConnectFailed()
    }

    /** Whitelist mode: connect to each whitelisted IP in turn. */
    private fun startWhitelistConnections() {
        whitelistCandidates = Prefs.whitelist(this).mapNotNull {
            it.ip.trim().takeIf { ip -> ip.isNotBlank() }?.let { ip -> ip to Prefs.serverPort(this) }
        }
        whitelistIndex = 0
        if (whitelistCandidates.isEmpty()) {
            AppState.onSearching()
            updateNotification("No whitelist IPs configured")
            return
        }
        connectToCandidate()
    }

    private fun connectToCandidate() {
        val candidates = whitelistCandidates
        if (candidates.isEmpty() || !running) return
        val idx = whitelistIndex % candidates.size
        whitelistIndex++
        val (host, port) = candidates[idx]
        connectTo(host, port, Prefs.tlsEnabled(this), verifyName = true, persist = false)
    }

    /** Advances to the next whitelist candidate after a failed attempt. */
    private fun onConnectFailed() {
        if (Prefs.connectionMode(this) != Prefs.MODE_WHITELIST) return
        if (whitelistCandidates.isEmpty()) return
        stopWs()
        scope.launch {
            delay(Constants.Whitelist.RETRY_DELAY_MS)
            if (running) connectToCandidate()
        }
    }

    private fun whitelistNameMatches(name: String): Boolean {
        val host = currentHost ?: return false
        val entry = Prefs.whitelist(this).firstOrNull { it.ip == host }
            ?: return false
        return entry.name.isBlank() || entry.name == name
    }

    private fun stopWs() {
        synchronized(lock) {
            ws?.stop()
            ws = null
        }
    }

    /** Push local clipboard text to the daemon. Called from the app UI or the
     * accessibility service while connected. */
    fun send(text: String): Boolean {
        val ok = ws?.send(text, Prefs.deviceName(this)) == true
        if (ok) {
            AppState.onSent(this, text)
            Log.i("SyncService", "sent ${text.length} chars")
        } else {
            Log.w("SyncService", "send failed (not connected?)")
        }
        return ok
    }

    /** Push a local image to the daemon. */
    fun sendImage(bytes: ByteArray, mime: String): Boolean {
        val ws = synchronized(lock) { ws }
        if (ws == null) {
            Log.w("SyncService", "sendImage: no active socket")
            return false
        }
        val ok = ws.sendImage(bytes, mime, Prefs.deviceName(this))
        if (ok) {
            AppState.onSent(this, "[image: $mime, ${bytes.size} bytes]")
            Log.i("SyncService", "sent ${bytes.size} byte $mime image")
        } else {
            Log.w("SyncService", "sendImage failed (not connected?)")
        }
        return ok
    }

    private fun receive(clip: Protocol.Clipboard) {
        val image = clip.image
        if (image != null) {
            Log.i("SyncService", "received ${image.size} byte ${clip.mime ?: "image"}")
            writeClipboardImage(image, clip.mime ?: Constants.Mime.IMAGE_PNG)
            AppState.onReceivedImage(this, clip.mime ?: Constants.Mime.IMAGE_PNG, image.size, clip.from)
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
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("clipshare", text))
        AppState.lastRemoteWritten = text
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
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newUri(contentResolver, "clipshare image", uri))
        AppState.lastRemoteWrittenImage = bytes
        Log.i("SyncService", "wrote image to clipboard: ${file.name}")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                Constants.Notification.CHANNEL_ID, "ClipShare sync", NotificationManager.IMPORTANCE_LOW
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
        return NotificationCompat.Builder(this, Constants.Notification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clipboard)
            .setContentTitle("ClipShare")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.Notification.ID, buildNotification(text))
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        stopWs()
        discovery?.stop()
        discovery = null
        AppState.onServiceStopped()
        Log.i("SyncService", "onDestroy")
        super.onDestroy()
    }
}

package win.downops.clipshare.sync

import android.content.Context
import win.downops.clipshare.certs.CertStore
import win.downops.clipshare.discover.DiscoveryManager
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.WsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Client-mode strategy: discovers desktop/Android servers and maintains a
 * WebSocket connection to the selected one (manual, discovered or whitelist).
 */
class ClientSyncMode(
    private val context: Context,
    private val events: SyncEvents,
) : SyncMode {

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

    override fun start() {
        running = true
        startDiscovery()
        connect()
    }

    override fun stop() {
        running = false
        scope.cancel()
        discovery?.stop()
        discovery = null
        stopWs()
    }

    override fun onStartCommand() {
        val shouldConnect = synchronized(lock) { ws == null }
        if (shouldConnect) connect()
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    private fun startDiscovery() {
        if (Prefs.connectionMode(context) == Prefs.MODE_WHITELIST) {
            Log.i("SyncService", "discovery skipped: whitelist mode")
            return
        }
        if (!Prefs.discoveryEnabled(context)) {
            Log.i("SyncService", "discovery skipped: disabled")
            return
        }
        Log.i("SyncService", "starting discovery")
        val beaconPort = Prefs.discoveryBeaconPort(context)
        val d = DiscoveryManager(context, beaconPort) { name, host, port, tls, _ ->
            Log.i("SyncService", "discovered $name at $host:$port (tls=$tls)")
            onDeviceFound(name, host, port, tls)
        }
        discovery = d
        d.start()
    }

    /** Called from the discovery threads whenever a daemon announces itself. */
    private fun onDeviceFound(name: String, host: String, port: Int, tls: Boolean) {
        if (!running) return
        if (!Prefs.autoConnect(context)) return
        connectTo(host, port, tls, verifyName = false, persist = true)
    }

    override fun switchTo(host: String, port: Int, tls: Boolean) {
        connectTo(host, port, tls, verifyName = false, persist = true)
    }

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    private fun connect() {
        if (Prefs.connectionMode(context) == Prefs.MODE_WHITELIST) {
            startWhitelistConnections()
            return
        }
        val host = currentHost ?: Prefs.serverHost(context).takeIf { it.isNotBlank() } ?: run {
            AppState.onSearching()
            events.updateNotification("Searching for servers...")
            Log.i("SyncService", "no host configured, searching via discovery")
            return
        }
        val port = if (currentPort > 0) currentPort else Prefs.serverPort(context)
        Log.i("SyncService", "connecting to $host:$port (tls=${Prefs.tlsEnabled(context)})")
        connectTo(host, port, Prefs.tlsEnabled(context), verifyName = false, persist = false)
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
                    Prefs.setServerHost(context, host)
                    Prefs.setServerPort(context, port)
                }
                AppState.onConnecting()
                events.updateNotification("Connecting to $host...")
                socket.start()
            }
        }
    }

    private fun buildSocket(host: String, port: Int, tls: Boolean, verifyName: Boolean): WsClient? {
        val token = Prefs.token(context)
        val scheme = if (tls) Constants.Protocol.Scheme.WSS else Constants.Protocol.Scheme.WS
        var url = "$scheme://$host:$port${Constants.Protocol.WS_PATH}"
        if (token.isNotBlank()) {
            url += "?token=" + token
        }
        val hello = Protocol.hello(Prefs.deviceName(context), Constants.Protocol.PLATFORM_ANDROID, Constants.App.VERSION)
        Log.i("SyncService", "opening $url")

        val tlsConfig = if (tls) {
            val clientTls = CertStore.clientTlsWithCert(context) ?: CertStore.trustOnlyTls(context)
            if (clientTls == null) {
                val msg = "TLS on, but no client certificate or trusted CA imported"
                Log.e("SyncService", msg)
                AppState.onError(msg)
                events.updateNotification("TLS on, but no certificate or CA imported")
                onConnectFailed()
                return null
            }
            clientTls
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
                    events.updateNotification("Connected to $host")
                }
            },
            onClipboard = clip@{ clip ->
                synchronized(lock) { if (ws !== socket) return@clip }
                events.receive(clip)
            },
            onDisconnected = disc@{ reason ->
                synchronized(lock) {
                    if (ws !== socket) return@disc
                }
                Log.i("SyncService", "disconnected: ${reason ?: "unknown"}")
                AppState.onDisconnected(reason)
                events.updateNotification("Disconnected")
            },
            onReconnecting = recon@{ attempt ->
                synchronized(lock) { if (ws !== socket) return@recon }
                Log.i("SyncService", "reconnecting attempt $attempt")
                AppState.onReconnecting(attempt)
                events.updateNotification("Reconnecting ($host)...")
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

    // ------------------------------------------------------------------
    // Whitelist mode
    // ------------------------------------------------------------------

    /** Whitelist mode: connect to each whitelisted IP in turn. */
    private fun startWhitelistConnections() {
        whitelistCandidates = Prefs.whitelist(context).mapNotNull {
            it.ip.trim().takeIf { ip -> ip.isNotBlank() }?.let { ip -> ip to Prefs.serverPort(context) }
        }
        whitelistIndex = 0
        if (whitelistCandidates.isEmpty()) {
            AppState.onSearching()
            events.updateNotification("No whitelist IPs configured")
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
        connectTo(host, port, Prefs.tlsEnabled(context), verifyName = true, persist = false)
    }

    /** Advances to the next whitelist candidate after a failed attempt. */
    private fun onConnectFailed() {
        if (Prefs.connectionMode(context) != Prefs.MODE_WHITELIST) return
        if (whitelistCandidates.isEmpty()) return
        stopWs()
        scope.launch {
            delay(Constants.Whitelist.RETRY_DELAY_MS)
            if (running) connectToCandidate()
        }
    }

    private fun whitelistNameMatches(name: String): Boolean {
        val host = currentHost ?: return false
        val entry = Prefs.whitelist(context).firstOrNull { it.ip == host }
            ?: return false
        return entry.name.isBlank() || entry.name == name
    }

    private fun stopWs() {
        synchronized(lock) {
            ws?.stop()
            ws = null
        }
    }

    // ------------------------------------------------------------------
    // Send
    // ------------------------------------------------------------------

    override fun send(text: String): Boolean {
        val ok = ws?.send(text, Prefs.deviceName(context)) == true
        if (ok) {
            AppState.onSent(context, text)
            Log.i("SyncService", "sent ${text.length} chars")
        } else {
            Log.w("SyncService", "send failed (not connected?)")
        }
        return ok
    }

    override fun sendImage(bytes: ByteArray, mime: String): Boolean {
        val socket = synchronized(lock) { ws }
        if (socket == null) {
            Log.w("SyncService", "sendImage: no active socket")
            return false
        }
        val ok = socket.sendImage(bytes, mime, Prefs.deviceName(context))
        if (ok) {
            AppState.onSent(context, "[image: $mime, ${bytes.size} bytes]")
            Log.i("SyncService", "sent ${bytes.size} byte $mime image")
        } else {
            Log.w("SyncService", "sendImage failed (not connected?)")
        }
        return ok
    }
}
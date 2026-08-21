package win.downops.clipshare.sync.clientmode

import android.content.Context
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.sync.SyncEvents
import win.downops.clipshare.sync.SyncMode
import win.downops.clipshare.sync.SyncSend
import win.downops.clipshare.util.HostUtil
import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.WsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Client-mode strategy: discovers desktop/Android servers and maintains a
 * WebSocket connection to the selected one (manual, discovered or whitelist).
 *
 * The moving parts live in their own classes: [DiscoveryController] (finding
 * servers), [WhitelistConnector] (whitelist candidates and retries),
 * [ClientSocketFactory] (dialing + TLS), with this class coordinating target
 * selection and owning the current connection.
 */
class ClientSyncMode(
    private val context: Context,
    private val events: SyncEvents,
) : SyncMode, ClientConnectionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sockets = ClientSocketFactory(context, this) {
        events.updateNotification(TLS_UNAVAILABLE_MSG)
        handleConnectFailed()
    }
    private val discovery = DiscoveryController(context) { name, host, port, tls ->
        onDeviceFound(name, host, port, tls)
    }
    private val whitelist = WhitelistConnector(context, scope)

    @Volatile
    private var running = false

    private val lock = Object()

    @Volatile
    private var ws: WsClient? = null

    /** The daemon we are currently (or last) targeting. */
    @Volatile
    private var currentTarget: ClientTarget? = null

    /** Normalized manual host from "Server (IP or hostname)" when set. */
    @Volatile
    private var manualTarget: String? = null

    /**
     * True while the manual host is the intended target (attempting to connect
     * or already connected). Discovery announcements are ignored until the
     * manual host is unreachable, so a filled host field always wins.
     */
    @Volatile
    private var manualActive = false

    override fun start() {
        running = true
        discovery.startIfEnabled()
        connect()
    }

    override fun stop() {
        running = false
        scope.cancel()
        discovery.stop()
        closeSocket()
    }

    override fun onStartCommand() {
        val shouldConnect = synchronized(lock) { ws == null }
        if (shouldConnect) connect()
    }

    // ------------------------------------------------------------------
    // Target selection
    // ------------------------------------------------------------------

    private fun connect() {
        if (Prefs.connectionMode(context) == Prefs.MODE_WHITELIST) {
            startWhitelistConnections()
            return
        }
        val manual = Prefs.serverHost(context).takeIf { it.isNotBlank() }
        if (manual != null) {
            val normalized = HostUtil.normalize(manual)
            manualTarget = normalized
            manualActive = true
            Log.i(TAG, "connecting to manual host $manual (tls=${Prefs.tlsEnabled(context)})")
            connectTo(
                ClientTarget(normalized, Prefs.serverPort(context), Prefs.tlsEnabled(context))
            )
        } else {
            manualTarget = null
            manualActive = false
            AppState.onSearching()
            events.updateNotification("Searching for servers...")
            Log.i(TAG, "no host configured, searching via discovery")
        }
    }

    override fun switchTo(host: String, port: Int, tls: Boolean) {
        // Device taps are ephemeral and never rewrite the manual host field.
        manualActive = false
        connectTo(ClientTarget(HostUtil.normalize(host), port, tls))
    }

    /** Called by [DiscoveryController] whenever a daemon announces itself. */
    private fun onDeviceFound(name: String, host: String, port: Int, tls: Boolean) {
        if (!running) return
        // A filled manual host wins: only fall back to discovery once it is
        // unreachable or not configured.
        if (manualActive) return
        connectTo(ClientTarget(HostUtil.normalize(host), port, tls))
    }

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    private fun connectTo(target: ClientTarget) {
        synchronized(lock) {
            if (currentTarget == target && ws != null) {
                Log.d(TAG, "already connected/connecting to ${target.host}:${target.port}")
                return
            }
            currentTarget = target
            ws?.stop()
            val socket = sockets.build(target)
            ws = socket
            if (socket != null) {
                if (target.persist) {
                    Prefs.setServerHost(context, target.host)
                    Prefs.setServerPort(context, target.port)
                }
                AppState.onConnecting()
                events.updateNotification("Connecting to ${target.host}...")
                socket.start()
            } else {
                // A TLS failure must not block the discovery fallback.
                manualActive = false
            }
        }
    }

    private fun closeSocket() {
        synchronized(lock) {
            ws?.stop()
            ws = null
        }
    }

    /** A whitelisted daemon presented the wrong identity: drop and move on. */
    private fun rejectCurrent() {
        closeSocket()
        handleConnectFailed()
    }

    // ------------------------------------------------------------------
    // Whitelist mode
    // ------------------------------------------------------------------

    private fun startWhitelistConnections() {
        if (!whitelist.refresh()) {
            AppState.onSearching()
            events.updateNotification("No whitelist IPs configured")
            return
        }
        connectToCandidate()
    }

    private fun connectToCandidate() {
        if (whitelist.isEmpty() || !running) return
        val (host, port) = whitelist.next()
        connectTo(
            ClientTarget(host, port, Prefs.tlsEnabled(context), verifyName = true)
        )
    }

    /** Advances to the next whitelist candidate after a failed attempt. */
    private fun handleConnectFailed() {
        if (Prefs.connectionMode(context) != Prefs.MODE_WHITELIST) return
        if (whitelist.isEmpty()) return
        closeSocket()
        whitelist.scheduleRetry(isRunning = { running }, retry = { connectToCandidate() })
    }

    // ------------------------------------------------------------------
    // ClientConnectionListener (guarded: stale sockets are ignored)
    // ------------------------------------------------------------------

    override fun onConnected(socket: WsClient, name: String, host: String) {
        synchronized(lock) { if (ws !== socket) return }
        Log.i(TAG, "connected to $name at $host")
        manualActive = currentTarget?.host == manualTarget
        val target = currentTarget
        if (target?.verifyName == true && name.isNotBlank() && !whitelist.nameMatches(target.host, name)) {
            Log.w(TAG, "name $name rejected by whitelist")
            rejectCurrent()
        } else {
            AppState.onConnected(name, host)
            events.updateNotification("Connected to $host")
        }
    }

    override fun onClipboard(socket: WsClient, clip: Protocol.Clipboard) {
        synchronized(lock) { if (ws !== socket) return }
        events.receive(clip)
    }

    override fun onDisconnected(socket: WsClient, reason: String?) {
        synchronized(lock) { if (ws !== socket) return }
        Log.i(TAG, "disconnected: ${reason ?: "unknown"}")
        manualActive = false
        AppState.onDisconnected(reason)
        events.updateNotification("Disconnected")
    }

    override fun onReconnecting(socket: WsClient, attempt: Int) {
        synchronized(lock) { if (ws !== socket) return }
        Log.i(TAG, "reconnecting attempt $attempt")
        manualActive = currentTarget?.host == manualTarget
        AppState.onReconnecting(attempt)
        events.updateNotification("Reconnecting (${currentTarget?.host})...")
    }

    override fun onConnectFailed(socket: WsClient) {
        synchronized(lock) { if (ws !== socket) return }
        Log.w(TAG, "connect failed")
        manualActive = false
        handleConnectFailed()
    }

    override fun onError(socket: WsClient, msg: String) {
        synchronized(lock) { if (ws !== socket) return }
        Log.e(TAG, "error: $msg")
        AppState.onError(msg)
    }

    // ------------------------------------------------------------------
    // Send
    // ------------------------------------------------------------------

    override fun send(text: String): Boolean {
        val ok = ws?.send(text, Prefs.deviceName(context)) == true
        SyncSend.text(context, text, ok)
        return ok
    }

    override fun sendImage(bytes: ByteArray, mime: String): Boolean {
        val socket = synchronized(lock) { ws }
        if (socket == null) {
            Log.w(TAG, "sendImage: no active socket")
            return false
        }
        val ok = socket.sendImage(bytes, mime, Prefs.deviceName(context))
        SyncSend.image(context, mime, bytes, ok)
        return ok
    }

    private companion object {
        const val TAG = "SyncService"
        const val TLS_UNAVAILABLE_MSG = "TLS on, but no certificate or CA imported"
    }
}

package win.downops.clipshare.sync.servermode

import android.content.Context
import win.downops.clipshare.discover.DiscoveryAdvertiser
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.sync.SyncEvents
import win.downops.clipshare.sync.SyncMode
import win.downops.clipshare.sync.SyncSend
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.WsServer

/**
 * Server-mode strategy: listens for inbound WebSocket connections from other
 * ClipShare clients and advertises itself via mDNS/UDP beacons.
 *
 * TLS material preparation lives in [ServerTls]; this class wires the server,
 * relays received clips to the other clients, and handles sends.
 */
class ServerSyncMode(
    private val context: Context,
    private val events: SyncEvents,
) : SyncMode {

    private var wsServer: WsServer? = null
    private var advertiser: DiscoveryAdvertiser? = null

    override fun start() {
        startServerMode()
    }

    override fun stop() {
        wsServer?.stop()
        wsServer = null
        advertiser?.stop()
        advertiser = null
    }

    private fun startServerMode() {
        val port = Prefs.serverPort(context)
        val material = ServerTls(context) { events.updateNotification(it) }
            .prepare(Prefs.serverTlsEnabled(context)) ?: return

        val bindHost = when (Prefs.serverBindIpVersion(context)) {
            Prefs.IP_VERSION_IPV4 -> "0.0.0.0"
            Prefs.IP_VERSION_IPV6 -> "::"
            else -> "0.0.0.0"
        }
        val server = WsServer(
            port = port,
            bindHost = bindHost,
            deviceName = Prefs.deviceName(context),
            keyStore = material.keyStore,
            keyStorePassword = material.password,
            trustStore = material.trustStore,
            serverToken = Prefs.serverToken(context),
            onReceived = { from, clip ->
                events.receive(clip)
                broadcastReceived(clip, skipFrom = from)
            },
            onClientChange = { count ->
                AppState.onServerClientCountChanged(count)
                events.updateNotification(
                    if (count == 0) "Server running on :$port (no clients)"
                    else "Server running on :$port ($count client${if (count == 1) "" else "s"})"
                )
            },
        )
        wsServer = server
        server.start()
        AppState.onServerStarted(port)
        events.updateNotification("Server running on :$port")

        val adv = DiscoveryAdvertiser(context, Prefs.discoveryBeaconPort(context))
        advertiser = adv
        adv.start(Prefs.deviceName(context), port, Prefs.serverTlsEnabled(context))
    }

    /** Broadcast a just-received clipboard item to every other connected client. */
    private fun broadcastReceived(clip: Protocol.Clipboard, skipFrom: String) {
        val server = wsServer ?: return
        val from = Prefs.deviceName(context)
        clip.text?.let { server.broadcast(it, from, skipFrom) }
        clip.image?.let { server.broadcastImage(it, clip.mime ?: Constants.Mime.IMAGE_PNG, from, skipFrom) }
    }

    override fun send(text: String): Boolean {
        val ok = wsServer?.broadcast(text, Prefs.deviceName(context)) == true
        SyncSend.text(context, text, ok)
        return ok
    }

    override fun sendImage(bytes: ByteArray, mime: String): Boolean {
        val ok = wsServer?.broadcastImage(bytes, mime, Prefs.deviceName(context)) == true
        SyncSend.image(context, mime, bytes, ok)
        return ok
    }
}

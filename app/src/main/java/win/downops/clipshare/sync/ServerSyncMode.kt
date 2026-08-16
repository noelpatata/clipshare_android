package win.downops.clipshare.sync

import android.content.Context
import win.downops.clipshare.certs.ServerCertManager
import win.downops.clipshare.discover.DiscoveryAdvertiser
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.WsServer

/**
 * Server-mode strategy: listens for inbound WebSocket connections from other
 * ClipShare clients and advertises itself via mDNS/UDP beacons.
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
        val tls = Prefs.serverTlsEnabled(context)

        if (tls && !ServerCertManager.hasCerts(context)) {
            Log.i("SyncService", "generating server certificates")
            ServerCertManager.generate(context, Prefs.deviceName(context))
        }

        val keyStore = if (tls) ServerCertManager.loadKeyStore(context) else null
        if (tls && keyStore == null) {
            val msg = "Server TLS enabled but certificate failed to load"
            Log.e("SyncService", msg)
            AppState.onError(msg)
            events.updateNotification(msg)
            return
        }

        val server = WsServer(
            port = port,
            deviceName = Prefs.deviceName(context),
            keyStore = keyStore,
            keyStorePassword = if (tls) Constants.Pkcs12.PASSWORD.toCharArray() else null,
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

        val beaconPort = Prefs.discoveryBeaconPort(context)
        val adv = DiscoveryAdvertiser(context, beaconPort)
        advertiser = adv
        adv.start(Prefs.deviceName(context), port, tls)
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
        if (ok) {
            AppState.onSent(context, text)
            Log.i("SyncService", "sent ${text.length} chars")
        } else {
            Log.w("SyncService", "send failed (not connected?)")
        }
        return ok
    }

    override fun sendImage(bytes: ByteArray, mime: String): Boolean {
        val ok = wsServer?.broadcastImage(bytes, mime, Prefs.deviceName(context)) == true
        if (ok) {
            AppState.onSent(context, "[image: $mime, ${bytes.size} bytes]")
            Log.i("SyncService", "sent ${bytes.size} byte $mime image")
        } else {
            Log.w("SyncService", "sendImage failed (not connected?)")
        }
        return ok
    }
}
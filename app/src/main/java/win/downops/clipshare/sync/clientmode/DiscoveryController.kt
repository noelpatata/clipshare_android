package win.downops.clipshare.sync.clientmode

import android.content.Context
import win.downops.clipshare.discover.DiscoveryManager
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs

/**
 * LAN discovery for client mode: starts the mDNS/UDP-beacon [DiscoveryManager]
 * when enabled (and not in whitelist mode) and forwards announced servers to
 * [onDeviceFound].
 */
class DiscoveryController(
    private val context: Context,
    private val onDeviceFound: (name: String, host: String, port: Int, tls: Boolean) -> Unit,
) {

    private var discovery: DiscoveryManager? = null

    /** Starts discovery unless disabled or whitelist mode is configured. */
    fun startIfEnabled() {
        if (Prefs.connectionMode(context) == Prefs.MODE_WHITELIST) {
            Log.i(TAG, "discovery skipped: whitelist mode")
            return
        }
        if (!Prefs.discoveryEnabled(context)) {
            Log.i(TAG, "discovery skipped: disabled")
            return
        }
        Log.i(TAG, "starting discovery")
        val d = DiscoveryManager(
            context, Prefs.discoveryBeaconPort(context), Prefs.ipVersion(context),
        ) { name, host, port, tls, _ ->
            Log.i(TAG, "discovered $name at $host:$port (tls=$tls)")
            onDeviceFound(name, host, port, tls)
        }
        discovery = d
        d.start()
    }

    fun stop() {
        discovery?.stop()
        discovery = null
    }

    private companion object {
        const val TAG = "SyncService"
    }
}

package win.downops.clipshare.discover

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.core.content.ContextCompat
import win.downops.clipshare.logs.Log
import win.downops.clipshare.state.AppState
import win.downops.clipshare.state.DiscoveredDevice
import win.downops.clipshare.util.Constants
import win.downops.clipshare.util.HostUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Discovers desktop daemons via mDNS (NsdManager) and UDP broadcast beacons.
 * [onDeviceFound] fires for every newly seen device so the caller can act on it.
 */
class DiscoveryManager(
    private val context: Context,
    private val beaconPort: Int,
    private val ipVersion: String,
    private val onDeviceFound: (name: String, host: String, port: Int, tls: Boolean, source: String) -> Unit,
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val multicastLock: WifiManager.MulticastLock? =
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.let {
            it.createMulticastLock("clipshare_discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }

    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var running = false

    private val devices = mutableMapOf<String, DiscoveredDevice>()

    fun start() {
        if (running) return
        running = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 3)
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(
                    service,
                    mainExecutor,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val rawHost = info.host?.hostAddress ?: return
                            val port = info.port.takeIf { it != 0 } ?: Constants.Discovery.DEFAULT_SERVER_PORT
                            val name = info.serviceName
                            val tls = (info.attributes?.get("tls")
                                ?.let { String(it, Charsets.UTF_8) } == "true")
                            reportDevice(name, rawHost, port, tls, "mdns")
                        }
                    }
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}
        }
        nsdManager.discoverServices(
            Constants.Discovery.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener
        )

        scope.launch {
            runUdpListener()
        }
    }

    private fun runUdpListener() {
        val sock = try {
            DatagramSocket(beaconPort, InetAddress.getByName(Constants.Discovery.DATAGRAM_BIND_ADDR))
        } catch (_: Exception) {
            return
        }
        socket = sock
        sock.broadcast = true
        val buf = ByteArray(4096)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                val json = String(packet.data, 0, packet.length)
                val obj = JSONObject(json)
                val name = obj.optString("name")
                val port = obj.optInt("port", Constants.Discovery.DEFAULT_SERVER_PORT)
                val tls = obj.optBoolean("tls", false)
                val rawHost = packet.address.hostAddress ?: continue
                reportDevice(name, rawHost, port, tls, "beacon")
            } catch (_: Exception) {
                if (!running) return
            }
        }
    }

    private fun reportDevice(name: String, rawHost: String, port: Int, tls: Boolean, source: String) {
        val host = HostUtil.normalize(rawHost)
        if (host.isBlank()) return
        if (!HostUtil.matchesIpVersion(host, ipVersion)) {
            Log.d("DiscoveryManager", "skipping $host due to IP version preference $ipVersion")
            return
        }
        val displayName = name.ifBlank { host }
        val fallbackName = if (source == "beacon") Constants.Protocol.PLATFORM_DESKTOP else host
        val resolvedName = displayName.ifBlank { fallbackName }
        add(resolvedName, host, port, tls, source)
        onDeviceFound(resolvedName, host, port, tls, source)
    }

    private fun add(name: String, host: String, port: Int, tls: Boolean, source: String) {
        if (host.isBlank()) return
        synchronized(this) {
            val key = "$host:$port"
            val old = devices[key]
            if (old != null && old.name == name && old.source == source && old.tls == tls) return
            devices[key] = DiscoveredDevice(name, host, port, source, tls)
            AppState.setDiscovered(devices.values.sortedBy { it.name })
        }
    }

    fun stop() {
        running = false
        discoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
        }
        discoveryListener = null
        socket?.close()
        socket = null
        runCatching { multicastLock?.release() }
        scope.cancel()
        AppState.setDiscovered(emptyList())
    }
}

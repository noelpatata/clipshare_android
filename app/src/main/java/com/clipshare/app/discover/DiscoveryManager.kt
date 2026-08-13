package com.clipshare.app.discover

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.clipshare.app.state.AppState
import com.clipshare.app.state.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val SERVICE_TYPE = "_clipshare._tcp"
private const val BEACON_PORT = 40404

/**
 * Discovers desktop daemons via mDNS (NsdManager) and UDP broadcast beacons.
 * [onDeviceFound] fires for every newly seen device so the caller can act on it.
 */
class DiscoveryManager(
    private val context: Context,
    private val onDeviceFound: (name: String, host: String, port: Int, source: String) -> Unit,
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

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(
                    service,
                    mainExecutor,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val port = info.port.takeIf { it != 0 } ?: 40403
                            val name = info.serviceName.ifBlank { host }
                            add(name, host, port, "mdns")
                            onDeviceFound(name, host, port, "mdns")
                        }
                    }
                )
            }

            override fun onServiceLost(service: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}
        }
        nsdManager.discoverServices(
            SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener
        )

        scope.launch {
            runUdpListener()
        }
    }

    private fun runUdpListener() {
        val sock = try {
            DatagramSocket(BEACON_PORT, InetAddress.getByName("0.0.0.0"))
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
                val name = obj.optString("name").ifBlank { "desktop" }
                val port = obj.optInt("port", 40403)
                val host = packet.address.hostAddress ?: continue
                add(name, host, port, "beacon")
                onDeviceFound(name, host, port, "beacon")
            } catch (_: Exception) {
                if (!running) return
            }
        }
    }

    private fun add(name: String, host: String, port: Int, source: String) {
        if (host.isBlank()) return
        synchronized(this) {
            val key = "$host:$port"
            val old = devices[key]
            if (old != null && old.name == name && old.source == source) return
            devices[key] = DiscoveredDevice(name, host, port, source)
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

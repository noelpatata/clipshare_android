package win.downops.clipshare.discover

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import win.downops.clipshare.logs.Log
import win.downops.clipshare.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.coroutines.coroutineContext

/**
 * Advertises this Android device as a ClipShare server via mDNS and UDP
 * broadcast beacons. Used only when the app is in server mode.
 */
class DiscoveryAdvertiser(
    private val context: Context,
    private val beaconPort: Int,
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var running = false

    fun start(name: String, port: Int, tls: Boolean) {
        if (running) return
        running = true
        Log.i("DiscoveryAdvertiser", "advertising as $name on port $port (tls=$tls)")

        startMdns(name, port, tls)
        scope.launch { sendBeacons(name, port, tls) }
    }

    fun stop() {
        running = false
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
        }
        registrationListener = null
        socket?.close()
        socket = null
        scope.cancel()
    }

    private fun startMdns(name: String, port: Int, tls: Boolean) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = Constants.Discovery.SERVICE_TYPE
            setPort(port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("tls", tls.toString())
                setAttribute("platform", Constants.Protocol.PLATFORM_ANDROID)
            }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                Log.i("DiscoveryAdvertiser", "mDNS registered: ${info?.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w("DiscoveryAdvertiser", "mDNS registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                Log.i("DiscoveryAdvertiser", "mDNS unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w("DiscoveryAdvertiser", "mDNS unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { e ->
            Log.e("DiscoveryAdvertiser", "registerService failed", e)
        }
    }

    private suspend fun sendBeacons(name: String, port: Int, tls: Boolean) {
        val sock = try {
            DatagramSocket().apply { broadcast = true }
        } catch (e: Exception) {
            Log.e("DiscoveryAdvertiser", "failed to create beacon socket", e)
            return
        }
        socket = sock

        val payload = JSONObject()
            .put("name", name)
            .put("port", port)
            .put("tls", tls)
            .put("platform", Constants.Protocol.PLATFORM_ANDROID)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val address = InetAddress.getByName(Constants.Discovery.BEACON_BROADCAST_ADDR)
        val packet = DatagramPacket(payload, payload.size, address, beaconPort)

        while (running) {
            try {
                sock.send(packet)
            } catch (e: Exception) {
                if (running) Log.w("DiscoveryAdvertiser", "beacon send failed: ${e.message}")
            }
            delay(Constants.Discovery.BEACON_INTERVAL_MS)
        }
    }
}

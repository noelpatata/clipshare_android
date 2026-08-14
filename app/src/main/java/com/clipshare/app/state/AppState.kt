package com.clipshare.app.state

import android.content.Context
import android.content.Intent
import com.clipshare.app.history.HistoryEntry
import com.clipshare.app.history.HistoryStore
import com.clipshare.app.logs.Log
import com.clipshare.app.settings.Prefs
import com.clipshare.app.sync.SyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
    val source: String,
    val tls: Boolean = false,
)

/** App-wide state shared between the UI, service and tile. */
object AppState {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _serverName = MutableStateFlow<String?>(null)
    val serverName: StateFlow<String?> = _serverName.asStateFlow()

    private val _connectedIp = MutableStateFlow<String?>(null)
    val connectedIp: StateFlow<String?> = _connectedIp.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()

    @Volatile
    var service: SyncService? = null
        private set

    /** Last text the service wrote to the local clipboard (loop protection). */
    @Volatile
    var lastRemoteWritten: String? = null

    /** Last image bytes the service wrote to the local clipboard (loop protection). */
    @Volatile
    var lastRemoteWrittenImage: ByteArray? = null

    fun startSync(ctx: Context) {
        _running.value = true
        _status.value = "Starting..."
        Log.i("AppState", "startSync")
        ctx.startForegroundService(Intent(ctx, SyncService::class.java))
    }

    fun stopSync(ctx: Context) {
        _running.value = false
        _status.value = "Stopped"
        _connected.value = false
        _serverName.value = null
        _connectedIp.value = null
        Log.i("AppState", "stopSync")
        ctx.stopService(Intent(ctx, SyncService::class.java))
    }

    fun onServiceStarted(s: SyncService) {
        service = s
        _running.value = true
        Log.i("AppState", "service started")
    }

    fun onServiceStopped() {
        service = null
        _running.value = false
        _connected.value = false
        _serverName.value = null
        _connectedIp.value = null
        _status.value = "Stopped"
        Log.i("AppState", "service stopped")
    }

    fun onConnecting() {
        _connected.value = false
        _status.value = "Connecting..."
        Log.i("AppState", "connecting")
    }

    fun onSearching() {
        _connected.value = false
        _status.value = "Searching for desktops..."
        Log.i("AppState", "searching")
    }

    fun onConnected(name: String, host: String) {
        _connected.value = true
        _serverName.value = name
        _connectedIp.value = host
        _status.value = "Connected to $name"
        _lastError.value = null
        Log.i("AppState", "connected to $name at $host")
    }

    fun onDisconnected(reason: String?) {
        _connected.value = false
        _serverName.value = null
        _status.value = if (reason.isNullOrBlank()) "Disconnected" else "Disconnected: $reason"
        Log.i("AppState", "disconnected: ${reason ?: "unknown"}")
    }

    fun onReconnecting(attempt: Int) {
        _connected.value = false
        _status.value = "Reconnecting (attempt $attempt)..."
        Log.i("AppState", "reconnecting attempt $attempt")
    }

    fun onError(msg: String?) {
        _lastError.value = msg
        if (msg != null && !_connected.value) {
            _status.value = "Error: $msg"
        }
        if (msg != null) Log.e("AppState", "error: $msg")
    }

    fun onReceived(ctx: Context, text: String, from: String) {
        val entry = HistoryEntry(text = text, from = from.ifBlank { "remote" }, ts = System.currentTimeMillis(), incoming = true)
        _history.value = listOf(entry) + _history.value
        HistoryStore.append(ctx, entry)
    }

    fun onReceivedImage(ctx: Context, mime: String, size: Int, from: String) {
        val entry = HistoryEntry(
            text = "[image: $mime, ${size} bytes]",
            from = from.ifBlank { "remote" },
            ts = System.currentTimeMillis(),
            incoming = true,
            isImage = true,
        )
        _history.value = listOf(entry) + _history.value
        HistoryStore.append(ctx, entry)
    }

    fun onSent(ctx: Context, text: String) {
        val name = Prefs.deviceName(ctx)
        val entry = HistoryEntry(text = text, from = name, ts = System.currentTimeMillis(), incoming = false)
        _history.value = listOf(entry) + _history.value
        HistoryStore.append(ctx, entry)
    }

    fun loadHistory(ctx: Context) {
        _history.value = HistoryStore.load(ctx)
    }

    fun setDiscovered(devices: List<DiscoveredDevice>) {
        _discovered.value = devices
    }
}

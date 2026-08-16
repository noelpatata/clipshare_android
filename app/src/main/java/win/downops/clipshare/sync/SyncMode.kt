package win.downops.clipshare.sync

/**
 * Strategy for the sync foreground service. The concrete implementation is
 * chosen once in [SyncService] based on the configured app mode (client or
 * server), so the service itself only needs a single dispatch.
 */
interface SyncMode {

    /** Start the mode's background work (connections, server, discovery). */
    fun start()

    /** Stop all background work and release resources. */
    fun stop()

    /** Called on every service start command; client mode uses it to (re)connect. */
    fun onStartCommand() {}

    /** Push clipboard text to peers. Returns false when not connected. */
    fun send(text: String): Boolean

    /** Push clipboard image bytes to peers. Returns false when not connected. */
    fun sendImage(bytes: ByteArray, mime: String): Boolean

    /** Manually switch to a specific peer (client mode only; no-op in server mode). */
    fun switchTo(host: String, port: Int, tls: Boolean) {}
}
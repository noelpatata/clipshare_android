package win.downops.clipshare.sync.clientmode

/**
 * One client-mode connection target: where to dial and how to treat the
 * resulting connection.
 */
data class ClientTarget(
    val host: String,
    val port: Int,
    val tls: Boolean,

    /** Reject the daemon when its hello name does not match the whitelist. */
    val verifyName: Boolean = false,

    /** Persist host/port as the manual server when the connection succeeds. */
    val persist: Boolean = false,
)

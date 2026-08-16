package win.downops.clipshare.settings

import android.content.Context
import android.os.Build
import win.downops.clipshare.util.Constants
import org.json.JSONArray
import org.json.JSONObject

/** One allowed device in whitelist mode. Matching is on name OR ip. */
data class WhitelistEntry(
    val name: String,
    val ip: String,
)

/** SharedPreferences-backed settings. */
object Prefs {
    const val MODE_DISCOVER = "discover"
    const val MODE_WHITELIST = "whitelist"

    const val APP_MODE_CLIENT = "client"
    const val APP_MODE_SERVER = "server"

    private const val FILE = "clipshare_prefs"

    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_APP_MODE = "app_mode"
    private const val KEY_SERVER_HOST = "server_host"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SERVER_TLS_ENABLED = "server_tls_enabled"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_DISCOVERY = "discovery_enabled"
    private const val KEY_DISCOVERY_BEACON_PORT = "discovery_beacon_port"
    private const val KEY_TLS_ENABLED = "tls_enabled"
    private const val KEY_CONNECTION_MODE = "connection_mode"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_MAX_IMAGE_PAYLOAD_KB = "max_image_payload_kb"
    private const val KEY_MAX_LOG_FILE_KB = "max_log_file_kb"
    private const val KEY_MAX_HISTORY_ENTRIES = "max_history_entries"
    private const val KEY_CLIPBOARD_POLL_MS = "clipboard_poll_ms"
    private const val KEY_CLIENT_CERTS = "client_certs"
    private const val KEY_TRUSTED_CAS = "trusted_cas"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun deviceName(ctx: Context): String =
        prefs(ctx).getString(KEY_DEVICE_NAME, Build.MODEL) ?: Build.MODEL

    fun appMode(ctx: Context): String =
        prefs(ctx).getString(KEY_APP_MODE, APP_MODE_CLIENT) ?: APP_MODE_CLIENT

    fun serverHost(ctx: Context): String =
        prefs(ctx).getString(KEY_SERVER_HOST, "") ?: ""

    fun serverPort(ctx: Context): Int = prefs(ctx).getInt(KEY_SERVER_PORT, Constants.Discovery.DEFAULT_SERVER_PORT)

    fun serverTlsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SERVER_TLS_ENABLED, false)

    fun token(ctx: Context): String = prefs(ctx).getString(KEY_TOKEN, "") ?: ""

    fun autoConnect(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO_CONNECT, true)

    fun discoveryEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DISCOVERY, true)

    fun discoveryBeaconPort(ctx: Context): Int =
        prefs(ctx).getInt(KEY_DISCOVERY_BEACON_PORT, Constants.Discovery.DEFAULT_BEACON_PORT)

    fun tlsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TLS_ENABLED, false)

    fun connectionMode(ctx: Context): String =
        prefs(ctx).getString(KEY_CONNECTION_MODE, MODE_DISCOVER) ?: MODE_DISCOVER

    fun maxImagePayloadKb(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MAX_IMAGE_PAYLOAD_KB, Constants.Image.DEFAULT_MAX_PAYLOAD_KB)

    fun maxLogFileKb(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MAX_LOG_FILE_KB, Constants.Log.DEFAULT_MAX_FILE_KB)

    fun maxHistoryEntries(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MAX_HISTORY_ENTRIES, Constants.History.DEFAULT_MAX_ENTRIES)

    fun clipboardPollMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_CLIPBOARD_POLL_MS, Constants.Clipboard.SYNC_POLL_MS)
            .coerceIn(Constants.Clipboard.MIN_POLL_MS, Constants.Clipboard.MAX_POLL_MS)

    fun clientCertsJson(ctx: Context): String =
        prefs(ctx).getString(KEY_CLIENT_CERTS, "[]") ?: "[]"

    fun trustedCasJson(ctx: Context): String =
        prefs(ctx).getString(KEY_TRUSTED_CAS, "[]") ?: "[]"

    fun whitelist(ctx: Context): List<WhitelistEntry> {
        val raw = prefs(ctx).getString(KEY_WHITELIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(WhitelistEntry(o.optString("name"), o.optString("ip")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setDeviceName(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_DEVICE_NAME, value).apply()

    fun setAppMode(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_APP_MODE, value).apply()

    fun setServerHost(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_SERVER_HOST, value.trim()).apply()

    fun setServerPort(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_SERVER_PORT, value).apply()

    fun setServerTlsEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SERVER_TLS_ENABLED, value).apply()

    fun setToken(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_TOKEN, value.trim()).apply()

    fun setAutoConnect(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    fun setDiscoveryEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DISCOVERY, value).apply()

    fun setDiscoveryBeaconPort(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_DISCOVERY_BEACON_PORT, value.coerceAtLeast(1)).apply()

    fun setTlsEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_TLS_ENABLED, value).apply()

    fun setConnectionMode(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_CONNECTION_MODE, value).apply()

    fun setMaxImagePayloadKb(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_MAX_IMAGE_PAYLOAD_KB, value.coerceIn(Constants.Image.MIN_MAX_PAYLOAD_KB, Constants.Image.MAX_MAX_PAYLOAD_KB)).apply()

    fun setMaxLogFileKb(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_MAX_LOG_FILE_KB, value.coerceIn(Constants.Log.MIN_MAX_FILE_KB, Constants.Log.MAX_MAX_FILE_KB)).apply()

    fun setMaxHistoryEntries(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_MAX_HISTORY_ENTRIES, value.coerceIn(Constants.History.MIN_MAX_ENTRIES, Constants.History.MAX_MAX_ENTRIES)).apply()

    fun setClipboardPollMs(ctx: Context, value: Long) =
        prefs(ctx).edit().putLong(KEY_CLIPBOARD_POLL_MS, value.coerceIn(Constants.Clipboard.MIN_POLL_MS, Constants.Clipboard.MAX_POLL_MS)).apply()

    fun setClientCertsJson(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_CLIENT_CERTS, value).apply()

    fun setTrustedCasJson(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_TRUSTED_CAS, value).apply()

    fun setWhitelist(ctx: Context, entries: List<WhitelistEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().put("name", e.name).put("ip", e.ip))
        }
        prefs(ctx).edit().putString(KEY_WHITELIST, arr.toString()).apply()
    }
}

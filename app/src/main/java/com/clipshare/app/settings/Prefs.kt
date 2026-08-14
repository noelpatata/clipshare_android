package com.clipshare.app.settings

import android.content.Context
import android.os.Build
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

    private const val FILE = "clipshare_prefs"

    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_SERVER_HOST = "server_host"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_DISCOVERY = "discovery_enabled"
    private const val KEY_TLS_ENABLED = "tls_enabled"
    private const val KEY_CONNECTION_MODE = "connection_mode"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_MAX_IMAGE_PAYLOAD_KB = "max_image_payload_kb"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun deviceName(ctx: Context): String =
        prefs(ctx).getString(KEY_DEVICE_NAME, Build.MODEL) ?: Build.MODEL

    fun serverHost(ctx: Context): String =
        prefs(ctx).getString(KEY_SERVER_HOST, "") ?: ""

    fun serverPort(ctx: Context): Int = prefs(ctx).getInt(KEY_SERVER_PORT, 40403)

    fun token(ctx: Context): String = prefs(ctx).getString(KEY_TOKEN, "") ?: ""

    fun autoConnect(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO_CONNECT, true)

    fun discoveryEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DISCOVERY, true)

    fun tlsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TLS_ENABLED, false)

    fun connectionMode(ctx: Context): String =
        prefs(ctx).getString(KEY_CONNECTION_MODE, MODE_DISCOVER) ?: MODE_DISCOVER

    fun maxImagePayloadKb(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MAX_IMAGE_PAYLOAD_KB, 10240)

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

    fun setServerHost(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_SERVER_HOST, value.trim()).apply()

    fun setServerPort(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_SERVER_PORT, value).apply()

    fun setToken(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_TOKEN, value.trim()).apply()

    fun setAutoConnect(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    fun setDiscoveryEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DISCOVERY, value).apply()

    fun setTlsEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_TLS_ENABLED, value).apply()

    fun setConnectionMode(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_CONNECTION_MODE, value).apply()

    fun setMaxImagePayloadKb(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_MAX_IMAGE_PAYLOAD_KB, value.coerceIn(8, 2048)).apply()

    fun setWhitelist(ctx: Context, entries: List<WhitelistEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().put("name", e.name).put("ip", e.ip))
        }
        prefs(ctx).edit().putString(KEY_WHITELIST, arr.toString()).apply()
    }
}

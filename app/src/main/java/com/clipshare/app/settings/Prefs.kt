package com.clipshare.app.settings

import android.content.Context
import android.os.Build

/** SharedPreferences-backed settings. */
object Prefs {
    private const val FILE = "clipshare_prefs"

    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_SERVER_HOST = "server_host"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_DISCOVERY = "discovery_enabled"

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
}

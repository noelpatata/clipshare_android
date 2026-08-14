package com.clipshare.app.logs

import android.content.Context
import android.util.Log

/**
 * Thin wrapper around [android.util.Log] that also stores recent messages in
 * [LogStore] so they can be viewed from inside the app.
 */
object Log {

    private const val MAX_MESSAGE_LENGTH = 2000

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        LogStore.loadFromFile(context)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        store("DEBUG", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        store("INFO", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        store("WARN", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable?) {
        Log.w(tag, msg, tr)
        store("WARN", tag, "$msg\n${tr?.stackTraceToString() ?: ""}".trimEnd())
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        store("ERROR", tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable?) {
        Log.e(tag, msg, tr)
        store("ERROR", tag, "$msg\n${tr?.stackTraceToString() ?: ""}".trimEnd())
    }

    private fun store(level: String, tag: String, msg: String) {
        if (!::context.isInitialized) return
        val safe = if (msg.length > MAX_MESSAGE_LENGTH) msg.take(MAX_MESSAGE_LENGTH) + "…" else msg
        LogStore.append(context, level, tag, safe)
    }
}

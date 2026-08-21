package win.downops.clipshare.sync.clientmode

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.util.Constants
import win.downops.clipshare.util.HostUtil

/**
 * Whitelist connection mode: builds dial targets from the configured whitelist
 * entries, cycles through them on every attempt, schedules the retry after a
 * failed candidate, and verifies daemon hello names against the whitelist.
 */
class WhitelistConnector(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var candidates: List<Pair<String, Int>> = emptyList()
    private var index = 0

    /** Rebuilds candidates from prefs. Returns false when none are usable. */
    fun refresh(): Boolean {
        candidates = Prefs.whitelist(context).mapNotNull { entry ->
            entry.ip.trim()
                .takeIf { ip -> ip.isNotBlank() }
                ?.let { ip -> HostUtil.normalize(ip) to Prefs.serverPort(context) }
        }
        index = 0
        return candidates.isNotEmpty()
    }

    fun isEmpty(): Boolean = candidates.isEmpty()

    /** Returns the next candidate, cycling back to the first after the last. */
    fun next(): Pair<String, Int> {
        val target = candidates[index % candidates.size]
        index++
        return target
    }

    /** Runs [retry] after the retry delay, unless [isRunning] turned false. */
    fun scheduleRetry(isRunning: () -> Boolean, retry: () -> Unit) {
        scope.launch {
            delay(Constants.Whitelist.RETRY_DELAY_MS)
            if (isRunning()) retry()
        }
    }

    /** True when [name] matches the whitelist entry for [host] (blank = any). */
    fun nameMatches(host: String, name: String): Boolean {
        val entry = Prefs.whitelist(context).firstOrNull { HostUtil.normalize(it.ip) == host }
            ?: return false
        return entry.name.isBlank() || entry.name == name
    }
}

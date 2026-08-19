package win.downops.clipshare.ui.history

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import win.downops.clipshare.history.HistoryEntry

/**
 * Renders the "Recent" header and history rows inside a [LazyListScope].
 */
@Composable
fun HistorySectionHeader(
    history: List<HistoryEntry>,
    onClearHistory: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (history.isNotEmpty()) {
            TextButton(onClick = onClearHistory) { Text("Clear") }
        }
    }
}

fun LazyListScope.historyItems(history: List<HistoryEntry>) {
    items(history.take(20)) { entry ->
        HistoryRow(entry)
    }
}

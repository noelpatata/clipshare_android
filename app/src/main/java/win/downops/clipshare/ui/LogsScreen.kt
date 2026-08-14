package win.downops.clipshare.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import win.downops.clipshare.logs.LogStore
import java.io.File

@Composable
fun LogsScreen(context: Context) {
    val entries by LogStore.flow.collectAsState()
    val logText = rememberLogText(entries)
    val scrollState = rememberScrollState()
    var previousSize by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val bottomThreshold = with(density) { 48.dp.toPx() }

    // Auto-scroll to the end on first open and when new log lines are appended while the user
    // is already at the bottom. If the user has scrolled up, leave the scroll position alone.
    LaunchedEffect(entries.size, scrollState.maxValue) {
        if (scrollState.maxValue == 0) return@LaunchedEffect
        val atBottom = scrollState.value >= scrollState.maxValue - bottomThreshold
        val grew = entries.size > previousSize
        if (previousSize == 0) {
            // First layout after open (or after clear): jump to the bottom.
            scrollState.scrollTo(scrollState.maxValue)
        } else if (grew && atBottom) {
            // New logs arrived while the user was already at the bottom.
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        previousSize = entries.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Recent logs",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Last ${entries.size} log lines are kept. Long-press to select and copy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { LogStore.clear(context) },
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
            Button(
                onClick = { shareLogs(context) },
                modifier = Modifier.weight(1f),
            ) { Text("Share") }
        }
    }
}

@Composable
private fun rememberLogText(entries: List<LogStore.Entry>): AnnotatedString {
    return buildAnnotatedString {
        entries.forEachIndexed { index, entry ->
            if (index > 0) append("\n")
            withStyle(
                SpanStyle(
                    color = when (entry.level) {
                        "ERROR" -> MaterialTheme.colorScheme.error
                        "WARN" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                append(entry.format())
            }
        }
    }
}

private fun shareLogs(context: Context) {
    val text = LogStore.shareText()
    val dir = File(context.cacheDir, "clipshare_share").apply { mkdirs() }
    val file = File(dir, "clipshare_logs.txt")
    file.writeText(text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "ClipShare logs")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share logs"))
}

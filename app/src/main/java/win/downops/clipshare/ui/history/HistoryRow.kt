package win.downops.clipshare.ui.history

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryEntryProcessorFactory
import win.downops.clipshare.history.image.DiskImageHistoryProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single history row. Tapping it copies the payload back to the clipboard
 * using the processor returned by [HistoryEntryProcessorFactory].
 */
@Composable
fun HistoryRow(entry: HistoryEntry) {
    val context = LocalContext.current
    val processor = remember(entry.clip) { HistoryEntryProcessorFactory.get(context, entry) }
    val ts = remember(entry.ts) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.ts))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { processor.copyToClipboard(context, entry) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.incoming)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            HistoryRowHeader(entry, ts)
            Spacer(Modifier.height(4.dp))
            HistoryRowContent(entry)
        }
    }
}

@Composable
private fun HistoryRowHeader(entry: HistoryEntry, ts: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (entry.clip) {
                is ClipItem.Text ->
                    if (entry.incoming) "RECEIVED from ${entry.from}" else "SENT"
                is ClipItem.Image ->
                    if (entry.incoming) "IMAGE RECEIVED from ${entry.from}" else "IMAGE SENT"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = ts,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRowContent(entry: HistoryEntry) {
    when (val clip = entry.clip) {
        is ClipItem.Text -> {
            Text(
                text = clip.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        is ClipItem.Image -> {
            HistoryImagePreview(entry)
        }
    }
}

@Composable
private fun HistoryImagePreview(entry: HistoryEntry) {
    val context = LocalContext.current
    val provider = remember { DiskImageHistoryProvider(context) }
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.clip, entry.ts) {
        value = withContext(Dispatchers.IO) {
            val bytes = provider.loadPreview(entry) ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Image preview",
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            text = entry.displayText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

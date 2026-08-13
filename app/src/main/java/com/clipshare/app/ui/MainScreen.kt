package com.clipshare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clipshare.app.history.HistoryEntry
import com.clipshare.app.state.AppState
import com.clipshare.app.state.DiscoveredDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    onPushText: (String) -> Unit,
    onConnectTo: (DiscoveredDevice) -> Unit,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val running by AppState.running.collectAsState()
    val connected by AppState.connected.collectAsState()
    val status by AppState.status.collectAsState()
    val serverName by AppState.serverName.collectAsState()
    val devices by AppState.discovered.collectAsState()
    val history by AppState.history.collectAsState()
    val error by AppState.lastError.collectAsState()

    var text by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(running, connected, status, serverName, onToggle)
        }
        item {
            if (error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        item {
            Text("Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (devices.isEmpty()) {
                Text(
                    "No devices found. Make sure the desktop daemon is running and you are on the same network, or add the address in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                devices.forEach { device ->
                    DeviceRow(device, onConnectTo)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Send",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onOpenSettings) { Text("Settings") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text to push") },
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onPushText(text)
                    text = ""
                },
                enabled = text.isNotBlank() && connected,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send to desktop") }
        }
        item {
            Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text(
                    "Nothing yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(history.take(20)) { entry ->
            HistoryRow(entry)
        }
    }
}

@Composable
private fun StatusCard(
    running: Boolean,
    connected: Boolean,
    status: String,
    serverName: String?,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(
                            if (connected) Color(0xFF2E7D32) else if (running) Color(0xFFF9A825) else Color(0xFF9E9E9E),
                            CircleShape
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(status, style = MaterialTheme.typography.titleMedium)
                    if (serverName != null) {
                        Text(
                            "Syncing with $serverName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            if (running) "Watching clipboard while app is open" else "Not syncing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = running, onCheckedChange = { onToggle() })
            }
            if (running && !connected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onConnect: (DiscoveredDevice) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect(device) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${device.host}:${device.port}  (${device.source})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Connect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val ts = remember(entry.ts) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.ts))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.incoming)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (entry.incoming) "RECEIVED from ${entry.from}" else "SENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    ts,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

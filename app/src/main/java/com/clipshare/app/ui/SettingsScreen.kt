package com.clipshare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.material3.MaterialTheme
import com.clipshare.app.settings.Prefs

@Composable
fun SettingsScreen(context: Context, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(Prefs.deviceName(context)) }
    var host by rememberSaveable { mutableStateOf(Prefs.serverHost(context)) }
    var port by rememberSaveable { mutableStateOf(Prefs.serverPort(context).toString()) }
    var token by rememberSaveable { mutableStateOf(Prefs.token(context)) }
    var autoConnect by rememberSaveable { mutableStateOf(Prefs.autoConnect(context)) }
    var discovery by rememberSaveable { mutableStateOf(Prefs.discoveryEnabled(context)) }

    fun save() {
        Prefs.setDeviceName(context, name)
        Prefs.setServerHost(context, host)
        Prefs.setServerPort(context, port.toIntOrNull() ?: 40403)
        Prefs.setToken(context, token)
        Prefs.setAutoConnect(context, autoConnect)
        Prefs.setDiscoveryEnabled(context, discovery)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Server (desktop IP or hostname)") },
            placeholder = { Text("e.g. 192.168.1.10") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit) },
            label = { Text("Server port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Shared token (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Auto-connect", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Reconnect automatically when the service starts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Discovery", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Browse for desktops via mDNS and UDP beacons",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = discovery, onCheckedChange = { discovery = it })
        }

        androidx.compose.material3.Button(
            onClick = { save(); onBack() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

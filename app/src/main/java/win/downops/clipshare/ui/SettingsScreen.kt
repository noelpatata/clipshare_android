package win.downops.clipshare.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import win.downops.clipshare.certs.CertStore
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.settings.WhitelistEntry
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants

@Composable
fun SettingsScreen(context: Context, onBack: () -> Unit, onOpenLogs: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(Prefs.deviceName(context)) }
    var host by rememberSaveable { mutableStateOf(Prefs.serverHost(context)) }
    var port by rememberSaveable { mutableStateOf(Prefs.serverPort(context).toString()) }
    var token by rememberSaveable { mutableStateOf(Prefs.token(context)) }
    var autoConnect by rememberSaveable { mutableStateOf(Prefs.autoConnect(context)) }
    var discovery by rememberSaveable { mutableStateOf(Prefs.discoveryEnabled(context)) }
    var beaconPort by rememberSaveable { mutableStateOf(Prefs.discoveryBeaconPort(context).toString()) }
    var tlsEnabled by rememberSaveable { mutableStateOf(Prefs.tlsEnabled(context)) }
    var mode by rememberSaveable { mutableStateOf(Prefs.connectionMode(context)) }
    val whitelist = remember {
        mutableStateListOf<WhitelistEntry>().apply { addAll(Prefs.whitelist(context)) }
    }
    var certStatus by remember { mutableStateOf(certStatusText(context)) }
    var certLoaded by remember { mutableStateOf(CertStore.hasCert(context)) }
    val accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }

    val certPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val ok = CertStore.importP12(context, uri)
            certLoaded = CertStore.hasCert(context)
            certStatus = certStatusText(context)
            Toast.makeText(
                context,
                if (ok) "Certificate imported" else "Import failed: not a valid ClipShare .p12",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun save() {
        Prefs.setDeviceName(context, name)
        Prefs.setServerHost(context, host)
        Prefs.setServerPort(context, port.toIntOrNull() ?: Constants.Discovery.DEFAULT_SERVER_PORT)
        Prefs.setToken(context, token)
        Prefs.setAutoConnect(context, autoConnect)
        Prefs.setDiscoveryEnabled(context, discovery)
        Prefs.setDiscoveryBeaconPort(context, beaconPort.toIntOrNull() ?: Constants.Discovery.DEFAULT_BEACON_PORT)
        Prefs.setTlsEnabled(context, tlsEnabled)
        Prefs.setConnectionMode(context, mode)
        Prefs.setWhitelist(context, whitelist.toList())
        if (AppState.running.value) {
            AppState.stopSync(context)
            AppState.startSync(context)
        }
        onBack()
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

        OutlinedTextField(
            value = beaconPort,
            onValueChange = { beaconPort = it.filter(Char::isDigit) },
            label = { Text("Beacon port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Connection mode", style = MaterialTheme.typography.titleSmall)
        Text(
            "Mirror this on the desktop daemon (connection.mode).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == Prefs.MODE_DISCOVER,
                onClick = { mode = Prefs.MODE_DISCOVER },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Discover") }
            SegmentedButton(
                selected = mode == Prefs.MODE_WHITELIST,
                onClick = { mode = Prefs.MODE_WHITELIST },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Whitelist") }
        }

        if (mode == Prefs.MODE_WHITELIST) {
            Text("Whitelist", style = MaterialTheme.typography.titleSmall)
            Text(
                "Only these devices are allowed. Matches on name or IP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            whitelist.forEachIndexed { i, entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = entry.name,
                        onValueChange = { whitelist[i] = entry.copy(name = it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = entry.ip,
                        onValueChange = { whitelist[i] = entry.copy(ip = it) },
                        label = { Text("IP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.2f),
                    )
                    IconButton(onClick = { whitelist.removeAt(i) }) { Text("\u2715") }
                }
            }
            OutlinedButton(
                onClick = { whitelist.add(WhitelistEntry("", "")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add entry") }
        }

        Text("Security", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("TLS (wss)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Requires a client certificate (below)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = tlsEnabled, onCheckedChange = { tlsEnabled = it })
        }

        Text("Client certificate", style = MaterialTheme.typography.titleSmall)
        Text(
            if (certLoaded) "Imported: $certStatus"
            else "Not imported. Generate on the desktop with: clipshare cert export --name <you> --type client",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { certPicker.launch("*/*") },
                modifier = Modifier.weight(1f),
            ) { Text(if (certLoaded) "Replace .p12" else "Import .p12") }
            if (certLoaded) {
                OutlinedButton(
                    onClick = {
                        CertStore.clear(context)
                        certLoaded = false
                        certStatus = ""
                        Toast.makeText(context, "Certificate removed", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Remove") }
            }
        }
        Text(
            "The .p12 password is ${Constants.Pkcs12.PASSWORD}. Export the server client certificate, then pick the file here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Background capture", style = MaterialTheme.typography.titleSmall)
        Text(
            if (accessibilityOn) "Enabled - copies from any app sync to the desktop"
            else "Disabled. Android 10+ hides the clipboard from background apps; an accessibility service lets ClipShare capture copies from other apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (accessibilityOn) "Accessibility settings" else "Enable background capture") }

        Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
        Text(
            "View recent app logs to troubleshoot connection or clipboard issues.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onOpenLogs,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("View logs") }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

private fun certStatusText(context: Context): String =
    CertStore.caSubject(context) ?: ""

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${context.packageName}.accessibility.ClipShareAccessibilityService"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

package win.downops.clipshare.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.CaptureActivity
import win.downops.clipshare.certs.CertStore
import win.downops.clipshare.certs.ClientCertInfo
import win.downops.clipshare.certs.QrCodes
import win.downops.clipshare.certs.ServerCertManager
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.logs.LogStore
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.settings.WhitelistEntry
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants

@Composable
fun SettingsScreen(context: Context, onBack: () -> Unit, registerSave: (() -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(Prefs.deviceName(context)) }
    var appMode by rememberSaveable { mutableStateOf(Prefs.appMode(context)) }

    // Connection settings
    var host by rememberSaveable { mutableStateOf(Prefs.serverHost(context)) }
    var port by rememberSaveable { mutableStateOf(Prefs.serverPort(context).toString()) }
    var token by rememberSaveable { mutableStateOf(Prefs.token(context)) }
    var autoConnect by rememberSaveable { mutableStateOf(Prefs.autoConnect(context)) }
    var discovery by rememberSaveable { mutableStateOf(Prefs.discoveryEnabled(context)) }
    var beaconPort by rememberSaveable { mutableStateOf(Prefs.discoveryBeaconPort(context).toString()) }
    var mode by rememberSaveable { mutableStateOf(Prefs.connectionMode(context)) }
    val whitelist = remember {
        mutableStateListOf<WhitelistEntry>().apply { addAll(Prefs.whitelist(context)) }
    }

    // Clipboard / history / logging
    var pollMs by rememberSaveable { mutableStateOf(Prefs.clipboardPollMs(context).toString()) }
    var maxHistoryEntries by rememberSaveable { mutableStateOf(Prefs.maxHistoryEntries(context).toString()) }
    var maxLogKb by rememberSaveable { mutableStateOf(Prefs.maxLogFileKb(context).toString()) }

    // TLS: client
    var tlsEnabled by rememberSaveable { mutableStateOf(Prefs.tlsEnabled(context)) }
    var verifyHostname by rememberSaveable { mutableStateOf(Prefs.verifyHostname(context)) }
    val clientCerts = remember { mutableStateListOf<ClientCertInfo>().apply { addAll(CertStore.clientCerts(context)) } }

    // TLS: server
    var serverPort by rememberSaveable { mutableStateOf(Prefs.serverPort(context).toString()) }
    var serverTls by rememberSaveable { mutableStateOf(Prefs.serverTlsEnabled(context)) }
    var serverCertStatus by rememberSaveable { mutableStateOf(serverCertStatusText(context)) }
    var showCaQr by rememberSaveable { mutableStateOf(false) }

    val clientCertPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val ok = CertStore.importClientP12(context, uri)
            if (ok) {
                clientCerts.clear()
                clientCerts.addAll(CertStore.clientCerts(context))
            }
            Toast.makeText(
                context,
                if (ok) "Certificate imported" else "Import failed",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val qrScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val content = result.data?.getStringExtra("SCAN_RESULT")
            if (content != null) {
                val ok = CertStore.importFromQrContent(context, content)
                if (ok) {
                    clientCerts.clear()
                    clientCerts.addAll(CertStore.clientCerts(context))
                }
                Toast.makeText(
                    context,
                    if (ok) "Certificate imported from QR" else "QR code not recognized",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun save() {
        Prefs.setDeviceName(context, name)
        Prefs.setAppMode(context, appMode)
        Prefs.setServerHost(context, host)
        Prefs.setServerPort(context, port.toIntOrNull() ?: Constants.Discovery.DEFAULT_SERVER_PORT)
        Prefs.setServerTlsEnabled(context, serverTls)
        Prefs.setToken(context, token)
        Prefs.setAutoConnect(context, autoConnect)
        Prefs.setDiscoveryEnabled(context, discovery)
        Prefs.setDiscoveryBeaconPort(context, beaconPort.toIntOrNull() ?: Constants.Discovery.DEFAULT_BEACON_PORT)
        Prefs.setTlsEnabled(context, tlsEnabled)
        Prefs.setVerifyHostname(context, verifyHostname)
        Prefs.setConnectionMode(context, mode)
        Prefs.setWhitelist(context, whitelist.toList())
        Prefs.setClipboardPollMs(context, pollMs.toLongOrNull() ?: Constants.Clipboard.SYNC_POLL_MS)
        Prefs.setMaxHistoryEntries(context, maxHistoryEntries.toIntOrNull() ?: Constants.History.DEFAULT_MAX_ENTRIES)
        Prefs.setMaxLogFileKb(context, maxLogKb.toIntOrNull() ?: Constants.Log.DEFAULT_MAX_FILE_KB)
        LogStore.setMaxLogFileKb(maxLogKb.toIntOrNull() ?: Constants.Log.DEFAULT_MAX_FILE_KB)
        AppState.setAppMode(appMode)
        if (AppState.running.value) {
            AppState.stopSync(context)
            AppState.startSync(context)
        }
        onBack()
    }

    LaunchedEffect(Unit) {
        registerSave { save() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // General
        SettingsSectionTitle("General")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsSectionTitle("Clipboard")
        OutlinedTextField(
            value = pollMs,
            onValueChange = { pollMs = it.filter(Char::isDigit) },
            label = { Text("Clipboard poll interval (ms)") },
            supportingText = { Text("How often the clipboard is re-checked. Lower = more responsive, higher = less battery. Clamped to 200-10000.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // History
        SettingsSectionTitle("History")
        OutlinedTextField(
            value = maxHistoryEntries,
            onValueChange = { maxHistoryEntries = it.filter(Char::isDigit) },
            label = { Text("Max history entries") },
            supportingText = { Text("Older items are dropped to stay within this limit.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Logging
        SettingsSectionTitle("Logging")
        OutlinedTextField(
            value = maxLogKb,
            onValueChange = { maxLogKb = it.filter(Char::isDigit) },
            label = { Text("Max log file size (KB)") },
            supportingText = { Text("The on-device log is trimmed to stay within this limit.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("App mode", style = MaterialTheme.typography.titleSmall)
        Text(
            "Choose whether this device connects to other servers or acts as one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = appMode == Prefs.APP_MODE_CLIENT,
                onClick = { appMode = Prefs.APP_MODE_CLIENT },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Client") }
            SegmentedButton(
                selected = appMode == Prefs.APP_MODE_SERVER,
                onClick = { appMode = Prefs.APP_MODE_SERVER },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Server") }
        }

        // Connection (client mode)
        if (appMode == Prefs.APP_MODE_CLIENT) {
            SettingsSectionTitle("Connection")
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server (IP or hostname)") },
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

            SwitchRow(
                title = "Auto-connect",
                subtitle = "Reconnect automatically when the service starts",
                checked = autoConnect,
                onCheckedChange = { autoConnect = it },
            )
            SwitchRow(
                title = "Discovery",
                subtitle = "Browse for servers via mDNS and UDP beacons",
                checked = discovery,
                onCheckedChange = { discovery = it },
            )
            OutlinedTextField(
                value = beaconPort,
                onValueChange = { beaconPort = it.filter(Char::isDigit) },
                label = { Text("Beacon port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Connection mode", style = MaterialTheme.typography.titleSmall)
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
        }

        // TLS
        SettingsSectionTitle("TLS")

        Text("Client", style = MaterialTheme.typography.titleSmall)
        Text(
            "How this device connects to desktop daemons or Android servers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            title = "TLS (wss)",
            subtitle = "Requires a client certificate or trusted CA",
            checked = tlsEnabled,
            onCheckedChange = { tlsEnabled = it },
        )
        SwitchRow(
            title = "Verify hostname",
            subtitle = "Require the server certificate to match its address. Turn off to keep working after network changes",
            checked = verifyHostname,
            onCheckedChange = { verifyHostname = it },
        )

        Text("Client certificates", style = MaterialTheme.typography.titleSmall)
        Text(
            "Used for mutual TLS with desktop servers. The first certificate is used automatically; its CA is trusted automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        clientCerts.forEach { cert ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(cert.caSubject, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = {
                    CertStore.deleteClientCert(context, cert.id)
                    clientCerts.clear()
                    clientCerts.addAll(CertStore.clientCerts(context))
                }) { Text("\u2715") }
            }
        }
        OutlinedButton(
            onClick = { clientCertPicker.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import .p12") }
        OutlinedButton(
            onClick = { qrScanner.launch(Intent(context, CaptureActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Scan QR certificate") }

        if (appMode == Prefs.APP_MODE_SERVER) {
            Text("Server", style = MaterialTheme.typography.titleSmall)
            Text(
                "How this device presents itself to other ClipShare devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it.filter(Char::isDigit) },
                label = { Text("Server port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                title = "TLS (wss)",
                subtitle = "Generate a local CA + server certificate",
                checked = serverTls,
                onCheckedChange = { serverTls = it },
            )
            Text(
                serverCertStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        ServerCertManager.generate(context, Prefs.deviceName(context))
                        serverCertStatus = serverCertStatusText(context)
                        Toast.makeText(context, "Server certificate regenerated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Regenerate cert") }
                OutlinedButton(
                    onClick = {
                        val pem = ServerCertManager.getCaCertificatePem(context)
                        if (pem != null) {
                            copyToClipboard(context, pem)
                            Toast.makeText(context, "CA certificate copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Copy CA") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val uri = ServerCertManager.getCaCertificateShareUri(context)
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/x-x509-ca-cert"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share CA certificate"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Share CA file") }
                OutlinedButton(
                    onClick = { showCaQr = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Show QR") }
            }
        }
    }

    if (showCaQr) {
        ServerCaQrDialog(
            context = context,
            onDismiss = { showCaQr = false },
        )
    }
}

@Composable
private fun ServerCaQrDialog(context: Context, onDismiss: () -> Unit) {
    val content = remember { QrCodes.serverCaContent(context) }
    val bitmap = remember(content) { content?.let { QrCodes.encode(it, 480) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server CA QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Scan this QR with another ClipShare device in client mode to trust this server's CA.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Server CA QR code",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("No server certificate generated yet.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(title))
    }
}

private fun serverCertStatusText(context: Context): String {
    return if (ServerCertManager.hasCerts(context)) {
        val pem = ServerCertManager.getCaCertificatePem(context)
        if (pem != null) "Server certificate ready" else "Certificate state unknown"
    } else {
        "No server certificate generated yet"
    }
}

private fun copyToClipboard(context: Context, text: String) {
    ClipboardWriter.writeText(context, "ClipShare CA", text)
}
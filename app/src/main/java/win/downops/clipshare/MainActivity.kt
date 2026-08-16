package win.downops.clipshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import win.downops.clipshare.clipboard.ClipboardPusher
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.AccessibilityScreen
import win.downops.clipshare.ui.LogsScreen
import win.downops.clipshare.ui.MainScreen
import win.downops.clipshare.ui.NavCaptureIcon
import win.downops.clipshare.ui.NavHomeIcon
import win.downops.clipshare.ui.NavLogsIcon
import win.downops.clipshare.ui.NavSaveIcon
import win.downops.clipshare.ui.NavSettingsIcon
import win.downops.clipshare.ui.SettingsScreen
import win.downops.clipshare.ui.theme.ClipShareTheme

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.init(this)
        Log.i("MainActivity", "onCreate")
        AppState.loadHistory(this)
        AppState.setAppMode(Prefs.appMode(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ClipShareTheme {
                ClipShareApp()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ClipShareApp() {
        val context = LocalContext.current
        var screen by remember { mutableStateOf("main") }
        var saveSettings by remember { mutableStateOf<(() -> Unit)?>(null) }

        BackHandler(enabled = screen != "main") {
            screen = "main"
        }

        val title = when (screen) {
            "settings" -> "Settings"
            "logs" -> "Logs"
            "accessibility" -> "Background capture"
            else -> "ClipShare"
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (screen != "main") {
                            IconButton(onClick = { screen = "main" }) {
                                Text("\u2190", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                    actions = {
                        if (screen == "settings") {
                            Button(
                                onClick = { saveSettings?.invoke() },
                                enabled = saveSettings != null,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                NavSaveIcon()
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == "main",
                        onClick = { screen = "main" },
                        icon = { NavHomeIcon() },
                        label = { Text("Main") },
                    )
                    NavigationBarItem(
                        selected = screen == "accessibility",
                        onClick = { screen = "accessibility" },
                        icon = { NavCaptureIcon() },
                        label = { Text("Capture") },
                    )
                    NavigationBarItem(
                        selected = screen == "logs",
                        onClick = { screen = "logs" },
                        icon = { NavLogsIcon() },
                        label = { Text("Logs") },
                    )
                    NavigationBarItem(
                        selected = screen == "settings",
                        onClick = { screen = "settings" },
                        icon = { NavSettingsIcon() },
                        label = { Text("Settings") },
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    "settings" -> SettingsScreen(
                        context = context,
                        onBack = { screen = "main" },
                        registerSave = { saveSettings = it },
                    )
                    "logs" -> LogsScreen(context)
                    "accessibility" -> AccessibilityScreen(context)
                    else -> MainScreen(
                        onPushText = { text -> AppState.service?.send(text) },
                        onConnectTo = { device ->
                            val svc = AppState.service
                            if (svc != null) svc.switchTo(device.host, device.port, device.tls) else {
                                Prefs.setServerHost(context, device.host)
                                Prefs.setServerPort(context, device.port)
                                AppState.startSync(context)
                            }
                        },
                        onToggle = {
                            if (AppState.running.value) AppState.stopSync(context) else AppState.startSync(context)
                        },
                        onClearHistory = { AppState.clearHistory(context) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.appInForeground = true
        ClipboardPusher.start(this)
    }

    override fun onPause() {
        super.onPause()
        ClipboardPusher.stop(this)
        AppState.appInForeground = false
    }
}

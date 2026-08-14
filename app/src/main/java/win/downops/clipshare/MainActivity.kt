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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.core.content.ContextCompat
import win.downops.clipshare.clipboard.ClipboardSync
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.LogsScreen
import win.downops.clipshare.ui.MainScreen
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

        BackHandler(enabled = screen != "main") {
            screen = when (screen) {
                "logs" -> "settings"
                else -> "main"
            }
        }

        val title = when (screen) {
            "settings" -> "Settings"
            "logs" -> "Logs"
            else -> ""
        }

        Scaffold(
            topBar = {
                if (screen != "main") {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = {
                                screen = when (screen) {
                                    "logs" -> "settings"
                                    else -> "main"
                                }
                            }) {
                                Text("\u2190", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            }
                        },
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    "settings" -> SettingsScreen(
                        context = context,
                        onBack = { screen = "main" },
                        onOpenLogs = { screen = "logs" },
                    )
                    "logs" -> LogsScreen(context)
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
                        onOpenSettings = { screen = "settings" },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ClipboardSync.start(this)
    }

    override fun onPause() {
        super.onPause()
        ClipboardSync.stop(this)
    }
}

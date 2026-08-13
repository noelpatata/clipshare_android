package com.clipshare.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.clipshare.app.clipboard.ClipboardSync
import com.clipshare.app.settings.Prefs
import com.clipshare.app.state.AppState
import com.clipshare.app.ui.MainScreen
import com.clipshare.app.ui.SettingsScreen
import com.clipshare.app.ui.theme.ClipShareTheme

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        Scaffold(
            topBar = {
                if (screen == "settings") {
                    TopAppBar(
                        title = { Text("Settings") },
                        navigationIcon = {
                            IconButton(onClick = { screen = "main" }) {
                                Text("\u2190", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            }
                        },
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                if (screen == "settings") {
                    SettingsScreen(context) { screen = "main" }
                } else {
                    MainScreen(
                        onPushText = { text -> AppState.service?.send(text) },
                        onConnectTo = { device ->
                            val svc = AppState.service
                            if (svc != null) svc.switchTo(device.host, device.port) else {
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

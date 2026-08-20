package win.downops.clipshare.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import win.downops.clipshare.ui.NavCaptureIcon
import win.downops.clipshare.ui.NavHomeIcon
import win.downops.clipshare.ui.NavLogsIcon
import win.downops.clipshare.ui.NavSettingsIcon

/**
 * Bottom navigation bar that mirrors the swipeable pager pages.
 */
@Composable
fun AppBottomBar(
    selected: AppScreen,
    screens: List<AppScreen>,
    onSelect: (AppScreen) -> Unit,
) {
    NavigationBar {
        screens.forEach { screen ->
            NavigationBarItem(
                selected = screen == selected,
                onClick = { onSelect(screen) },
                icon = { AppNavIcon(screen) },
                label = { Text(stringResource(screen.labelRes)) },
            )
        }
    }
}

@Composable
private fun AppNavIcon(screen: AppScreen) {
    when (screen) {
        AppScreen.Main -> NavHomeIcon()
        AppScreen.Capture -> NavCaptureIcon()
        AppScreen.Logs -> NavLogsIcon()
        AppScreen.Settings -> NavSettingsIcon()
    }
}

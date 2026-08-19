package win.downops.clipshare.ui.navigation

import androidx.annotation.StringRes
import win.downops.clipshare.R
import win.downops.clipshare.util.Constants

/**
 * The four top-level destinations shown in the bottom navigation bar and
 * navigable by horizontal swipe.
 */
enum class AppScreen(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val titleRes: Int,
) {
    Main(Constants.Navigation.MAIN, R.string.nav_main, R.string.title_main),
    Capture(Constants.Navigation.CAPTURE, R.string.nav_capture, R.string.title_capture),
    Logs(Constants.Navigation.LOGS, R.string.nav_logs, R.string.title_logs),
    Settings(Constants.Navigation.SETTINGS, R.string.nav_settings, R.string.title_settings);

    companion object {
        private val byRoute = entries.associateBy { it.route }

        fun fromRoute(route: String): AppScreen = byRoute[route] ?: Main

        fun ordered(): List<AppScreen> = listOf(Main, Capture, Logs, Settings)
    }
}

package win.downops.clipshare.ui.main.servermode

import org.junit.Before
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.main.MainScreenBaseTest

/** Shared setup for server-mode main-screen tests. */
abstract class ServerModeBaseTest : MainScreenBaseTest() {

    @Before
    open fun setServerMode() {
        AppState.setAppMode(Prefs.APP_MODE_SERVER)
        mainScreen.setContent()
    }
}

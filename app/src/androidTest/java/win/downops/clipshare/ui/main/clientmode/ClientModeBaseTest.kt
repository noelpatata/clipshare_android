package win.downops.clipshare.ui.main.clientmode

import org.junit.Before
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.main.MainScreenBaseTest

/** Shared setup for client-mode main-screen tests. */
abstract class ClientModeBaseTest : MainScreenBaseTest() {

    @Before
    open fun setClientMode() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        mainScreen.setContent()
    }
}

package win.downops.clipshare.ui.history

import org.junit.Before
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.main.MainScreenBaseTest

/** Shared setup for history-rendering tests. */
abstract class HistoryBaseTest : MainScreenBaseTest() {

    @Before
    open fun setClientMode() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        mainScreen.setContent()
    }
}

package win.downops.clipshare.ui.main.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.main.MainScreenBaseTest

@RunWith(AndroidJUnit4::class)
class MainScreenCommonTest : MainScreenBaseTest() {

    @Before
    fun setClientMode() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        mainScreen.setContent()
    }

    @Test
    fun toggleSwitchInvokesCallback() {
        mainScreen.assertTextDisplayed("Stopped")
        mainScreen.clickToggle()

        composeRule.runOnIdle {
            assertEquals(1, mainScreen.toggleCount)
        }
    }
}

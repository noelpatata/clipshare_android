package win.downops.clipshare.ui.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.state.AppState

@RunWith(AndroidJUnit4::class)
class HistoryTextTest : HistoryBaseTest() {

    @Test
    fun textEntriesAreRendered() {
        AppState.onReceived(context, "received from laptop", "laptop")
        AppState.onSent(context, "sent by me")

        mainScreen.assertTextDisplayed("received from laptop")
        mainScreen.assertTextDisplayed("sent by me")
        mainScreen.assertTextDisplayed("RECEIVED from laptop")
    }

    @Test
    fun tappingHistoryTextDoesNotDuplicateIt() {
        AppState.onReceived(context, "single entry", "laptop")

        mainScreen.click("single entry")
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, AppState.history.value.size)
        }
    }
}

package win.downops.clipshare.ui.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ui.common.TestFixtures
import win.downops.clipshare.util.Constants

@RunWith(AndroidJUnit4::class)
class HistoryImageTest : HistoryBaseTest() {

    @Test
    fun imageEntryIsRenderedWithPreview() {
        AppState.onReceivedImage(context, TestFixtures.pngBytes(), Constants.Mime.IMAGE_PNG, "laptop")

        mainScreen.assertTextDisplayed("IMAGE RECEIVED from laptop")
        mainScreen.assertTextDisplayed("image/png", substring = true)
    }

    @Test
    fun tappingImageHistoryDoesNotDuplicateIt() {
        AppState.onReceivedImage(context, TestFixtures.pngBytes(), Constants.Mime.IMAGE_PNG, "laptop")

        mainScreen.click("IMAGE RECEIVED from laptop")
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, AppState.history.value.filter { it.clip is ClipItem.Image }.size)
        }
    }

    @Test
    fun receivedImageDoesNotCreateSentDuplicate() {
        AppState.onReceivedImage(context, TestFixtures.pngBytes(), Constants.Mime.IMAGE_PNG, "laptop")

        composeRule.runOnIdle {
            val sent = AppState.history.value.filter { !it.incoming }
            assertTrue("received image should not create a sent entry", sent.isEmpty())
        }
    }
}

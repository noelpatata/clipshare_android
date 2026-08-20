package win.downops.clipshare.ui.history

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.state.AppState

@RunWith(AndroidJUnit4::class)
class HistoryCopyTest : HistoryBaseTest() {

    @Test
    fun tappingHistoryTextCopiesItBackToClipboard() {
        AppState.onReceived(context, "copy me back", "laptop")

        mainScreen.click("copy me back")
        composeRule.waitForIdle()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("copy me back", clipboard.primaryClip?.getItemAt(0)?.text)
    }
}

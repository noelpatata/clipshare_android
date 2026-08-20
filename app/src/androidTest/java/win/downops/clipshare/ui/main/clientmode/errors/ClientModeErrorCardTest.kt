package win.downops.clipshare.ui.main.clientmode.errors

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.main.clientmode.ClientModeBaseTest

@RunWith(AndroidJUnit4::class)
class ClientModeErrorCardTest : ClientModeBaseTest() {

    @Test
    fun errorCardIsShownWhenLastErrorSet() {
        mainScreen.showError("TLS handshake failed")

        composeRule.onAllNodesWithText("Error: TLS handshake failed", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }
}

package win.downops.clipshare.ui.main.clientmode.tls

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.state.DiscoveredDevice
import win.downops.clipshare.ui.main.clientmode.ClientModeBaseTest

@RunWith(AndroidJUnit4::class)
class ClientModeTlsBadgeTest : ClientModeBaseTest() {

    @Test
    fun tlsBadgeIsShownForSecureDevices() {
        mainScreen.setDiscoveredDevices(
            listOf(
                DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = true),
                DiscoveredDevice("desktop", "192.168.1.11", 40403, "beacon", tls = false),
            ),
        )

        composeRule.onNodeWithText("192.168.1.10:40403", substring = true)
            .assertTextContains("TLS", substring = true)
        composeRule.onNodeWithText("192.168.1.11:40403  (beacon · TLS)").assertDoesNotExist()
    }
}

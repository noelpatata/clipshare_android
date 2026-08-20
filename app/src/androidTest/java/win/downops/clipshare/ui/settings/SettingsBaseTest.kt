package win.downops.clipshare.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import win.downops.clipshare.ui.common.TestFixtures
import win.downops.clipshare.ui.robots.SettingsRobot

/** Shared setup for tests that render [win.downops.clipshare.ui.SettingsScreen]. */
abstract class SettingsBaseTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected lateinit var context: Context
    protected lateinit var settings: SettingsRobot

    @Before
    open fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        TestFixtures.clearAppStateAndPrefs(context)
        settings = SettingsRobot(composeRule)
        settings.setContent()
    }
}

package win.downops.clipshare.ui.main

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import win.downops.clipshare.ui.common.TestFixtures
import win.downops.clipshare.ui.robots.MainScreenRobot

/** Shared setup for tests that render [win.downops.clipshare.ui.MainScreen]. */
abstract class MainScreenBaseTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected lateinit var context: Context
    protected lateinit var mainScreen: MainScreenRobot

    @Before
    open fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        TestFixtures.clearAppStateAndPrefs(context)
        mainScreen = MainScreenRobot(composeRule)
    }
}

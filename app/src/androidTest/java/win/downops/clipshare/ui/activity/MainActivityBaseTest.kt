package win.downops.clipshare.ui.activity

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import win.downops.clipshare.MainActivity
import win.downops.clipshare.ui.common.TestFixtures
import win.downops.clipshare.ui.robots.MainActivityRobot

/** Shared setup for tests that launch the real [MainActivity]. */
@RunWith(AndroidJUnit4::class)
abstract class MainActivityBaseTest {

    protected val composeRule = createAndroidComposeRule<MainActivity>()
    protected val permissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    protected lateinit var context: Context
    protected lateinit var activity: MainActivityRobot

    @Before
    open fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        TestFixtures.clearAppStateAndPrefs(context)
        activity = MainActivityRobot(composeRule)
    }
}

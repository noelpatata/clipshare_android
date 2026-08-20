package win.downops.clipshare.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import win.downops.clipshare.MainActivity
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState

/**
 * Tests the full MainActivity scaffold, including the Save button in the top
 * app bar which is owned by MainActivity rather than by SettingsScreen.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val permissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("clipshare_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppState.resetForTesting()
    }

    @Test
    fun saveButtonIsOnlyVisibleOnSettingsScreen() {
        composeRule.onNodeWithText("ClipShare").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun saveButtonBecomesEnabledOnSettingsScreen() {
        // Navigate to settings via the bottom bar.
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun saveButtonSavesSettingsAndReturnsToMain() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // Should be back on the main screen.
        composeRule.onNodeWithText("ClipShare").assertIsDisplayed()
        // App mode defaults to client and is persisted on save.
        assertEquals(Prefs.APP_MODE_CLIENT, Prefs.appMode(context))
    }
}

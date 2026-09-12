package za.org.rtc.community

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs only against the debug build. It launches the existing synthetic resident adapter through
 * an explicit activity extra, so no account, credential, notification permission, or backend row
 * is created in order to verify the resident Account menu route.
 */
@RunWith(AndroidJUnit4::class)
class DebugResidentMenuAutomationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun debugResidentIntentOpensHomeThenAccountAndExposesSignOut() {
        assertTrue("This test must only execute against a debug build.", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEBUG_SESSION_ROLE, "RESIDENT_A")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Welcome back, Resident A").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Open account or work queue").performClick()
            composeRule.onNodeWithText("Account").assertIsDisplayed()
            composeRule.onNodeWithText("Sign out").assertIsDisplayed()
        }
    }
}

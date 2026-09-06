package za.org.rtc.community

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun packagedSplashUsesSuppliedTransparentRtcLogo() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val drawableId = targetContext.resources.getIdentifier(
            "rtc_community_logo_transparent",
            "drawable",
            targetContext.packageName,
        )

        assertTrue("The supplied transparent RTC splash logo was not packaged.", drawableId != 0)
    }

    @Test
    fun welcomeGetStartedOpensCreateAccountAndBackReturnsToWelcome() {
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("Create your RTC account").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Welcome to RTC\nCommunity").assertIsDisplayed()
    }

    @Test
    fun welcomeSignInOpensSecureSignInAndBackReturnsToWelcome() {
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.onNodeWithText("Sign in to RTC Community").assertIsDisplayed()
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Welcome to RTC\nCommunity").assertIsDisplayed()
    }

    @Test
    fun accountModeSwitchesBetweenCreateAndSignInWithoutSubmittingCredentials() {
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("I already have an account.").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.onNodeWithText("Sign in to RTC Community").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()
        composeRule.onNodeWithText("Create your RTC account").assertIsDisplayed()
    }
}

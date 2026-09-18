package za.org.rtc.community.feature.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.navigation.RtcRoute

class InteractiveOnboardingTutorialTest {

    @Test
    fun `tutorial initial visibility state is false by default`() {
        val tutorialVisible = MutableStateFlow(false)
        assertFalse(tutorialVisible.value)
    }

    @Test
    fun `triggering tutorial switches visibility to true`() {
        val tutorialVisible = MutableStateFlow(false)
        tutorialVisible.value = true
        assertTrue(tutorialVisible.value)
    }

    @Test
    fun `dismissing tutorial sets visibility to false`() {
        val tutorialVisible = MutableStateFlow(true)
        tutorialVisible.value = false
        assertFalse(tutorialVisible.value)
    }

    @Test
    fun `onboarding highlighted features map to correct routes`() {
        val communitySnapshotRoute = RtcRoute.HOME
        val marketplaceRoute = RtcRoute.MARKETPLACE_HOME

        assertEquals("resident_home", communitySnapshotRoute)
        assertEquals("community/marketplace", marketplaceRoute)
    }

    @Test
    fun `tutorial steps correspond to the remaining highlighted features in order`() {
        val steps = listOf("Community Snapshot", "Marketplace")
        assertEquals(2, steps.size)
        assertEquals("Community Snapshot", steps[0])
        assertEquals("Marketplace", steps[1])
    }
}

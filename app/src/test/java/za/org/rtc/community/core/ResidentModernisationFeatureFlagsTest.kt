package za.org.rtc.community.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentModernisationFeatureFlagsTest {
    @Test
    fun `production beta resident features are enabled by default`() {
        ResidentModernisationFeature.entries.forEach { feature ->
            assertTrue(
                "${feature.name} must be available in the production-beta build.",
                ResidentModernisationFeatureFlags.isEnabled(feature),
            )
            assertTrue("${feature.name} release default must be enabled.", feature.releaseDefaultEnabled)
        }
    }

    @Test
    fun `feature flags expose stable server configuration keys`() {
        assertEquals("public_reports_enabled", ResidentModernisationFeature.PUBLIC_REPORTS.configKey)
        assertEquals("resident_navigation_v2_enabled", ResidentModernisationFeature.RESIDENT_NAVIGATION_V2.configKey)
        assertEquals("community_events_enabled", ResidentModernisationFeature.COMMUNITY_EVENTS.configKey)
        assertEquals("unified_inbox_enabled", ResidentModernisationFeature.UNIFIED_INBOX.configKey)
    }
}

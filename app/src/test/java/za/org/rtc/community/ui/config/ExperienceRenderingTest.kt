package za.org.rtc.community.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test
import za.org.rtc.community.core.BrandDensityPreset
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.ScreenDensity
import za.org.rtc.community.core.ScreenPresentation
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcContentDensity

class ExperienceRenderingTest {
    @Test
    fun explicitCompactScreenDensityMapsToCompiledFeedDensity() {
        assertEquals(
            RtcContentDensity.FEED_CONTENT,
            ScreenPresentation(density = ScreenDensity.COMPACT).rtcContentDensity(BrandDensityPreset.COMFORTABLE),
        )
    }

    @Test
    fun standardScreenDensityInheritsGlobalCompactPreference() {
        assertEquals(
            RtcContentDensity.FEED_CONTENT,
            ScreenPresentation(density = ScreenDensity.STANDARD).rtcContentDensity(BrandDensityPreset.COMPACT),
        )
    }

    @Test
    fun routePresentationUsesOnlyCompiledScreenCatalogue() {
        val configuration = GlobalUiConfiguration.default()
        assertEquals(configuration.screens.marketplace, configuration.presentationForRoute(RtcRoute.MARKETPLACE_HOME))
        assertEquals(configuration.screens.support, configuration.presentationForRoute(RtcRoute.SUPPORT_CASE_DETAIL))
        assertEquals(configuration.screens.community, configuration.presentationForRoute(RtcRoute.COMMUNITY_FEED))
    }
}

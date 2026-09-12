package za.org.rtc.community.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import za.org.rtc.community.core.ResidentModernisationFeature
import za.org.rtc.community.core.ResidentModernisationFeatureFlags
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.residentPrimaryRoutes

class ResidentNavigationV2ActivationTest {
    private val navigationV2Enabled =
        ResidentModernisationFeatureFlags.isEnabled(ResidentModernisationFeature.RESIDENT_NAVIGATION_V2)

    @Test
    fun `active resident chrome exposes Home Community Explore Market and Account in order`() {
        val destinations = ResidentNavigationPolicy.primaryDestinations(navigationV2Enabled)

        assertEquals(
            listOf("Home", "Community", "Explore", "Market", "Account"),
            destinations.map { it.label },
        )
        assertEquals(
            listOf(RtcRoute.HOME, RtcRoute.COMMUNITY, RtcRoute.EXPLORE, RtcRoute.SERVICES, RtcRoute.ACCOUNT),
            destinations.map { it.route },
        )
        assertEquals(destinations.mapTo(linkedSetOf()) { it.route }, residentPrimaryRoutes(navigationV2Enabled))
    }

    @Test
    fun `chrome items follow the active navigation policy instead of a hardcoded list`() {
        assertEquals(
            ResidentNavigationPolicy.primaryDestinations(navigationV2Enabled).map { it.destination },
            navItems.map { it.destination },
        )
    }

    @Test
    fun `resident account and accessibility actions leave the top bar when navigation v2 is active`() {
        assertFalse(ResidentNavigationPolicy.showProfileAction(navigationV2Enabled, isStaff = false))
        assertFalse(ResidentNavigationPolicy.showReadingModeAction(navigationV2Enabled, isStaff = false))
    }
}

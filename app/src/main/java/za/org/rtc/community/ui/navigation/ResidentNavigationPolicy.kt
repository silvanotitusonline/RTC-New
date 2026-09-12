package za.org.rtc.community.ui.navigation

import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.navigation.InboxTab
import za.org.rtc.community.navigation.ResidentModernisationRoutes
import za.org.rtc.community.navigation.RtcRoute

internal data class ResidentPrimaryDestination(
    val destination: MainDestination,
    val label: String,
    val route: String,
)

internal object ResidentNavigationPolicy {
    fun primaryDestinations(enabled: Boolean): List<ResidentPrimaryDestination> =
        if (enabled) {
            listOf(
                ResidentPrimaryDestination(MainDestination.HOME, "Home", RtcRoute.HOME),
                ResidentPrimaryDestination(MainDestination.COMMUNITY, "Community", RtcRoute.COMMUNITY),
                ResidentPrimaryDestination(MainDestination.EXPLORE, "Explore", RtcRoute.EXPLORE),
                ResidentPrimaryDestination(MainDestination.SUPPORT, "Market", ResidentModernisationRoutes.services()),
                ResidentPrimaryDestination(MainDestination.ACCOUNT, "Account", ResidentModernisationRoutes.account()),
            )
        } else {
            listOf(
                ResidentPrimaryDestination(MainDestination.HOME, "Home", RtcRoute.HOME),
                ResidentPrimaryDestination(MainDestination.COMMUNITY, "Community", RtcRoute.COMMUNITY),
                ResidentPrimaryDestination(MainDestination.EXPLORE, "Explore", RtcRoute.EXPLORE),
                ResidentPrimaryDestination(MainDestination.SUPPORT, "Support", RtcRoute.SUPPORT),
            )
        }

    fun notificationRoute(unifiedInboxEnabled: Boolean, isStaff: Boolean): String? =
        ResidentModernisationRoutes.inbox(InboxTab.UPDATES)
            .takeIf { unifiedInboxEnabled && !isStaff }

    fun showProfileAction(navigationV2Enabled: Boolean, isStaff: Boolean): Boolean =
        isStaff || !navigationV2Enabled

    fun showReadingModeAction(navigationV2Enabled: Boolean, isStaff: Boolean): Boolean =
        !isStaff && !navigationV2Enabled
}

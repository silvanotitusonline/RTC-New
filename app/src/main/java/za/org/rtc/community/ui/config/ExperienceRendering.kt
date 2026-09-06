package za.org.rtc.community.ui.config

import za.org.rtc.community.core.BrandDensityPreset
import za.org.rtc.community.core.GlobalUiConfiguration
import za.org.rtc.community.core.ScreenDensity
import za.org.rtc.community.core.ScreenPresentation
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcContentDensity

/** Maps bounded administrator presentation choices onto the compiled RTC design system. */
fun ScreenPresentation.rtcContentDensity(globalDensity: BrandDensityPreset): RtcContentDensity = when (density) {
    ScreenDensity.COMPACT -> RtcContentDensity.FEED_CONTENT
    ScreenDensity.COMFORTABLE -> RtcContentDensity.RESIDENT_COMFORTABLE
    ScreenDensity.STANDARD -> when (globalDensity) {
        BrandDensityPreset.COMPACT -> RtcContentDensity.FEED_CONTENT
        BrandDensityPreset.STANDARD, BrandDensityPreset.COMFORTABLE -> RtcContentDensity.RESIDENT_COMFORTABLE
    }
}

/**
 * Central route mapper. Remote configuration selects only precompiled presentation presets;
 * it cannot introduce routes, composables, actions or executable layout instructions.
 */
fun GlobalUiConfiguration.presentationForRoute(route: String?): ScreenPresentation {
    val fallbackDensity = when (appearance.densityPreset) {
        BrandDensityPreset.COMPACT -> ScreenDensity.COMPACT
        BrandDensityPreset.STANDARD -> ScreenDensity.STANDARD
        BrandDensityPreset.COMFORTABLE -> ScreenDensity.COMFORTABLE
    }
    val fallback = ScreenPresentation(density = fallbackDensity)
    return when (route) {
        RtcRoute.COMMUNITY, RtcRoute.COMMUNITY_FEED, "community_post/{postId}" -> screens.community
        RtcRoute.EXPLORE, "explore_directory/{directory}", "directory_item/{type}/{id}", "notice/{id}" -> screens.explore
        RtcRoute.SUPPORT, RtcRoute.SUPPORT_CASE_DETAIL -> screens.support
        RtcRoute.ACCOUNT -> screens.account
        RtcRoute.NOTIFICATIONS, RtcRoute.ALERTS, RtcRoute.ALERT_DETAIL -> screens.notifications
        RtcRoute.SEARCH, "search_result/{type}/{id}" -> screens.search
        RtcRoute.HELP -> screens.help
        RtcRoute.MARKETPLACE_SEARCH -> screens.marketplaceSearch
        RtcRoute.MARKETPLACE_MAP -> screens.marketplaceMap
        RtcRoute.MARKETPLACE_MY_BUSINESSES,
        RtcRoute.MARKETPLACE_INVITATIONS,
        RtcRoute.MARKETPLACE_BUSINESS_NEW,
        RtcRoute.MARKETPLACE_BUSINESS_EDIT,
        RtcRoute.MARKETPLACE_BUSINESS_PREVIEW,
        RtcRoute.MARKETPLACE_BUSINESS_STATUS,
        RtcRoute.MARKETPLACE_SAVED,
        RtcRoute.MARKETPLACE_MY_REVIEWS -> screens.marketplaceAccount
        RtcRoute.MARKETPLACE_HOME,
        RtcRoute.MARKETPLACE_BUSINESS,
        RtcRoute.MARKETPLACE_REVIEWS,
        RtcRoute.MARKETPLACE_DIRECTIONS,
        RtcRoute.MARKETPLACE_NAVIGATION -> screens.marketplace
        else -> fallback
    }
}

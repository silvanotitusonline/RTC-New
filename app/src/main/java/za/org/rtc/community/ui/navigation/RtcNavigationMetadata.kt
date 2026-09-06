package za.org.rtc.community.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.ui.graphics.vector.ImageVector
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.PublicSearchResult
import za.org.rtc.community.core.ResidentModernisationFeature
import za.org.rtc.community.core.ResidentModernisationFeatureFlags
import za.org.rtc.community.navigation.RtcRoute

internal data class NavItem(
    val destination: MainDestination,
    val label: String,
    val route: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
)

internal val navItems: List<NavItem>
    get() = residentNavItems(
        ResidentModernisationFeatureFlags.isEnabled(ResidentModernisationFeature.RESIDENT_NAVIGATION_V2),
    )

internal fun residentNavItems(navigationV2Enabled: Boolean): List<NavItem> =
    ResidentNavigationPolicy.primaryDestinations(navigationV2Enabled).map { item ->
        val icons = when {
            item.destination == MainDestination.HOME -> Icons.Filled.Home to Icons.Outlined.Home
            item.destination == MainDestination.COMMUNITY -> Icons.Filled.Forum to Icons.Outlined.Forum
            item.destination == MainDestination.EXPLORE -> Icons.Filled.Explore to Icons.Outlined.Explore
            item.destination == MainDestination.ACCOUNT -> Icons.Filled.Person to Icons.Outlined.Person
            item.route == RtcRoute.SERVICES -> Icons.Filled.Storefront to Icons.Outlined.Storefront
            else -> Icons.Filled.SupportAgent to Icons.Outlined.SupportAgent
        }
        NavItem(item.destination, item.label, item.route, icons.first, icons.second)
    }

internal fun MainDestination.route(): String = when (this) {
    MainDestination.HOME -> RtcRoute.HOME
    MainDestination.COMMUNITY -> RtcRoute.COMMUNITY
    MainDestination.EXPLORE -> RtcRoute.EXPLORE
    MainDestination.SUPPORT -> RtcRoute.SUPPORT
    MainDestination.ACCOUNT -> RtcRoute.ACCOUNT
}

internal fun publicSearchResultRoute(result: PublicSearchResult): String =
    "search_result/${result.resultType}/${result.resultId}"

internal fun exploreDirectoryRoute(directory: String): String = "explore_directory/$directory"

internal fun communityPostRoute(postId: String): String = "community_post/$postId"

internal fun shareCommunityPost(context: Context, postId: String) {
    val safePostId = postId.takeIf { value ->
        value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
    } ?: return
    val shareText = "Open this RTC Community conversation in the app: rtc://community/post/$safePostId"
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, shareText),
            "Share Community post",
        ),
    )
}

internal fun directoryItemRoute(type: String, id: String): String = "directory_item/$type/$id"

internal fun noticeRoute(id: String): String = "notice/$id"

internal fun routeTitle(route: String?): String = when (route) {
    RtcRoute.HOME -> "Home"
    RtcRoute.COMMUNITY -> "Community"
    RtcRoute.EXPLORE -> "Explore"
    RtcRoute.SERVICES -> "Market"
    RtcRoute.SUPPORT -> "Support"
    RtcRoute.SUPPORT_CASE_DETAIL -> "Case details"
    RtcRoute.ACCOUNT -> "Account"
    RtcRoute.NOTIFICATIONS -> "Notifications"
    RtcRoute.ALERTS -> "Community alerts"
    RtcRoute.ALERT_DETAIL -> "Community alert"
    RtcRoute.STAFF_ALERTS -> "Community alerts"
    RtcRoute.SEARCH -> "Search"
    "search_result/{type}/{id}" -> "Search result"
    "explore_directory/{directory}" -> "Explore directory"
    "community_post/{postId}" -> "Community conversation"
    "directory_item/{type}/{id}" -> "Directory item"
    "notice/{id}" -> "Community notice"
    RtcRoute.HELP -> "Help Centre"
    RtcRoute.OPERATIONS_HUB -> "Operations Hub"
    RtcRoute.EDITORIAL_CONTENT -> "Content & publication"
    RtcRoute.MODERATOR_CENTRE -> "Community Safety"
    RtcRoute.AI -> "Ask RTC AI"
    RtcRoute.ACCESS_MANAGEMENT -> "Access Management"
    RtcRoute.ANALYTICS_DASHBOARD -> "Privacy Analytics"
    RtcRoute.ADMIN_MFA -> "Verify administrator MFA"
    RtcRoute.COMMUNITY_FEED -> "Community Feed"
    RtcRoute.MARKETPLACE_HOME -> "Community Marketplace"
    RtcRoute.MARKETPLACE_SEARCH -> "Search Marketplace"
    RtcRoute.MARKETPLACE_MAP -> "Marketplace map"
    RtcRoute.MARKETPLACE_BUSINESS -> "Business details"
    RtcRoute.MARKETPLACE_REVIEWS -> "Business reviews"
    RtcRoute.MARKETPLACE_MY_BUSINESSES -> "My businesses"
    RtcRoute.MARKETPLACE_BUSINESS_EDIT -> "Business editor"
    RtcRoute.MARKETPLACE_BUSINESS_PREVIEW -> "Business preview"
    RtcRoute.MARKETPLACE_BUSINESS_STATUS -> "Business status"
    RtcRoute.ADMIN_MARKETPLACE -> "Marketplace Operations"
    RtcRoute.SYSTEM_CONTROL_CENTRE -> "System Control Centre"
    RtcRoute.SYSTEM_HEALTH -> "System Health & Delivery"
    RtcRoute.ADMIN_ACTIVITY -> "Administrative Activity"
    RtcRoute.MY_WORK -> "My Work Profile"
    else -> "RTC Community"
}

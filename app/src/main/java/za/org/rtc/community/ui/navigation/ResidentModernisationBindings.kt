package za.org.rtc.community.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.feature.inbox.presentation.ResidentInboxScreen
import za.org.rtc.community.feature.marketplace.presentation.MarketHubScreen
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay

/**
 * Resident modernisation routes that remain active after removal of the legacy Events Calendar.
 */
internal fun NavGraphBuilder.residentModernisationBindings(
    navController: NavHostController,
    session: RtcSession,
) {
    @Suppress("UNUSED_VARIABLE") val sessionCompatibility = session
    composable(
        route = "inbox?tab={tab}",
        arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "updates" }),
    ) {
        ResidentInboxScreen(onOpenRoute = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.SERVICES) {
        MarketHubScreen(onNavigate = { navController.navigateOverlay(it) })
    }
}

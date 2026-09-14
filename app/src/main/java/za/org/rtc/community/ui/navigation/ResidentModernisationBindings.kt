package za.org.rtc.community.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.feature.events.presentation.AdminEventsScreen
import za.org.rtc.community.feature.events.presentation.CommunityEventsScreen
import za.org.rtc.community.feature.inbox.presentation.ResidentInboxScreen
import za.org.rtc.community.feature.marketplace.presentation.MarketHubScreen
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.returnToSafeWorkspace

internal fun NavGraphBuilder.residentModernisationBindings(
    navController: NavHostController,
    session: RtcSession,
) {
    composable(RtcRoute.EVENTS) { CommunityEventsScreen() }
    composable(
        route = "inbox?tab={tab}",
        arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "updates" }),
    ) {
        ResidentInboxScreen(onOpenRoute = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.SERVICES) {
        MarketHubScreen(onNavigate = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.ADMIN_EVENTS) {
        ProtectedRoute(
            RtcRoute.ADMIN_EVENTS,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            AdminEventsScreen()
        }
    }
}

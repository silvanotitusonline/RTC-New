package za.org.rtc.community.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.feature.account.AccountSettingsRoute
import za.org.rtc.community.feature.dailypost.presentation.AdminDailyPostStudioScreen
import za.org.rtc.community.feature.dailypost.presentation.DailyPostScreen
import za.org.rtc.community.feature.inbox.presentation.ResidentInboxScreen
import za.org.rtc.community.feature.marketplace.presentation.MarketHubScreen
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.returnToSafeWorkspace

/**
 * Resident modernisation bindings after removal of legacy resident-only feature surfaces.
 */
internal fun NavGraphBuilder.residentModernisationBindings(
    navController: NavHostController,
    session: RtcSession,
) {
    composable(
        route = "inbox?tab={tab}",
        arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "updates" }),
    ) {
        ResidentInboxScreen(onOpenRoute = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.ACCOUNT_SETTINGS) {
        AccountSettingsRoute(
            onOpenNotifications = { navController.navigateOverlay(RtcRoute.NOTIFICATIONS) },
            onOpenMarketplaceRoute = { route -> navController.navigateOverlay(route) },
        )
    }
    composable(RtcRoute.SERVICES) {
        MarketHubScreen(onNavigate = { navController.navigateOverlay(it) })
    }
    composable(
        route = RtcRoute.DAILY_POST_DETAIL,
        arguments = listOf(navArgument("postId") { type = NavType.StringType }),
    ) { entry ->
        DailyPostScreen(
            currentUserId = session.id.takeIf { session.authority != za.org.rtc.community.core.SessionAuthority.PUBLIC },
            initialPostId = entry.arguments?.getString("postId"),
        )
    }
    composable(RtcRoute.ADMIN_DAILY_POST) {
        ProtectedRoute(
            route = RtcRoute.ADMIN_DAILY_POST,
            session = session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            AdminDailyPostStudioScreen(onBack = { navController.popBackStack() })
        }
    }
}

package za.org.rtc.community.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.marketplace.presentation.*
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.returnToSafeWorkspace

internal fun NavGraphBuilder.marketplaceNavBindings(
    navController: NavHostController,
    session: RtcSession,
) {
    composable(RtcRoute.MARKETPLACE_HOME) {
        MarketplaceHomeRoute(onNavigate = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.MARKETPLACE_SEARCH) {
        MarketplaceSearchRoute(onNavigate = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.MARKETPLACE_MAP) {
        MarketplaceMapRoute(
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigateOverlay(route) },
        )
    }
    composable(
        route = RtcRoute.MARKETPLACE_BUSINESS,
        arguments = listOf(navArgument("businessIdOrSlug") { type = NavType.StringType }),
    ) { entry ->
        MarketplaceDetailRoute(
            id = entry.arguments?.getString("businessIdOrSlug").orEmpty(),
            onNavigate = { navController.navigateOverlay(it) },
        )
    }
    composable(
        route = RtcRoute.MARKETPLACE_REVIEWS,
        arguments = listOf(navArgument("businessId") { type = NavType.StringType }),
    ) { entry ->
        MarketplaceReviewsRoute(
            businessId = entry.arguments?.getString("businessId").orEmpty(),
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = RtcRoute.MARKETPLACE_DIRECTIONS,
        arguments = listOf(
            navArgument("businessId") { type = NavType.StringType },
            navArgument("locationId") { type = NavType.StringType },
        ),
    ) { entry ->
        MarketplaceDirectionsRoute(
            businessId = entry.arguments?.getString("businessId").orEmpty(),
            locationId = entry.arguments?.getString("locationId").orEmpty(),
            onBack = { navController.popBackStack() },
            onNavigate = { navController.navigateOverlay(it) },
        )
    }
    composable(
        route = RtcRoute.MARKETPLACE_NAVIGATION,
        arguments = listOf(navArgument("locationId") { type = NavType.StringType }),
    ) {
        MarketplaceMapRoute(onBack = { navController.popBackStack() })
    }
    composable(RtcRoute.MARKETPLACE_MY_BUSINESSES) {
        MarketplaceOwnerRoute(onNavigate = { navController.navigateOverlay(it) })
    }
    composable(RtcRoute.MARKETPLACE_SAVED) {
        MarketplaceSavedRoute(onNavigate = { route -> navController.navigateOverlay(route) })
    }
    composable(RtcRoute.MARKETPLACE_INVITATIONS) { MarketplaceInvitationsRoute() }
    composable(RtcRoute.MARKETPLACE_MY_REVIEWS) { MarketplaceMyReviewsRoute() }
    composable(RtcRoute.MARKETPLACE_BUSINESS_NEW) {
        MarketplaceOwnerWizardRoute(
            businessId = null,
            onNavigate = { navController.navigateOverlay(it) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = RtcRoute.MARKETPLACE_BUSINESS_EDIT,
        arguments = listOf(navArgument("businessId") { type = NavType.StringType }),
    ) { entry ->
        MarketplaceOwnerWizardRoute(
            businessId = entry.arguments?.getString("businessId"),
            onNavigate = { navController.navigateOverlay(it) },
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = RtcRoute.MARKETPLACE_BUSINESS_PREVIEW,
        arguments = listOf(navArgument("businessId") { type = NavType.StringType }),
    ) { entry ->
        MarketplaceOwnerPreviewRoute(entry.arguments?.getString("businessId").orEmpty())
    }
    composable(
        route = RtcRoute.MARKETPLACE_BUSINESS_STATUS,
        arguments = listOf(navArgument("businessId") { type = NavType.StringType }),
    ) { entry ->
        MarketplaceStatusRoute(entry.arguments?.getString("businessId").orEmpty())
    }
    composable(RtcRoute.ADMIN_MARKETPLACE) {
        ProtectedRoute(
            RtcRoute.ADMIN_MARKETPLACE,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            MarketplaceAdminRoute(onNavigate = { navController.navigateOverlay(it) })
        }
    }
    composable(
        route = RtcRoute.ADMIN_MARKETPLACE_BUSINESS,
        arguments = listOf(navArgument("submissionId") { type = NavType.StringType }),
    ) { entry ->
        ProtectedRoute(
            RtcRoute.ADMIN_MARKETPLACE_BUSINESS,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            MarketplaceAdminSubmissionRoute(
                submissionId = entry.arguments?.getString("submissionId").orEmpty(),
                canModerateLifecycle = session.role == UserRole.SYSTEM_ADMIN,
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(RtcRoute.ADMIN_MARKETPLACE_REVIEWS) {
        ProtectedRoute(
            RtcRoute.ADMIN_MARKETPLACE_REVIEWS,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            MarketplaceAdminReviewsRoute(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateOverlay(route) },
            )
        }
    }
    composable(RtcRoute.ADMIN_MARKETPLACE_CATEGORIES) {
        ProtectedRoute(
            RtcRoute.ADMIN_MARKETPLACE_CATEGORIES,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            MarketplaceAdminCategoriesRoute(onBack = { navController.popBackStack() })
        }
    }
    composable(RtcRoute.ADMIN_MARKETPLACE_FEATURED) {
        ProtectedRoute(
            RtcRoute.ADMIN_MARKETPLACE_FEATURED,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            MarketplaceAdminFeaturedRoute(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigateOverlay(route) },
            )
        }
    }
    composable(RtcRoute.ADMIN_MARKETPLACE_ANALYTICS) {
        ProtectedRoute(
            RtcRoute.ADMIN_MARKETPLACE_ANALYTICS,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            MarketplaceAdminAnalyticsRoute(onBack = { navController.popBackStack() })
        }
    }
}

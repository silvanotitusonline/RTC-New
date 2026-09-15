package za.org.rtc.community.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.feature.publicreports.presentation.PublicReportAdminScreen
import za.org.rtc.community.feature.publicreports.presentation.PublicReportComposerScreen
import za.org.rtc.community.feature.publicreports.presentation.PublicReportDetailScreen
import za.org.rtc.community.feature.publicreports.presentation.PublicReportsScreen
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.navigation.navigateOverlay
import za.org.rtc.community.navigation.returnToSafeWorkspace

internal fun NavGraphBuilder.publicReportRoutes(
    navController: NavHostController,
    session: RtcSession,
    guidelinesVersion: String = "1",
) {
    composable(RtcRoute.PUBLIC_REPORTS) {
        val isAdmin = session.role.isStaff || session.role in setOf(
            za.org.rtc.community.core.UserRole.MODERATOR,
            za.org.rtc.community.core.UserRole.SYSTEM_ADMIN,
            za.org.rtc.community.core.UserRole.CASE_STAFF,
            za.org.rtc.community.core.UserRole.EVIDENCE_REVIEWER,
        )
        PublicReportsScreen(
            isAdmin = isAdmin,
            onOpenReport = { navController.navigateOverlay(RtcRoute.publicReportDetail(it)) },
            onCompose = { navController.navigateOverlay(RtcRoute.PUBLIC_REPORT_NEW) },
            onOpenAdminWorkspace = { navController.navigateOverlay(RtcRoute.PUBLIC_REPORTS_ADMIN) },
        )
    }
    composable(RtcRoute.PUBLIC_REPORT_NEW) {
        PublicReportComposerScreen(
            guidelinesVersion = guidelinesVersion,
            onSubmitted = { id -> navController.navigateOverlay(RtcRoute.publicReportDetail(id)) },
        )
    }
    composable(
        route = RtcRoute.PUBLIC_REPORT_DETAIL,
        arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
    ) { entry ->
        PublicReportDetailScreen(reportId = entry.arguments?.getString("reportId").orEmpty())
    }
    composable(RtcRoute.PUBLIC_REPORTS_ADMIN) {
        ProtectedRoute(
            RtcRoute.PUBLIC_REPORTS_ADMIN,
            session,
            onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
        ) {
            PublicReportAdminScreen()
        }
    }
}

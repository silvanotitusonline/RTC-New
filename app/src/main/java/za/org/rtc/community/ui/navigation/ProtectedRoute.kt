package za.org.rtc.community.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.navigation.RouteAccessPolicy
import za.org.rtc.community.ui.components.PurposefulEmptyState

@Composable
internal fun ProtectedRoute(
    route: String,
    session: RtcSession,
    onDenied: () -> Unit,
    content: @Composable () -> Unit,
) {
    val decision = remember(route, session.role, session.authority, session.administratorMfaStatus) {
        RouteAccessPolicy.evaluate(route, session.role, session.authority, session.administratorMfaStatus)
    }
    if (decision.allowed) {
        content()
    } else {
        PurposefulEmptyState(
            decision.reason ?: "This protected RTC workspace is not available for the current session.",
            if (session.role.isStaff) "Return to Operations Hub" else "Return to Home",
            onDenied,
        )
    }
}

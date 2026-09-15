package za.org.rtc.community.navigation

import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole

data class RouteAccessDecision(
    val allowed: Boolean,
    val requiresMfa: Boolean = false,
    val reason: String? = null,
)

object RouteAccessPolicy {
    private val staffRoutes = setOf(
        RtcRoute.WORK_QUEUE, RtcRoute.MY_WORK, RtcRoute.STAFF_ALERTS,
        RtcRoute.CONTENT, RtcRoute.MODERATION, RtcRoute.AI,
        RtcRoute.PUBLIC_REPORTS_ADMIN, RtcRoute.DAILY_POST_STUDIO,
    )
    private val adminRoutes = setOf(
        RtcRoute.ACCESS_MANAGEMENT, RtcRoute.OPERATIONAL_CONTROLS,
        RtcRoute.ANALYTICS_DASHBOARD, RtcRoute.SYSTEM_HEALTH, RtcRoute.ADMIN_ACTIVITY,
        RtcRoute.ADMIN_BRANDING,
    )
    private val eventManagerRoutes = setOf(RtcRoute.ADMIN_EVENTS)
    private val marketplaceContentRoutes = setOf(
        RtcRoute.ADMIN_MARKETPLACE, RtcRoute.ADMIN_MARKETPLACE_BUSINESS,
        RtcRoute.ADMIN_MARKETPLACE_CATEGORIES, RtcRoute.ADMIN_MARKETPLACE_FEATURED,
        RtcRoute.ADMIN_MARKETPLACE_ANALYTICS,
    )
    private val marketplaceModerationRoutes = setOf(RtcRoute.ADMIN_MARKETPLACE_REVIEWS)

    fun evaluate(
        route: String?,
        role: UserRole,
        authority: SessionAuthority,
        mfaStatus: AdministratorMfaStatus,
    ): RouteAccessDecision {
        if (route == null) return RouteAccessDecision(true)
        if (route == RtcRoute.ADMIN_MFA) {
            return if (role == UserRole.SYSTEM_ADMIN) RouteAccessDecision(true)
            else RouteAccessDecision(false, reason = "System Administrator access required.")
        }
        if (route in adminRoutes) {
            if (role != UserRole.SYSTEM_ADMIN) return RouteAccessDecision(false, reason = "System Administrator access required.")
            val requiresMfa = authority == SessionAuthority.SUPABASE_AUTH && mfaStatus != AdministratorMfaStatus.VERIFIED
            return if (requiresMfa) RouteAccessDecision(false, requiresMfa = true, reason = "Verified administrator MFA required.") else RouteAccessDecision(true)
        }
        if (route in eventManagerRoutes) {
            return if (role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) {
                RouteAccessDecision(true)
            } else {
                RouteAccessDecision(false, reason = "Community Events require Content Editor or System Administrator access.")
            }
        }
        if (route in marketplaceContentRoutes) {
            if (role !in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) {
                return RouteAccessDecision(false, reason = "Marketplace publication access requires Content Editor or System Administrator authority.")
            }
            return RouteAccessDecision(true)
        }
        if (route in marketplaceModerationRoutes) {
            if (role !in setOf(UserRole.MODERATOR, UserRole.SYSTEM_ADMIN)) {
                return RouteAccessDecision(false, reason = "Marketplace review moderation requires Moderator or System Administrator authority.")
            }
            return RouteAccessDecision(true)
        }
        if (route in staffRoutes) {
            if (!role.isStaff) return RouteAccessDecision(false, reason = "Staff access required.")
            if (route == RtcRoute.STAFF_ALERTS && role !in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) return RouteAccessDecision(false, reason = "Community Alerts requires Content Editor or System Administrator access.")
            if (route == RtcRoute.CONTENT && role !in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) return RouteAccessDecision(false, reason = "Content Editor access required.")
            if (route == RtcRoute.MODERATION && role !in setOf(UserRole.MODERATOR, UserRole.SYSTEM_ADMIN)) return RouteAccessDecision(false, reason = "Moderator access required.")
            if (route == RtcRoute.AI && !role.canUseAi) return RouteAccessDecision(false, reason = "RTC AI is not available for this role.")
        }
        if (route.startsWith("admin/")) {
            return RouteAccessDecision(
                allowed = false,
                reason = "This administrative workspace is not classified for access.",
            )
        }
        return RouteAccessDecision(true)
    }
}

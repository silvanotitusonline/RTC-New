package za.org.rtc.community.feature.administration

import za.org.rtc.community.core.UserRole
import za.org.rtc.community.navigation.RtcRoute

enum class AdminWorkspaceArea(val label: String) {
    NEEDS_ATTENTION("Needs attention"),
    CONTENT_ALERTS("Content & alerts"),
    COMMUNITY_SAFETY("Community safety"),
    CASE_WORK("Case work"),
    SYSTEM_ADMINISTRATION("System administration"),
}

data class AdminWorkspaceDestination(
    val id: String,
    val title: String,
    val detail: String,
    val route: String,
    val area: AdminWorkspaceArea,
    val allowedRoles: Set<UserRole>,
    val requiresMfa: Boolean = false,
)

private val allStaffRoles = setOf(
    UserRole.CASE_STAFF,
    UserRole.CONTENT_EDITOR,
    UserRole.MODERATOR,
    UserRole.EVIDENCE_REVIEWER,
    UserRole.SYSTEM_ADMIN,
)

private val moderatorRoles = setOf(
    UserRole.MODERATOR,
    UserRole.EVIDENCE_REVIEWER,
    UserRole.SYSTEM_ADMIN,
)

private val editorialRoles = setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)
private val systemAdminRoles = setOf(UserRole.SYSTEM_ADMIN)

private val adminDestinationCatalog = listOf(
    AdminWorkspaceDestination(
        id = "work_profile",
        title = "My work profile",
        detail = "Availability, staff notifications, account settings and sign-out.",
        route = RtcRoute.MY_WORK,
        area = AdminWorkspaceArea.CASE_WORK,
        allowedRoles = allStaffRoles,
    ),
    AdminWorkspaceDestination(
        id = "community_alerts",
        title = "Community alerts",
        detail = "Create resident alerts and review delivery follow-up.",
        route = RtcRoute.STAFF_ALERTS,
        area = AdminWorkspaceArea.CONTENT_ALERTS,
        allowedRoles = editorialRoles,
    ),
    AdminWorkspaceDestination(
        id = "daily_post_studio",
        title = "The Daily Post Studio",
        detail = "Draft multimedia stories, choose templates, preview, quote RTC publications, schedule broadcasts and send publication pushes.",
        route = RtcRoute.ADMIN_DAILY_POST,
        area = AdminWorkspaceArea.CONTENT_ALERTS,
        allowedRoles = editorialRoles,
    ),
    AdminWorkspaceDestination(
        id = "content_management",
        title = "Content management",
        detail = "Draft, review, publish, correct or retire official notices.",
        route = RtcRoute.CONTENT,
        area = AdminWorkspaceArea.CONTENT_ALERTS,
        allowedRoles = editorialRoles,
    ),
    AdminWorkspaceDestination(
        id = "public_reports",
        title = "Public reports",
        detail = "Review civic reports, verification evidence and public status updates.",
        route = RtcRoute.PUBLIC_REPORTS_ADMIN,
        area = AdminWorkspaceArea.COMMUNITY_SAFETY,
        allowedRoles = moderatorRoles,
    ),
    AdminWorkspaceDestination(
        id = "moderation",
        title = "Moderation centre",
        detail = "Review reports, appeals and safeguarded moderation decisions.",
        route = RtcRoute.MODERATION,
        area = AdminWorkspaceArea.COMMUNITY_SAFETY,
        allowedRoles = moderatorRoles,
    ),
    AdminWorkspaceDestination(
        id = "access_management",
        title = "Access management",
        detail = "Verified accounts, roles, approvals and role-change audit history.",
        route = RtcRoute.ACCESS_MANAGEMENT,
        area = AdminWorkspaceArea.SYSTEM_ADMINISTRATION,
        allowedRoles = systemAdminRoles,
        requiresMfa = true,
    ),
    AdminWorkspaceDestination(
        id = "operational_controls",
        title = "Operational controls",
        detail = "Guarded production controls, incidents and deliberate service interventions.",
        route = RtcRoute.OPERATIONAL_CONTROLS,
        area = AdminWorkspaceArea.SYSTEM_ADMINISTRATION,
        allowedRoles = systemAdminRoles,
        requiresMfa = true,
    ),
    AdminWorkspaceDestination(
        id = "system_health",
        title = "System health",
        detail = "Service health, affected counts and operational status signals.",
        route = RtcRoute.SYSTEM_HEALTH,
        area = AdminWorkspaceArea.SYSTEM_ADMINISTRATION,
        allowedRoles = systemAdminRoles,
        requiresMfa = true,
    ),
    AdminWorkspaceDestination(
        id = "privacy_analytics",
        title = "Privacy analytics",
        detail = "Aggregate usage metrics, purpose-limited account lookup and audit trail.",
        route = RtcRoute.ANALYTICS_DASHBOARD,
        area = AdminWorkspaceArea.SYSTEM_ADMINISTRATION,
        allowedRoles = systemAdminRoles,
        requiresMfa = true,
    ),
    AdminWorkspaceDestination(
        id = "admin_activity",
        title = "Administrative activity",
        detail = "Review audited administrative actions and operational history.",
        route = RtcRoute.ADMIN_ACTIVITY,
        area = AdminWorkspaceArea.SYSTEM_ADMINISTRATION,
        allowedRoles = systemAdminRoles,
        requiresMfa = true,
    ),
    AdminWorkspaceDestination(
        id = "brand_experience",
        title = "Brand & experience",
        detail = "Manage approved brand and product-experience configuration.",
        route = RtcRoute.ADMIN_BRANDING,
        area = AdminWorkspaceArea.SYSTEM_ADMINISTRATION,
        allowedRoles = systemAdminRoles,
        requiresMfa = true,
    ),
)

fun adminWorkspaceDestinations(role: UserRole): List<AdminWorkspaceDestination> =
    adminDestinationCatalog.filter { role in it.allowedRoles }

fun filterAdminWorkspaceDestinations(
    destinations: List<AdminWorkspaceDestination>,
    query: String,
): List<AdminWorkspaceDestination> {
    val needle = query.trim()
    if (needle.isBlank()) return destinations
    return destinations.filter { destination ->
        destination.title.contains(needle, ignoreCase = true) ||
            destination.detail.contains(needle, ignoreCase = true) ||
            destination.area.label.contains(needle, ignoreCase = true)
    }
}

fun resolveAdminDestinationRoute(
    destination: AdminWorkspaceDestination,
    requiresMfa: Boolean,
): String = if (destination.requiresMfa && requiresMfa) RtcRoute.ADMIN_MFA else destination.route

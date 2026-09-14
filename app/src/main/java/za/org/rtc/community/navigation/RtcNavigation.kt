package za.org.rtc.community.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Typed route constants keep navigation separate from user-facing labels and role checks. */
object RtcRoute {
    const val HOME = "resident_home"
    const val COMMUNITY = "resident_community"
    const val EXPLORE = "resident_explore"
    const val SUPPORT = "resident_support"
    const val SUPPORT_CASE_DETAIL = "support_case/{caseId}"
    const val ACCOUNT = "account"
    const val SERVICES = "services"
    const val EVENTS = "events"
    const val ADMIN_EVENTS = "admin/events"
    const val NOTIFICATIONS = "notifications"
    const val SEARCH = "search"
    const val HELP = "help"
    const val OPERATIONS_HUB = "operations_hub"
    const val MODERATOR_CENTRE = "moderator_centre"
    const val EDITORIAL_CONTENT = "editorial_content"
    const val SYSTEM_CONTROL_CENTRE = "system_control_centre"
    const val SYSTEM_HEALTH = "system_health"
    const val ADMIN_ACTIVITY = "administrative_activity"
    const val ADMIN_BRANDING = "admin/branding"
    const val WORK_QUEUE = OPERATIONS_HUB
    const val CONTENT = EDITORIAL_CONTENT
    const val MODERATION = MODERATOR_CENTRE
    const val AI = "rtc_ai"
    const val OPERATIONAL_CONTROLS = SYSTEM_CONTROL_CENTRE
    const val MY_WORK = "my_work_profile"
    const val ALERTS = "community_alerts"
    const val ALERT_DETAIL = "community_alert/{alertId}"
    const val STAFF_ALERTS = "staff_alerts"
    const val ACCESS_MANAGEMENT = "access_management"
    const val ANALYTICS_DASHBOARD = "analytics_dashboard"
    const val ADMIN_MFA = "administrator_mfa"
    const val COMMUNITY_FEED = "community/feed"
    const val PUBLIC_REPORTS = "community/public-reports"
    const val PUBLIC_REPORT_NEW = "public-report/new"
    const val PUBLIC_REPORT_DETAIL = "public-report/{reportId}"
    const val PUBLIC_REPORTS_ADMIN = "staff/public-reports"
    const val MARKETPLACE_HOME = "community/marketplace"
    const val MARKETPLACE_SEARCH = "community/marketplace/search"
    const val MARKETPLACE_MAP = "community/marketplace/map"
    const val MARKETPLACE_BUSINESS = "community/marketplace/business/{businessIdOrSlug}"
    const val MARKETPLACE_REVIEWS = "community/marketplace/business/{businessId}/reviews"
    const val MARKETPLACE_DIRECTIONS = "community/marketplace/business/{businessId}/directions/{locationId}"
    const val MARKETPLACE_NAVIGATION = "community/marketplace/navigation/{locationId}"
    const val MARKETPLACE_MY_BUSINESSES = "account/marketplace/my-businesses"
    const val MARKETPLACE_INVITATIONS = "account/marketplace/invitations"
    const val MARKETPLACE_BUSINESS_NEW = "account/marketplace/business/new"
    const val MARKETPLACE_BUSINESS_EDIT = "account/marketplace/business/{businessId}/edit"
    const val MARKETPLACE_BUSINESS_PREVIEW = "account/marketplace/business/{businessId}/preview"
    const val MARKETPLACE_BUSINESS_STATUS = "account/marketplace/business/{businessId}/status"
    const val MARKETPLACE_SAVED = "account/marketplace/saved"
    const val MARKETPLACE_MY_REVIEWS = "account/marketplace/my-reviews"
    const val ADMIN_MARKETPLACE = "admin/marketplace"
    const val ADMIN_MARKETPLACE_BUSINESS = "admin/marketplace/business/{submissionId}"
    const val ADMIN_MARKETPLACE_REVIEWS = "admin/marketplace/reviews"
    const val ADMIN_MARKETPLACE_CATEGORIES = "admin/marketplace/categories"
    const val ADMIN_MARKETPLACE_FEATURED = "admin/marketplace/featured"
    const val ADMIN_MARKETPLACE_ANALYTICS = "admin/marketplace/analytics"

    const val SERVICE_CENTRE_HOME = "community/service-centre"
    const val SERVICE_CENTRE_REQUEST = "community/service-centre/request/{providerId}"
    const val SERVICE_CENTRE_REQUEST_PATTERN = "community/service-centre/request/{providerId}?businessId={businessId}&offeringId={offeringId}"
    const val SERVICE_CENTRE_PROVIDER = "account/service-centre/provider"
    const val SERVICE_CENTRE_BOOKINGS = "account/service-centre/bookings"
    const val SERVICE_CENTRE_BOOKING = "account/service-centre/booking/{bookingId}"
    const val SERVICE_CENTRE_CHAT = "account/service-centre/chat/{bookingId}"

    fun marketplaceBusiness(idOrSlug: String) = "community/marketplace/business/$idOrSlug"
    fun marketplaceReviews(businessId: String) = "community/marketplace/business/$businessId/reviews"
    fun marketplaceEdit(businessId: String) = "account/marketplace/business/$businessId/edit"
    fun marketplaceStatus(businessId: String) = "account/marketplace/business/$businessId/status"
    fun serviceCentreRequest(providerId: String, businessId: String? = null, offeringId: String? = null): String = buildString {
        append("community/service-centre/request/").append(providerId)
        val query = listOfNotNull(
            businessId?.let { "businessId=$it" },
            offeringId?.let { "offeringId=$it" },
        )
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
    }
    fun serviceCentreBooking(bookingId: String) = "account/service-centre/booking/$bookingId"
    fun serviceCentreChat(bookingId: String) = "account/service-centre/chat/$bookingId"
    fun alertDetail(alertId: String) = "community_alert/$alertId"
    fun supportCaseDetail(caseId: String) = "support_case/$caseId"
    fun publicReportDetail(reportId: String) = "public-report/$reportId"
}

fun residentPrimaryRoutes(navigationV2Enabled: Boolean): Set<String> =
    if (navigationV2Enabled) {
        setOf(RtcRoute.HOME, RtcRoute.COMMUNITY, RtcRoute.EXPLORE, RtcRoute.SERVICES, RtcRoute.ACCOUNT)
    } else {
        setOf(RtcRoute.HOME, RtcRoute.COMMUNITY, RtcRoute.EXPLORE, RtcRoute.SUPPORT)
    }

fun NavHostController.navigatePrimary(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavHostController.navigateOverlay(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

fun NavHostController.returnToSafeWorkspace(isStaff: Boolean) {
    val safeRoute = if (isStaff) RtcRoute.OPERATIONS_HUB else RtcRoute.HOME
    popBackStack(safeRoute, inclusive = false)
    if (currentDestination?.route != safeRoute) {
        navigate(safeRoute) { launchSingleTop = true }
    }
}

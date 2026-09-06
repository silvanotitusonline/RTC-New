package za.org.rtc.community.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessLifecycleAction

class MarketplaceRouteAccessPolicyTest {
    private fun decision(route: String, role: UserRole) = RouteAccessPolicy.evaluate(
        route = route,
        role = role,
        authority = SessionAuthority.DEVELOPMENT_ADAPTER,
        mfaStatus = AdministratorMfaStatus.VERIFIED,
    )

    @Test
    fun residentAndModeratorCannotOpenMarketplacePublicationRoutes() {
        listOf(
            RtcRoute.ADMIN_MARKETPLACE,
            RtcRoute.ADMIN_MARKETPLACE_BUSINESS,
            RtcRoute.ADMIN_MARKETPLACE_CATEGORIES,
            RtcRoute.ADMIN_MARKETPLACE_FEATURED,
            RtcRoute.ADMIN_MARKETPLACE_ANALYTICS,
        ).forEach { route ->
            assertFalse(decision(route, UserRole.RESIDENT_A).allowed)
            assertFalse(decision(route, UserRole.MODERATOR).allowed)
        }
    }

    @Test
    fun contentEditorCanPublishButCannotModerateMarketplaceReviews() {
        assertTrue(decision(RtcRoute.ADMIN_MARKETPLACE, UserRole.CONTENT_EDITOR).allowed)
        assertTrue(decision(RtcRoute.ADMIN_MARKETPLACE_BUSINESS, UserRole.CONTENT_EDITOR).allowed)
        assertFalse(decision(RtcRoute.ADMIN_MARKETPLACE_REVIEWS, UserRole.CONTENT_EDITOR).allowed)
    }

    @Test
    fun moderatorCanModerateReviewsButCannotPublishMarketplaceListings() {
        assertTrue(decision(RtcRoute.ADMIN_MARKETPLACE_REVIEWS, UserRole.MODERATOR).allowed)
        assertFalse(decision(RtcRoute.ADMIN_MARKETPLACE, UserRole.MODERATOR).allowed)
        assertFalse(decision(RtcRoute.ADMIN_MARKETPLACE_BUSINESS, UserRole.MODERATOR).allowed)
    }

    @Test
    fun systemAdministratorCanOpenAllMarketplaceAdministrationRoutes() {
        listOf(
            RtcRoute.ADMIN_MARKETPLACE,
            RtcRoute.ADMIN_MARKETPLACE_BUSINESS,
            RtcRoute.ADMIN_MARKETPLACE_REVIEWS,
            RtcRoute.ADMIN_MARKETPLACE_CATEGORIES,
            RtcRoute.ADMIN_MARKETPLACE_FEATURED,
            RtcRoute.ADMIN_MARKETPLACE_ANALYTICS,
        ).forEach { route -> assertTrue(decision(route, UserRole.SYSTEM_ADMIN).allowed) }
    }

    @Test
    fun lifecycleActionsAreDerivedFromBackendAuthoritativeState() {
        assertEquals(setOf(MarketplaceBusinessLifecycleAction.SUSPEND), MarketplaceBusinessLifecycleAction.forState("PUBLISHED"))
        assertEquals(setOf(MarketplaceBusinessLifecycleAction.REINSTATE), MarketplaceBusinessLifecycleAction.forState("SUSPENDED"))
        assertTrue(MarketplaceBusinessLifecycleAction.forState("DRAFT").isEmpty())
    }
}

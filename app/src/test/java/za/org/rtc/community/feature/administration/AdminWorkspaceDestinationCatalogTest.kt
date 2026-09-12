package za.org.rtc.community.feature.administration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.navigation.RtcRoute

class AdminWorkspaceDestinationCatalogTest {
    @Test
    fun `content editor sees editorial tools but not system administration`() {
        val destinations = adminWorkspaceDestinations(UserRole.CONTENT_EDITOR)
        assertTrue(destinations.any { it.route == RtcRoute.CONTENT })
        assertTrue(destinations.any { it.route == RtcRoute.STAFF_ALERTS })
        assertFalse(destinations.any { it.route == RtcRoute.ACCESS_MANAGEMENT })
    }

    @Test
    fun `moderator sees moderation tools but not access management`() {
        val destinations = adminWorkspaceDestinations(UserRole.MODERATOR)
        assertTrue(destinations.any { it.route == RtcRoute.MODERATION })
        assertTrue(destinations.any { it.route == RtcRoute.PUBLIC_REPORTS_ADMIN })
        assertFalse(destinations.any { it.route == RtcRoute.ACCESS_MANAGEMENT })
    }

    @Test
    fun `system administrator receives protected system destinations`() {
        val destinations = adminWorkspaceDestinations(UserRole.SYSTEM_ADMIN)
        assertTrue(destinations.any { it.route == RtcRoute.ACCESS_MANAGEMENT && it.requiresMfa })
        assertTrue(destinations.any { it.route == RtcRoute.SYSTEM_HEALTH && it.requiresMfa })
    }

    @Test
    fun `search matches title and detail case insensitively`() {
        val destinations = adminWorkspaceDestinations(UserRole.SYSTEM_ADMIN)
        val result = filterAdminWorkspaceDestinations(destinations, "audit")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { (it.title + " " + it.detail).contains("audit", ignoreCase = true) })
    }

    @Test
    fun `protected destination routes through MFA when verification is required`() {
        val destination = adminWorkspaceDestinations(UserRole.SYSTEM_ADMIN)
            .first { it.route == RtcRoute.ACCESS_MANAGEMENT }
        assertEquals(RtcRoute.ADMIN_MFA, resolveAdminDestinationRoute(destination, requiresMfa = true))
        assertEquals(RtcRoute.ACCESS_MANAGEMENT, resolveAdminDestinationRoute(destination, requiresMfa = false))
    }
}

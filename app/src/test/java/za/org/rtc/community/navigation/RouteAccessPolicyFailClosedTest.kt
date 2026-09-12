package za.org.rtc.community.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole

class RouteAccessPolicyFailClosedTest {
    private fun decision(route: String?, role: UserRole = UserRole.SYSTEM_ADMIN) = RouteAccessPolicy.evaluate(
        route = route,
        role = role,
        authority = SessionAuthority.SUPABASE_AUTH,
        mfaStatus = AdministratorMfaStatus.VERIFIED,
    )

    @Test
    fun unknownAdministrativeWorkspaceFailsClosedUntilExplicitlyClassified() {
        assertFalse(decision("admin/future-protected-workspace").allowed)
        assertFalse(decision("admin/unknown/deep-link", UserRole.RESIDENT_A).allowed)
    }

    @Test
    fun residentAndNullRoutesKeepTheirExistingPublicDefault() {
        assertTrue(decision(RtcRoute.HOME, UserRole.RESIDENT_A).allowed)
        assertTrue(decision(RtcRoute.COMMUNITY, UserRole.RESIDENT_A).allowed)
        assertTrue(decision(null, UserRole.RESIDENT_A).allowed)
    }
}

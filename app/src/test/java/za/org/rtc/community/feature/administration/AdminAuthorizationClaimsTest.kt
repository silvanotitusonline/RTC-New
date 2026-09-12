package za.org.rtc.community.feature.administration

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.feature.administration.security.hasServerAdminClaim

class AdminAuthorizationClaimsTest {
    @Test
    fun `server is_admin claim authorizes administrator`() {
        val appMetadata = buildJsonObject { put("is_admin", true) }
        assertTrue(hasServerAdminClaim(appMetadata))
    }

    @Test
    fun `server system admin role authorizes administrator`() {
        val appMetadata = buildJsonObject { put("role", "SYSTEM_ADMIN") }
        assertTrue(hasServerAdminClaim(appMetadata))
    }

    @Test
    fun `non administrative server role is rejected`() {
        val appMetadata = buildJsonObject { put("role", "MODERATOR") }
        assertFalse(hasServerAdminClaim(appMetadata))
    }

    @Test
    fun `missing server metadata is rejected`() {
        assertFalse(hasServerAdminClaim(null))
    }
}

package za.org.rtc.community.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import za.org.rtc.community.core.UserRole

class RtcSessionPrivilegeRestorationTest {
    @Test
    fun `administrator-looking email cannot restore system admin from local cache`() {
        val role = resolveLocalRestoredRole(
            cachedEmail = "admin-security@rtc.community",
            cachedRole = UserRole.SYSTEM_ADMIN.name,
        )

        assertEquals(UserRole.RESIDENT_A, role)
    }

    @Test
    fun `previous system admin cache cannot restore privilege without server session`() {
        val role = resolveLocalRestoredRole(
            cachedEmail = "resident@example.org",
            cachedRole = UserRole.SYSTEM_ADMIN.name,
        )

        assertEquals(UserRole.RESIDENT_A, role)
    }

    @Test
    fun `cached staff roles fail closed without verified server claims`() {
        listOf(
            UserRole.CASE_STAFF,
            UserRole.CONTENT_EDITOR,
            UserRole.MODERATOR,
            UserRole.EVIDENCE_REVIEWER,
            UserRole.SYSTEM_ADMIN,
        ).forEach { cachedRole ->
            assertEquals(
                UserRole.RESIDENT_A,
                resolveLocalRestoredRole("staff@rtc.community", cachedRole.name),
            )
        }
    }

    @Test
    fun `database system admin role alone cannot elevate verified client session`() {
        val role = resolveVerifiedServerRole(
            appMetadata = null,
            resolvedRoles = listOf(UserRole.SYSTEM_ADMIN),
        )

        assertEquals(UserRole.RESIDENT_A, role)
    }

    @Test
    fun `server managed app metadata authorizes system administrator`() {
        val role = resolveVerifiedServerRole(
            appMetadata = buildJsonObject { put("role", "SYSTEM_ADMIN") },
            resolvedRoles = emptyList(),
        )

        assertEquals(UserRole.SYSTEM_ADMIN, role)
    }
}

package za.org.rtc.community.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole

class AuthStateViewModelTest {

    @Test
    fun testAuthStatusUnauthenticated() {
        val status: AuthStatus = AuthStatus.Unauthenticated
        val uiState = AuthStateUiState(
            authStatus = status,
            isLoading = false,
            errorMessage = null,
        )

        assertFalse(uiState.isAuthenticated)
        assertEquals(AuthStatus.Unauthenticated, uiState.authStatus)
        assertNull(uiState.errorMessage)
    }

    @Test
    fun testAuthStatusAuthenticated() {
        val testSession = RtcSession(
            id = "user_123",
            displayName = "Jane Resident",
            handle = "jane_res",
            role = UserRole.RESIDENT_A,
            authority = SessionAuthority.SUPABASE_AUTH,
            authenticatedEmail = "jane@example.org"
        )
        val status: AuthStatus = AuthStatus.Authenticated(
            session = testSession,
            userEmail = "jane@example.org",
            userId = "user_123"
        )
        val uiState = AuthStateUiState(
            authStatus = status,
            isLoading = false,
            errorMessage = null,
        )

        assertTrue(uiState.isAuthenticated)
        assertEquals("user_123", (uiState.authStatus as AuthStatus.Authenticated).userId)
        assertEquals("jane@example.org", (uiState.authStatus as AuthStatus.Authenticated).userEmail)
    }

    @Test
    fun testAuthStatusLoading() {
        val uiState = AuthStateUiState(
            authStatus = AuthStatus.Loading,
            isLoading = true,
            errorMessage = null
        )

        assertFalse(uiState.isAuthenticated)
        assertTrue(uiState.isLoading)
    }
}

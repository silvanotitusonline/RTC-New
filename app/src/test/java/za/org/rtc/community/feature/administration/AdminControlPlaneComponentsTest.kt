package za.org.rtc.community.feature.administration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.feature.administration.security.AdminSecurityException

class AdminControlPlaneComponentsTest {

    @Test
    fun testAdminDashboardUiStateCalculation() {
        val state = AdminDashboardUiState(
            reportsCount = 5,
            businessSubmissionsCount = 3,
            supportRequestsCount = 8,
            totalPendingTasks = 16,
            isLoading = false,
            errorMessage = null,
            isAuthorized = true
        )

        assertEquals(16L, state.totalPendingTasks)
        assertEquals(5L, state.reportsCount)
        assertEquals(3L, state.businessSubmissionsCount)
        assertEquals(8L, state.supportRequestsCount)
        assertTrue(state.isAuthorized)
        assertFalse(state.isLoading)
    }

    @Test
    fun testAdminSecurityExceptionMessage() {
        val exception = AdminSecurityException("Administrative access denied for user")
        assertEquals("Administrative access denied for user", exception.message)
    }
}

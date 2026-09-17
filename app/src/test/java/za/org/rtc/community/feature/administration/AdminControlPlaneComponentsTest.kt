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
            noticesCount = 3,
            eventsCount = 2,
            workQueueCount = 6,
            totalPendingTasks = 16,
            isLoading = false,
            errorMessage = null,
            isAuthorized = true
        )

        assertEquals(16L, state.totalPendingTasks)
        assertEquals(5L, state.reportsCount)
        assertEquals(3L, state.noticesCount)
        assertEquals(2L, state.eventsCount)
        assertEquals(6L, state.workQueueCount)
        assertTrue(state.isAuthorized)
        assertFalse(state.isLoading)
    }

    @Test
    fun testAdminSecurityExceptionMessage() {
        val exception = AdminSecurityException("Administrative access denied for user")
        assertEquals("Administrative access denied for user", exception.message)
    }
}

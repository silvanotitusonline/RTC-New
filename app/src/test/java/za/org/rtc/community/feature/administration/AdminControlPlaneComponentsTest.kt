package za.org.rtc.community.feature.administration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.feature.administration.security.AdminSecurityException

class AdminControlPlaneComponentsTest {

    @Test
    fun `dashboard attention state reflects protected operations summary`() {
        val state = AdminDashboardUiState(
            summary = AdminOperationsSummary(
                assignedToMe = 3,
                highPriority = 2,
                unassigned = 4,
                readyForReview = 1,
                overdue = 1,
                totalVisible = 9,
            ),
            isLoading = false,
            errorMessage = null,
            lastRefreshedMillis = 123L,
        )

        assertEquals(3L, state.summary.assignedToMe)
        assertEquals(2L, state.summary.highPriority)
        assertEquals(4L, state.summary.unassigned)
        assertEquals(1L, state.summary.readyForReview)
        assertEquals(1L, state.summary.overdue)
        assertEquals(9L, state.summary.totalVisible)
        assertTrue(state.hasAttentionItems)
        assertFalse(state.isLoading)
    }

    @Test
    fun `attention state is false when no urgent review or overdue work exists`() {
        val state = AdminDashboardUiState(
            summary = AdminOperationsSummary(
                assignedToMe = 2,
                totalVisible = 2,
            ),
        )

        assertFalse(state.hasAttentionItems)
    }

    @Test
    fun testAdminSecurityExceptionMessage() {
        val exception = AdminSecurityException("Administrative access denied for user")
        assertEquals("Administrative access denied for user", exception.message)
    }
}

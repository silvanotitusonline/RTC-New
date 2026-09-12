package za.org.rtc.community.feature.servicecentre.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCentreBookingStateTest {
    @Test
    fun pendingMapsToPendingTabAndAllowsDirectConfirmationOrClosure() {
        val pending = ServiceCentreBookingStatus.PENDING_PROVIDER

        assertEquals(ServiceCentreBookingTab.PENDING, pending.tab)
        assertTrue(pending.canTransitionTo(ServiceCentreBookingStatus.CONFIRMED))
        assertTrue(pending.canTransitionTo(ServiceCentreBookingStatus.DECLINED))
        assertTrue(pending.canTransitionTo(ServiceCentreBookingStatus.CANCELLED))
        assertFalse(pending.canTransitionTo(ServiceCentreBookingStatus.COMPLETED))
    }

    @Test
    fun confirmedMapsToAcceptedAndAllowsCompletionOrCancellation() {
        val confirmed = ServiceCentreBookingStatus.CONFIRMED

        assertEquals(ServiceCentreBookingTab.ACCEPTED, confirmed.tab)
        assertTrue(confirmed.canTransitionTo(ServiceCentreBookingStatus.COMPLETED))
        assertTrue(confirmed.canTransitionTo(ServiceCentreBookingStatus.CANCELLED))
        assertFalse(confirmed.canTransitionTo(ServiceCentreBookingStatus.DECLINED))
    }

    @Test
    fun terminalStatesCannotTransition() {
        listOf(
            ServiceCentreBookingStatus.COMPLETED,
            ServiceCentreBookingStatus.DECLINED,
            ServiceCentreBookingStatus.CANCELLED,
        ).forEach { terminal ->
            assertEquals(ServiceCentreBookingTab.HISTORY, terminal.tab)
            ServiceCentreBookingStatus.entries.forEach { target ->
                assertFalse("$terminal must be terminal", terminal.canTransitionTo(target))
            }
        }
    }
}

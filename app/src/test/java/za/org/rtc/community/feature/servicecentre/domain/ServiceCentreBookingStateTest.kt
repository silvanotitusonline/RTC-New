package za.org.rtc.community.feature.servicecentre.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCentreBookingStateTest {
    @Test
    fun pendingMapsToPendingTabAndOnlyAllowsProviderDecisionOrCancellation() {
        val pending = ServiceCentreBookingStatus.PENDING_PROVIDER

        assertEquals(ServiceCentreBookingTab.PENDING, pending.tab)
        assertTrue(pending.canTransitionTo(ServiceCentreBookingStatus.ACCEPTED_AWAITING_PAYMENT))
        assertTrue(pending.canTransitionTo(ServiceCentreBookingStatus.DECLINED))
        assertTrue(pending.canTransitionTo(ServiceCentreBookingStatus.CANCELLED))
        assertFalse(pending.canTransitionTo(ServiceCentreBookingStatus.CONFIRMED))
        assertFalse(pending.canTransitionTo(ServiceCentreBookingStatus.COMPLETED))
    }

    @Test
    fun acceptedAwaitingPaymentMapsToAcceptedAndOnlyAllowsConfirmationOrCancellation() {
        val accepted = ServiceCentreBookingStatus.ACCEPTED_AWAITING_PAYMENT

        assertEquals(ServiceCentreBookingTab.ACCEPTED, accepted.tab)
        assertTrue(accepted.canTransitionTo(ServiceCentreBookingStatus.CONFIRMED))
        assertTrue(accepted.canTransitionTo(ServiceCentreBookingStatus.CANCELLED))
        assertFalse(accepted.canTransitionTo(ServiceCentreBookingStatus.DECLINED))
        assertFalse(accepted.canTransitionTo(ServiceCentreBookingStatus.COMPLETED))
    }

    @Test
    fun confirmedCanOnlyCompleteAndTerminalStatesCannotTransition() {
        assertEquals(ServiceCentreBookingTab.ACCEPTED, ServiceCentreBookingStatus.CONFIRMED.tab)
        assertTrue(ServiceCentreBookingStatus.CONFIRMED.canTransitionTo(ServiceCentreBookingStatus.COMPLETED))

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

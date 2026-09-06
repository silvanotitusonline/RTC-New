package za.org.rtc.community.feature.publicreports.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublicReportValidationTest {
    @Test
    fun titleBoundariesAndTrimming() {
        assertNotNull(PublicReportValidation.title(" ab "))
        assertNotNull(PublicReportValidation.title("abcd"))
        assertNull(PublicReportValidation.title("  Water leak  "))
        assertNotNull(PublicReportValidation.title("x".repeat(81)))
        assertNull(PublicReportValidation.title("x".repeat(80)))
    }

    @Test
    fun descriptionBoundaries() {
        assertNotNull(PublicReportValidation.description("too short"))
        assertNull(PublicReportValidation.description("This describes a leaking pipe near the clinic."))
        assertNotNull(PublicReportValidation.description("x".repeat(2001)))
    }

    @Test
    fun allUrgencyOptionsAreAccepted() {
        listOf(PublicReportUrgency.LOW, PublicReportUrgency.NORMAL, PublicReportUrgency.HIGH, PublicReportUrgency.CRITICAL).forEach {
            assertNull(PublicReportValidation.urgency(it))
        }
        assertNotNull(PublicReportValidation.urgency(null))
        assertNotNull(PublicReportValidation.urgency(PublicReportUrgency.UNKNOWN))
    }

    @Test
    fun criticalNoticeIsStable() {
        assertEquals(
            "Public Reports is not an emergency service. If anyone is in immediate danger, contact the appropriate emergency service first.",
            PublicReportValidation.CRITICAL_NOTICE,
        )
    }

    @Test
    fun manualLocationIsRequired() {
        assertNotNull(PublicReportValidation.manualAddress(null))
        assertNotNull(PublicReportValidation.manualAddress("ab"))
        assertNull(PublicReportValidation.manualAddress("Near the municipal clinic gate"))
    }

    @Test
    fun evidenceMaximumAndException() {
        assertNotNull(PublicReportValidation.evidenceOrException(7, false, null))
        assertNotNull(PublicReportValidation.evidenceOrException(0, false, null))
        assertNotNull(PublicReportValidation.evidenceOrException(0, true, "short"))
        assertNull(PublicReportValidation.evidenceOrException(1, false, null))
        assertNull(PublicReportValidation.evidenceOrException(0, true, "I cannot photograph this site without putting myself at risk."))
        assertNotNull(PublicReportValidation.exceptionReason("x".repeat(301)))
    }

    @Test
    fun identityAndGuidelines() {
        assertNotNull(PublicReportValidation.identity(null))
        assertNull(PublicReportValidation.identity(PublicReportIdentityMode.NAMED))
        assertNull(PublicReportValidation.identity(PublicReportIdentityMode.ANONYMOUS))
        assertNotNull(PublicReportValidation.guidelinesAccepted(false, "1"))
        assertNotNull(PublicReportValidation.guidelinesAccepted(true, ""))
        assertNull(PublicReportValidation.guidelinesAccepted(true, "2026.1"))
    }

    @Test
    fun unknownEnumFallbackDoesNotCrash() {
        assertEquals(PublicReportUrgency.UNKNOWN.label, "Urgency unavailable")
        assertEquals(PublicReportStatus.UNKNOWN.label, "Status unavailable")
    }
}

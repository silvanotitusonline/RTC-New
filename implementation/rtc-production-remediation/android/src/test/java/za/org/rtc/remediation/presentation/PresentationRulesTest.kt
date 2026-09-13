package za.org.rtc.remediation.presentation

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.remediation.model.BookingInput
import za.org.rtc.remediation.model.Report

class PresentationRulesTest {
    @Test fun `short reports remain invalid including whitespace padding`() {
        assertNotNull(PresentationRules.reportBodyError("  Short description  "))
        assertNull(PresentationRules.reportBodyError("Water has leaked all morning."))
        assertFalse(ReportFormState(body = "Water has leaked all morning.").canSubmit)
        assertTrue(ReportFormState(body = "Water has leaked all morning.", category = "INFRASTRUCTURE").canSubmit)
    }

    @Test fun `counter agrees with unicode codepoint validation`() {
        assertEquals(1, PresentationRules.characterCount("😀"))
        assertNotNull(PresentationRules.reportBodyError("😀".repeat(19)))
        assertNull(PresentationRules.reportBodyError("😀".repeat(20)))
    }

    @Test fun `empty dashboard has zero in every segment and total`() {
        assertEquals(DashboardSummary(0, 0, 0), PresentationRules.summary(emptyList()))
        assertEquals(0, PresentationRules.summary(emptyList()).total)
    }

    @Test fun `same reports generate chart and table totals`() {
        val reports = listOf(report("a", "PENDING"), report("b", "WORKING"), report("c", "RESOLVED"), report("d", ""))
        val summary = PresentationRules.summary(reports)
        assertEquals(2, summary.pending)
        assertEquals(1, summary.working)
        assertEquals(1, summary.resolved)
        assertEquals(reports.size, summary.total)
    }

    @Test fun `unavailable distance never becomes zero or mock travel distance`() {
        assertEquals("Distance unavailable", PresentationRules.distance(null))
        assertEquals("Distance unavailable", PresentationRules.distance(-1))
        assertEquals("0 m", PresentationRules.distance(0))
        assertEquals("750 m", PresentationRules.distance(750))
        assertEquals("1.5 km", PresentationRules.distance(1_500))
    }

    @Test fun `price uses rand and integer cents without float drift`() {
        val price = PresentationRules.price(35_099)
        assertTrue(price.contains("R"))
        assertTrue(price.contains("350"))
        assertTrue(price.endsWith("99"))
    }

    @Test fun `timeline uses server timestamp and pending is default status`() {
        val utc = PresentationRules.timestamp("2026-09-13T23:30:00Z", ZoneId.of("UTC"))
        val local = PresentationRules.timestamp("2026-09-13T23:30:00Z", ZoneId.of("Africa/Johannesburg"))
        assertTrue(utc.startsWith("13 ") && utc.endsWith("2026, 23:30"))
        assertTrue(local.startsWith("14 ") && local.endsWith("2026, 01:30"))
        assertEquals("Date unavailable", PresentationRules.timestamp("invalid"))
        assertEquals("Pending", PresentationRules.status(""))
    }

    @Test fun `booking cannot submit past or malformed dates`() {
        val now = Instant.parse("2026-09-13T08:30:00Z")
        assertNotNull(PresentationRules.bookingError(BookingInput("provider", "invalid", ""), now))
        assertNotNull(PresentationRules.bookingError(BookingInput("provider", now.minusSeconds(1).toString(), ""), now))
        assertNull(PresentationRules.bookingError(BookingInput("provider", now.plusSeconds(3600).toString(), ""), now))
    }

    private fun report(id: String, status: String) = Report(
        id = id, body = "Water has leaked all morning.", category = "INFRASTRUCTURE",
        priority = "NORMAL", createdAt = "2026-09-13T08:30:00Z", status = status,
    )
}

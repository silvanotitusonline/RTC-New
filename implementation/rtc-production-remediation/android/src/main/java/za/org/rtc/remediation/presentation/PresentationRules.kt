package za.org.rtc.remediation.presentation

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import za.org.rtc.remediation.model.BookingInput
import za.org.rtc.remediation.model.Report

/** Pure presentation rules; no fake distances, dates, or dashboard data. */
object PresentationRules {
    const val MIN_REPORT_LENGTH = 20
    const val MAX_BODY_LENGTH = 4_000

    fun characterCount(text: String): Int = text.codePointCount(0, text.length)

    fun reportBodyError(body: String): String? = when {
        characterCount(body.trim()) < MIN_REPORT_LENGTH -> "Describe the issue in at least 20 characters."
        characterCount(body.trim()) > MAX_BODY_LENGTH -> "Use no more than 4,000 characters."
        else -> null
    }

    fun price(rateCents: Long): String = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        .format(java.math.BigDecimal.valueOf(rateCents, 2))

    fun distance(meters: Long?): String = when {
        meters == null || meters < 0L -> "Distance unavailable"
        meters < 1_000L -> "$meters m"
        else -> String.format(Locale("en", "ZA"), "%.1f km", meters / 1_000.0)
    }

    fun timestamp(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("en", "ZA"))
                .withZone(zone).format(Instant.parse(value))
        }.getOrDefault("Date unavailable")

    fun status(value: String): String = when (value.uppercase(Locale.ROOT)) {
        "WORKING" -> "In progress"
        "RESOLVED" -> "Resolved"
        else -> "Pending"
    }

    fun summary(reports: List<Report>): DashboardSummary {
        val resolved = reports.count { it.status == "RESOLVED" }
        val working = reports.count { it.status == "WORKING" }
        return DashboardSummary(pending = reports.size - resolved - working, working, resolved)
    }

    fun bookingError(input: BookingInput, now: Instant): String? = when {
        input.providerId.isBlank() -> "Choose a service provider."
        runCatching { Instant.parse(input.startsAt) }.getOrNull()?.isAfter(now) != true ->
            "Choose a future date and time."
        characterCount(input.notes) > 2_000 -> "Use no more than 2,000 characters for booking notes."
        else -> null
    }
}

data class DashboardSummary(val pending: Int, val working: Int, val resolved: Int) {
    val total: Int get() = pending + working + resolved
}

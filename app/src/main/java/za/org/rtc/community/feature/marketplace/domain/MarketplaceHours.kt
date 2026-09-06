package za.org.rtc.community.feature.marketplace.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A weekly Marketplace opening-hours row. Database weekdays use Sunday = 0 through Saturday = 6. */
data class MarketplaceHoursInterval(
    val dayOfWeek: Int,
    val intervalOrder: Int,
    val state: String,
    val opensAt: String?,
    val closesAt: String?,
)

/** A single-date override matching marketplace_location_hour_exceptions. */
data class MarketplaceHoursException(
    val date: String,
    val state: String,
    val opensAt: String?,
    val closesAt: String?,
    val note: String?,
)

sealed interface MarketplaceOpeningStatus {
    data object OpenNow : MarketplaceOpeningStatus
    data object Closed : MarketplaceOpeningStatus
    data object Open24Hours : MarketplaceOpeningStatus
    data object ByAppointment : MarketplaceOpeningStatus
    data object Unavailable : MarketplaceOpeningStatus

    val label: String
        get() = when (this) {
            OpenNow -> "Open Now"
            Closed -> "Closed"
            Open24Hours -> "Open 24/7"
            ByAppointment -> "By Appointment"
            Unavailable -> "Hours on Request"
        }

    val isOpen: Boolean
        get() = this is OpenNow || this is Open24Hours
}

data class DayScheduleInfo(
    val dayName: String,
    val shortDayName: String,
    val dayOfWeekIndex: Int, // 0 = Sun, 1 = Mon ... 6 = Sat
    val isToday: Boolean,
    val summary: String,
    val isOpen: Boolean,
)

/**
 * Calculates only statements that are supported by the stored schedule. Invalid timezone or time
 * values fail closed to [MarketplaceOpeningStatus.Unavailable] rather than claiming availability.
 */
class MarketplaceHoursEvaluator {
    fun evaluate(location: MarketplaceLocation, instant: Instant = Instant.now()): MarketplaceOpeningStatus {
        val zone = runCatching { ZoneId.of(location.timezone) }.getOrNull()
            ?: return MarketplaceOpeningStatus.Unavailable
        val local = instant.atZone(zone)
        val date = local.toLocalDate()
        val time = local.toLocalTime()

        val currentException = location.hourExceptions.firstOrNull { it.date == date.toString() }
        if (currentException != null) return evaluateCurrentException(currentException, time)

        // A previous-date exception replaces that date's recurring schedule, including any
        // overnight carry into the current date. Only an explicit OPEN exception may carry over.
        val previousException = location.hourExceptions.firstOrNull { it.date == date.minusDays(1).toString() }
        if (previousException != null) {
            if (
                previousException.state == STATE_OPEN &&
                isPreviousDayOvernightOpen(previousException.opensAt, previousException.closesAt, time)
            ) {
                return MarketplaceOpeningStatus.OpenNow
            }
        } else {
            val previousDatabaseDay = date.minusDays(1).dayOfWeek.value % 7
            val previousOpenIntervals = location.hours.filter {
                it.dayOfWeek == previousDatabaseDay && it.state == STATE_OPEN
            }
            if (previousOpenIntervals.any { isPreviousDayOvernightOpen(it.opensAt, it.closesAt, time) }) {
                return MarketplaceOpeningStatus.OpenNow
            }
        }

        if (location.hours.isEmpty()) return MarketplaceOpeningStatus.Unavailable

        val databaseDay = local.dayOfWeek.value % 7
        val today = location.hours.filter { it.dayOfWeek == databaseDay }.sortedBy { it.intervalOrder }
        if (today.isEmpty()) return MarketplaceOpeningStatus.Closed

        if (today.any { it.state == STATE_OPEN_24_HOURS }) return MarketplaceOpeningStatus.Open24Hours
        if (today.any { it.state == STATE_APPOINTMENT_ONLY }) return MarketplaceOpeningStatus.ByAppointment

        val openRows = today.filter { it.state == STATE_OPEN }
        if (openRows.isNotEmpty()) {
            val parsed = openRows.map { row ->
                val opens = parseTime(row.opensAt) ?: return MarketplaceOpeningStatus.Unavailable
                val closes = parseTime(row.closesAt) ?: return MarketplaceOpeningStatus.Unavailable
                opens to closes
            }
            if (parsed.any { (opens, closes) -> isCurrentDayOpen(opens, closes, time) }) {
                return MarketplaceOpeningStatus.OpenNow
            }
            return MarketplaceOpeningStatus.Closed
        }

        return if (today.all { it.state == STATE_CLOSED }) {
            MarketplaceOpeningStatus.Closed
        } else {
            MarketplaceOpeningStatus.Unavailable
        }
    }

    fun todaySummary(location: MarketplaceLocation, instant: Instant = Instant.now()): String {
        val zone = runCatching { ZoneId.of(location.timezone) }.getOrNull()
            ?: ZoneId.systemDefault()
        val local = instant.atZone(zone)
        val databaseDay = local.dayOfWeek.value % 7
        val todayIntervals = location.hours.filter { it.dayOfWeek == databaseDay }.sortedBy { it.intervalOrder }

        if (todayIntervals.isEmpty()) {
            return if (location.hours.isEmpty()) "Hours not specified" else "Closed today"
        }

        val first = todayIntervals.first()
        return when (first.state) {
            STATE_OPEN_24_HOURS -> "Open 24 hours"
            STATE_APPOINTMENT_ONLY -> "By appointment only"
            STATE_CLOSED -> "Closed today"
            STATE_OPEN -> {
                val formatted = todayIntervals.filter { it.state == STATE_OPEN }.joinToString(", ") {
                    val opens = it.opensAt?.take(5) ?: ""
                    val closes = it.closesAt?.take(5) ?: ""
                    if (opens.isNotBlank() && closes.isNotBlank()) "$opens – $closes" else opens.ifBlank { closes }
                }
                if (formatted.isNotBlank()) formatted else "Open"
            }
            else -> "Hours on request"
        }
    }

    fun weeklySchedule(location: MarketplaceLocation, instant: Instant = Instant.now()): List<DayScheduleInfo> {
        val zone = runCatching { ZoneId.of(location.timezone) }.getOrNull()
            ?: ZoneId.systemDefault()
        val currentDatabaseDay = instant.atZone(zone).dayOfWeek.value % 7

        val dayNames = listOf(
            Triple(1, "Monday", "Mon"),
            Triple(2, "Tuesday", "Tue"),
            Triple(3, "Wednesday", "Wed"),
            Triple(4, "Thursday", "Thu"),
            Triple(5, "Friday", "Fri"),
            Triple(6, "Saturday", "Sat"),
            Triple(0, "Sunday", "Sun"),
        )

        return dayNames.map { (dayIdx, fullName, shortName) ->
            val intervals = location.hours.filter { it.dayOfWeek == dayIdx }.sortedBy { it.intervalOrder }
            val summary = if (intervals.isEmpty()) {
                if (location.hours.isEmpty()) "Open 08:00 – 17:00" else "Closed"
            } else {
                val first = intervals.first()
                when (first.state) {
                    STATE_OPEN_24_HOURS -> "Open 24 hours"
                    STATE_APPOINTMENT_ONLY -> "By appointment"
                    STATE_CLOSED -> "Closed"
                    STATE_OPEN -> {
                        intervals.filter { it.state == STATE_OPEN }.joinToString(", ") {
                            val opens = it.opensAt?.take(5) ?: "08:00"
                            val closes = it.closesAt?.take(5) ?: "17:00"
                            "$opens – $closes"
                        }.ifBlank { "Open" }
                    }
                    else -> "Closed"
                }
            }
            val isOpen = summary.contains("–") || summary.contains("24 hours") || summary.contains("Open")
            DayScheduleInfo(
                dayName = fullName,
                shortDayName = shortName,
                dayOfWeekIndex = dayIdx,
                isToday = dayIdx == currentDatabaseDay,
                summary = summary,
                isOpen = isOpen,
            )
        }
    }

    private fun evaluateCurrentException(
        exception: MarketplaceHoursException,
        time: LocalTime,
    ): MarketplaceOpeningStatus {
        return when (exception.state) {
            STATE_CLOSED -> MarketplaceOpeningStatus.Closed
            STATE_OPEN_24_HOURS -> MarketplaceOpeningStatus.Open24Hours
            STATE_APPOINTMENT_ONLY -> MarketplaceOpeningStatus.ByAppointment
            STATE_OPEN -> {
                val opens = parseTime(exception.opensAt) ?: return MarketplaceOpeningStatus.Unavailable
                val closes = parseTime(exception.closesAt) ?: return MarketplaceOpeningStatus.Unavailable
                if (isCurrentDayOpen(opens, closes, time)) MarketplaceOpeningStatus.OpenNow
                else MarketplaceOpeningStatus.Closed
            }
            else -> MarketplaceOpeningStatus.Unavailable
        }
    }

    private fun isCurrentDayOpen(opens: LocalTime, closes: LocalTime, now: LocalTime): Boolean =
        if (opens < closes) now >= opens && now < closes
        else if (opens > closes) now >= opens
        else false

    private fun isPreviousDayOvernightOpen(opensText: String?, closesText: String?, now: LocalTime): Boolean {
        val opens = parseTime(opensText) ?: return false
        val closes = parseTime(closesText) ?: return false
        return opens > closes && now < closes
    }

    private fun parseTime(value: String?): LocalTime? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private companion object {
        const val STATE_OPEN = "OPEN"
        const val STATE_CLOSED = "CLOSED"
        const val STATE_OPEN_24_HOURS = "OPEN_24_HOURS"
        const val STATE_APPOINTMENT_ONLY = "APPOINTMENT_ONLY"
    }
}

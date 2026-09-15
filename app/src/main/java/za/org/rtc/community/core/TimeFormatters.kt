package za.org.rtc.community.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Standardized date and time formatting across the entire RTC Community application.
 * Converts raw timestamps, ISO strings, epoch milliseconds, and date-time objects
 * into user-friendly relative labels (e.g. 'Just now', '5m ago', '2h ago', 'Yesterday', '3d ago', 'Sep 15', 'Sep 15, 2025').
 */
object TimeFormatters {
    private val SAME_YEAR_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val OTHER_YEAR_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val FULL_DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

    /**
     * Standardizes any timestamp to a concise relative format.
     */
    fun formatRelativeTime(
        raw: Any?,
        clock: Clock = Clock.systemUTC(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (raw == null) return ""
        val instant: Instant = when (raw) {
            is Instant -> raw
            is Long -> if (raw > 10_000_000_000L) Instant.ofEpochMilli(raw) else Instant.ofEpochSecond(raw)
            is LocalDateTime -> raw.atZone(zoneId).toInstant()
            is LocalDate -> raw.atStartOfDay(zoneId).toInstant()
            is String -> parseStringToInstant(raw, zoneId) ?: return raw
            else -> return raw.toString()
        }

        val now = clock.instant()
        val duration = Duration.between(instant, now)
        val seconds = duration.seconds

        if (seconds < 0) {
            return "Just now"
        }
        if (seconds < 60) {
            return "Just now"
        }

        val eventDate = instant.atZone(zoneId).toLocalDate()
        val currentDate = now.atZone(zoneId).toLocalDate()

        if (eventDate == currentDate) {
            val minutes = duration.toMinutes()
            if (minutes < 60) {
                return "${minutes}m ago"
            }
            val hours = duration.toHours()
            return "${hours}h ago"
        }

        if (eventDate == currentDate.minusDays(1)) {
            return "Yesterday"
        }
        val days = Duration.between(eventDate.atStartOfDay(), currentDate.atStartOfDay()).toDays()
        if (days in 2..6) {
            return "${days}d ago"
        }

        return if (eventDate.year == currentDate.year) {
            SAME_YEAR_DATE.format(eventDate)
        } else {
            OTHER_YEAR_DATE.format(eventDate)
        }
    }

    /**
     * Formats an instant or timestamp to a readable full date-time string.
     */
    fun formatFullDateTime(
        raw: Any?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (raw == null) return ""
        val instant: Instant = when (raw) {
            is Instant -> raw
            is Long -> if (raw > 10_000_000_000L) Instant.ofEpochMilli(raw) else Instant.ofEpochSecond(raw)
            is LocalDateTime -> raw.atZone(zoneId).toInstant()
            is LocalDate -> raw.atStartOfDay(zoneId).toInstant()
            is String -> parseStringToInstant(raw, zoneId) ?: return raw
            else -> return raw.toString()
        }
        return FULL_DATE_TIME.withZone(zoneId).format(instant)
    }

    fun parseToEpochMillis(
        raw: Any?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (raw == null) return 0L
        return when (raw) {
            is Instant -> raw.toEpochMilli()
            is Long -> if (raw > 10_000_000_000L) raw else raw * 1000L
            is LocalDateTime -> raw.atZone(zoneId).toInstant().toEpochMilli()
            is LocalDate -> raw.atStartOfDay(zoneId).toInstant().toEpochMilli()
            is String -> parseStringToInstant(raw, zoneId)?.toEpochMilli() ?: raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    fun parseStringToInstant(raw: String, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        try {
            return Instant.parse(trimmed)
        } catch (_: DateTimeParseException) {}

        trimmed.toLongOrNull()?.let { num ->
            return if (num > 10_000_000_000L) Instant.ofEpochMilli(num) else Instant.ofEpochSecond(num)
        }

        try {
            val ldt = LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return ldt.atZone(zoneId).toInstant()
        } catch (_: DateTimeParseException) {}

        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
            val ldt = LocalDateTime.parse(trimmed, formatter)
            return ldt.atZone(zoneId).toInstant()
        } catch (_: DateTimeParseException) {}

        try {
            val ld = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
            return ld.atStartOfDay(zoneId).toInstant()
        } catch (_: DateTimeParseException) {}

        return null
    }
}

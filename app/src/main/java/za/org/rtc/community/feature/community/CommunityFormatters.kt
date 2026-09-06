package za.org.rtc.community.feature.community

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAME_YEAR_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val OTHER_YEAR_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

fun relativeTimeLabel(
    timestamp: String,
    clock: Clock = Clock.systemUTC(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val instant = runCatching { Instant.parse(timestamp) }.getOrNull() ?: return timestamp
    val now = clock.instant()
    if (instant.isAfter(now)) return "Just now"

    val eventDate = instant.atZone(zoneId).toLocalDate()
    val currentDate = now.atZone(zoneId).toLocalDate()
    if (eventDate == currentDate.minusDays(1)) return "Yesterday"

    val elapsed = Duration.between(instant, now)
    if (elapsed.seconds < 60) return "Just now"
    if (elapsed.toMinutes() < 60) return "${elapsed.toMinutes()} min ago"
    if (elapsed.toHours() < 24) return "${elapsed.toHours()} hr ago"

    return if (eventDate.year == currentDate.year) {
        SAME_YEAR_DATE.format(eventDate)
    } else {
        OTHER_YEAR_DATE.format(eventDate)
    }
}

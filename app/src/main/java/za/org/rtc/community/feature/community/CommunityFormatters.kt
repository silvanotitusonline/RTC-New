package za.org.rtc.community.feature.community

import java.time.Clock
import java.time.ZoneId
import za.org.rtc.community.core.TimeFormatters

fun relativeTimeLabel(
    timestamp: String,
    clock: Clock = Clock.systemUTC(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = TimeFormatters.formatRelativeTime(timestamp, clock, zoneId)


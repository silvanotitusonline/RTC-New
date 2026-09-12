package za.org.rtc.community.feature.events.domain

import java.time.Instant
import java.time.ZoneId

object CommunityEventValidation {
    fun draft(
        title: String,
        description: String,
        startsAt: Instant?,
        endsAt: Instant?,
        timeZone: String,
        locality: String?,
        venueLabel: String,
    ): String? = when {
        title.trim().length !in 3..180 -> "Enter an Event title between 3 and 180 characters."
        description.trim().length !in 3..10_000 -> "Describe the Event in at least 3 characters."
        startsAt == null || endsAt == null || !endsAt.isAfter(startsAt) -> "Choose an Event end time after its start time."
        timeZone.trim().isEmpty() || runCatching { ZoneId.of(timeZone.trim()) }.isFailure -> "Choose a valid Event time zone."
        locality != null && locality.trim().isNotEmpty() && locality.trim().length !in 2..120 -> "Enter a valid locality."
        venueLabel.trim().length !in 2..180 -> "Enter a valid venue or meeting place."
        else -> null
    }

    fun cancellationReason(value: String): String? =
        if (value.trim().length in 3..500) null else "Provide a cancellation reason between 3 and 500 characters."
}

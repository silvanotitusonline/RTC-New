package za.org.rtc.community.feature.events.domain

import java.time.Instant

enum class CommunityEventState { DRAFT, PUBLISHED, CANCELLED }

data class CommunityEvent(
    val id: String,
    val title: String,
    val description: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val timeZone: String = "Africa/Johannesburg",
    val locality: String? = null,
    val venueLabel: String,
    val isLocal: Boolean = true,
    val state: CommunityEventState = CommunityEventState.PUBLISHED,
    val cancellationReason: String? = null,
    val publishedAt: Instant? = null,
    val category: String = "Civic",
    val rsvpCount: Int = 0,
    val isRsvped: Boolean = false,
)

data class CommunityEventDraft(
    val id: String? = null,
    val title: String,
    val description: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val timeZone: String = "Africa/Johannesburg",
    val locality: String? = null,
    val venueLabel: String,
    val isLocal: Boolean = true,
    val category: String = "Civic",
)

interface CommunityEventsRepository {
    suspend fun page(locality: String?, offset: Int = 0, limit: Int = 20): Result<List<CommunityEvent>>
    suspend fun adminPage(offset: Int = 0, limit: Int = 50): Result<List<CommunityEvent>>
    suspend fun upsert(draft: CommunityEventDraft): Result<String>
    suspend fun publish(eventId: String): Result<Unit>
    suspend fun cancel(eventId: String, reason: String): Result<Unit>
    suspend fun delete(eventId: String): Result<Unit>
    suspend fun toggleRsvp(eventId: String): Result<Unit>
}

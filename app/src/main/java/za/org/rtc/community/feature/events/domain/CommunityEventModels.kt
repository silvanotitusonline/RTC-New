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

val SampleCommunityEvents = listOf(
    CommunityEvent(
        id = "event-001",
        title = "Ward 4 Municipal Townhall Meeting",
        description = "Annual Ward 4 budget allocation review and open resident forum with municipal councillors regarding service delivery, water supply, and local roads.",
        startsAt = Instant.parse("2026-09-09T18:00:00Z"),
        endsAt = Instant.parse("2026-09-09T20:00:00Z"),
        locality = "RTC Ward 4",
        venueLabel = "RTC Community Hall, Main Street",
        category = "Civic",
        rsvpCount = 34,
        isRsvped = false,
        state = CommunityEventState.PUBLISHED,
    ),
    CommunityEvent(
        id = "event-002",
        title = "Spring Parks & River Clean-Up Drive",
        description = "Join fellow residents for a community park and riverbank restoration morning. Gloves, trash bags, and light refreshments will be provided.",
        startsAt = Instant.parse("2026-09-12T08:30:00Z"),
        endsAt = Instant.parse("2026-09-12T12:00:00Z"),
        locality = "Central Ward",
        venueLabel = "Civic Park & Riverside Promenade",
        category = "Environment",
        rsvpCount = 52,
        isRsvped = true,
        state = CommunityEventState.PUBLISHED,
    ),
    CommunityEvent(
        id = "event-003",
        title = "Youth Soccer & Athletics Tournament",
        description = "Inter-neighborhood youth athletics and soccer matches, wellness stalls, and sports coaching for age groups 10-18.",
        startsAt = Instant.parse("2026-09-16T14:00:00Z"),
        endsAt = Instant.parse("2026-09-16T17:30:00Z"),
        locality = "RTC West",
        venueLabel = "Municipal Sports Complex",
        category = "Youth",
        rsvpCount = 41,
        isRsvped = false,
        state = CommunityEventState.PUBLISHED,
    ),
    CommunityEvent(
        id = "event-004",
        title = "Community Health & Wellness Screening Clinic",
        description = "Free health screenings for blood pressure, glucose, and eye care provided by municipal health partners.",
        startsAt = Instant.parse("2026-09-21T09:00:00Z"),
        endsAt = Instant.parse("2026-09-21T15:00:00Z"),
        locality = "Central Ward",
        venueLabel = "Central Library Auditorium",
        category = "Safety",
        rsvpCount = 27,
        isRsvped = false,
        state = CommunityEventState.PUBLISHED,
    ),
    CommunityEvent(
        id = "event-005",
        title = "Heritage Day Cultural Food & Arts Market",
        description = "Celebrate cultural heritage with local food vendors, traditional live music, crafts market, and family activities.",
        startsAt = Instant.parse("2026-09-24T10:00:00Z"),
        endsAt = Instant.parse("2026-09-24T18:00:00Z"),
        locality = "Town Square",
        venueLabel = "Tsantsabane Heritage Plaza",
        category = "Culture",
        rsvpCount = 78,
        isRsvped = true,
        state = CommunityEventState.PUBLISHED,
    ),
    CommunityEvent(
        id = "event-006",
        title = "Road Safety & Neighborhood Watch Workshop",
        description = "Community policing forum briefing, street lighting updates, and emergency hotline training for residents.",
        startsAt = Instant.parse("2026-09-28T17:30:00Z"),
        endsAt = Instant.parse("2026-09-28T19:30:00Z"),
        locality = "RTC Ward 2",
        venueLabel = "RTC Safety Centre",
        category = "Safety",
        rsvpCount = 19,
        isRsvped = false,
        state = CommunityEventState.PUBLISHED,
    ),
    CommunityEvent(
        id = "event-007",
        title = "Rooftop Solar & Energy Resilience Briefing",
        description = "Public consultation on municipal solar feed-in tariffs and community energy backup grants.",
        startsAt = Instant.parse("2026-09-30T16:00:00Z"),
        endsAt = Instant.parse("2026-09-30T18:00:00Z"),
        locality = "RTC Central",
        venueLabel = "Municipal Council Chamber",
        category = "Civic",
        rsvpCount = 0,
        isRsvped = false,
        state = CommunityEventState.DRAFT,
    )
)


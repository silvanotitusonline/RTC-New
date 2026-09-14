package za.org.rtc.community.feature.events.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.events.domain.CommunityEvent
import za.org.rtc.community.feature.events.domain.CommunityEventDraft
import za.org.rtc.community.feature.events.domain.CommunityEventState
import za.org.rtc.community.feature.events.domain.CommunityEventsRepository

@Singleton
class SupabaseCommunityEventsRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : CommunityEventsRepository {
    private val _eventsFlow = MutableStateFlow<List<CommunityEvent>>(emptyList())
    val eventsFlow: StateFlow<List<CommunityEvent>> = _eventsFlow.asStateFlow()

    override suspend fun page(locality: String?, offset: Int, limit: Int): Result<List<CommunityEvent>> = runCatching {
        val remoteList = supabase.postgrest.rpc("community_events_page", buildJsonObject {
            put("p_limit", limit.coerceIn(1, 50))
            put("p_offset", offset.coerceIn(0, 1000))
            locality?.trim()?.takeIf(String::isNotBlank)?.let { put("p_locality", it) }
        }).decodeSingle<JsonArray>().map { it as JsonObject }.map { event -> event.toCommunityEvent() }

        if (offset == 0) {
            _eventsFlow.value = remoteList
        }
        remoteList
    }

    override suspend fun adminPage(offset: Int, limit: Int): Result<List<CommunityEvent>> = runCatching {
        val remoteList = supabase.postgrest.rpc("community_events_admin_page", buildJsonObject {
            put("p_limit", limit.coerceIn(1, 100))
            put("p_offset", offset.coerceIn(0, 1000))
        }).decodeSingle<JsonArray>().map { it as JsonObject }.map { event -> event.toCommunityEvent() }

        if (offset == 0) {
            _eventsFlow.value = remoteList
        }
        remoteList
    }

    override suspend fun upsert(draft: CommunityEventDraft): Result<String> = runCatching {
        val eventId = supabase.postgrest.rpc("upsert_community_event", buildJsonObject {
            draft.id?.let { put("p_event_id", it) }
            put("p_title", draft.title.trim())
            put("p_description", draft.description.trim())
            put("p_starts_at", draft.startsAt.toString())
            put("p_ends_at", draft.endsAt.toString())
            put("p_time_zone", draft.timeZone.trim())
            draft.locality?.trim()?.takeIf(String::isNotBlank)?.let { put("p_locality", it) }
            put("p_venue_label", draft.venueLabel.trim())
            put("p_is_local", draft.isLocal)
        }).decodeSingle<String>()

        val existing = _eventsFlow.value.firstOrNull { it.id == eventId }
        val confirmed = CommunityEvent(
            id = eventId,
            title = draft.title.trim(),
            description = draft.description.trim(),
            startsAt = draft.startsAt,
            endsAt = draft.endsAt,
            timeZone = draft.timeZone.trim(),
            locality = draft.locality?.trim()?.takeIf(String::isNotBlank),
            venueLabel = draft.venueLabel.trim(),
            isLocal = draft.isLocal,
            category = draft.category,
            state = existing?.state ?: CommunityEventState.DRAFT,
            cancellationReason = existing?.cancellationReason,
            publishedAt = existing?.publishedAt,
            rsvpCount = existing?.rsvpCount ?: 0,
            isRsvped = existing?.isRsvped ?: false,
        )
        _eventsFlow.value = listOf(confirmed) + _eventsFlow.value.filterNot { it.id == eventId }
        eventId
    }

    override suspend fun publish(eventId: String): Result<Unit> = runCatching {
        require(eventId.isNotBlank()) { "Choose an Event." }
        supabase.postgrest.rpc("publish_community_event", buildJsonObject { put("p_event_id", eventId) })
        _eventsFlow.value = _eventsFlow.value.map { event ->
            if (event.id == eventId) {
                event.copy(state = CommunityEventState.PUBLISHED)
            } else {
                event
            }
        }
    }

    override suspend fun cancel(eventId: String, reason: String): Result<Unit> = runCatching {
        require(eventId.isNotBlank()) { "Choose an Event." }
        val cleanReason = reason.trim()
        require(cleanReason.length in 3..500) { "Provide a cancellation reason between 3 and 500 characters." }
        supabase.postgrest.rpc("cancel_community_event", buildJsonObject {
            put("p_event_id", eventId)
            put("p_reason", cleanReason)
        })
        _eventsFlow.value = _eventsFlow.value.map { event ->
            if (event.id == eventId) {
                event.copy(state = CommunityEventState.CANCELLED, cancellationReason = cleanReason)
            } else {
                event
            }
        }
    }

    override suspend fun delete(eventId: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Community Event deletion is not exposed by the production backend. Cancel the Event instead."),
    )

    override suspend fun toggleRsvp(eventId: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Community Event RSVP is not exposed by the production backend."),
    )

    private fun JsonObject.toCommunityEvent(): CommunityEvent = CommunityEvent(
        id = requiredString("id"),
        title = requiredString("title"),
        description = requiredString("description"),
        startsAt = Instant.parse(requiredString("starts_at")),
        endsAt = Instant.parse(requiredString("ends_at")),
        timeZone = requiredString("time_zone"),
        locality = optionalString("locality"),
        venueLabel = requiredString("venue_label"),
        isLocal = get("is_local")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
        state = optionalString("state")?.let { runCatching { CommunityEventState.valueOf(it) }.getOrNull() }
            ?: CommunityEventState.PUBLISHED,
        cancellationReason = optionalString("cancellation_reason"),
        publishedAt = optionalString("published_at")?.let(Instant::parse),
        category = optionalString("category") ?: "Civic",
    )

    private fun JsonObject.requiredString(key: String): String =
        get(key)?.jsonPrimitive?.contentOrNull ?: error("Missing Community Event field: $key")

    private fun JsonObject.optionalString(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull
}

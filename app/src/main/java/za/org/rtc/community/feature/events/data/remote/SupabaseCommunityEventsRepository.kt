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
import za.org.rtc.community.feature.events.domain.SampleCommunityEvents

@Singleton
class SupabaseCommunityEventsRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : CommunityEventsRepository {
    private val _eventsFlow = MutableStateFlow<List<CommunityEvent>>(SampleCommunityEvents)
    val eventsFlow: StateFlow<List<CommunityEvent>> = _eventsFlow.asStateFlow()

    override suspend fun page(locality: String?, offset: Int, limit: Int): Result<List<CommunityEvent>> = runCatching {
        val remoteList = runCatching {
            supabase.postgrest.rpc("community_events_page", buildJsonObject {
                put("p_limit", limit.coerceIn(1, 50))
                put("p_offset", offset.coerceAtMost(1000))
                locality?.trim()?.takeIf(String::isNotBlank)?.let { put("p_locality", it) }
            }).decodeSingle<JsonArray>().map { it as JsonObject }.map { event -> event.toCommunityEvent() }
        }.getOrNull()

        if (!remoteList.isNullOrEmpty()) {
            remoteList
        } else {
            val filterLocality = locality?.trim()?.lowercase()
            _eventsFlow.value.filter { event ->
                event.state == CommunityEventState.PUBLISHED &&
                    (filterLocality.isNullOrBlank() || event.locality?.lowercase()?.contains(filterLocality) == true)
            }
        }
    }

    override suspend fun adminPage(offset: Int, limit: Int): Result<List<CommunityEvent>> = runCatching {
        val remoteList = runCatching {
            supabase.postgrest.rpc("community_events_admin_page", buildJsonObject {
                put("p_limit", limit.coerceIn(1, 100))
                put("p_offset", offset.coerceAtMost(1000))
            }).decodeSingle<JsonArray>().map { it as JsonObject }.map { event -> event.toCommunityEvent() }
        }.getOrNull()

        if (!remoteList.isNullOrEmpty()) {
            remoteList
        } else {
            _eventsFlow.value
        }
    }

    override suspend fun upsert(draft: CommunityEventDraft): Result<String> = runCatching {
        val newId = draft.id ?: "event-${System.currentTimeMillis()}"
        val existing = _eventsFlow.value.find { it.id == newId }
        val updatedEvent = CommunityEvent(
            id = newId,
            title = draft.title,
            description = draft.description,
            startsAt = draft.startsAt,
            endsAt = draft.endsAt,
            timeZone = draft.timeZone,
            locality = draft.locality,
            venueLabel = draft.venueLabel,
            isLocal = draft.isLocal,
            category = draft.category,
            state = existing?.state ?: CommunityEventState.DRAFT,
            rsvpCount = existing?.rsvpCount ?: 0,
            isRsvped = existing?.isRsvped ?: false,
        )
        _eventsFlow.value = listOf(updatedEvent) + _eventsFlow.value.filterNot { it.id == newId }

        runCatching {
            supabase.postgrest.rpc("upsert_community_event", buildJsonObject {
                put("p_event_id", draft.id)
                put("p_title", draft.title.trim())
                put("p_description", draft.description.trim())
                put("p_starts_at", draft.startsAt.toString())
                put("p_ends_at", draft.endsAt.toString())
                put("p_time_zone", draft.timeZone.trim())
                draft.locality?.trim()?.takeIf(String::isNotBlank)?.let { put("p_locality", it) }
                put("p_venue_label", draft.venueLabel.trim())
                put("p_is_local", draft.isLocal)
            })
        }
        newId
    }

    override suspend fun publish(eventId: String): Result<Unit> = runCatching {
        require(eventId.isNotBlank()) { "Choose an Event." }
        _eventsFlow.value = _eventsFlow.value.map {
            if (it.id == eventId) it.copy(state = CommunityEventState.PUBLISHED, publishedAt = Instant.now()) else it
        }
        runCatching {
            supabase.postgrest.rpc("publish_community_event", buildJsonObject { put("p_event_id", eventId) })
        }
        Unit
    }

    override suspend fun cancel(eventId: String, reason: String): Result<Unit> = runCatching {
        require(eventId.isNotBlank()) { "Choose an Event." }
        _eventsFlow.value = _eventsFlow.value.map {
            if (it.id == eventId) it.copy(state = CommunityEventState.CANCELLED, cancellationReason = reason) else it
        }
        runCatching {
            supabase.postgrest.rpc("cancel_community_event", buildJsonObject {
                put("p_event_id", eventId)
                put("p_reason", reason.trim())
            })
        }
        Unit
    }

    override suspend fun delete(eventId: String): Result<Unit> = runCatching {
        require(eventId.isNotBlank()) { "Choose an Event." }
        _eventsFlow.value = _eventsFlow.value.filterNot { it.id == eventId }
        Unit
    }

    override suspend fun toggleRsvp(eventId: String): Result<Unit> = runCatching {
        require(eventId.isNotBlank()) { "Choose an Event." }
        _eventsFlow.value = _eventsFlow.value.map { event ->
            if (event.id == eventId) {
                val newRsvped = !event.isRsvped
                val newCount = if (newRsvped) event.rsvpCount + 1 else (event.rsvpCount - 1).coerceAtLeast(0)
                event.copy(isRsvped = newRsvped, rsvpCount = newCount)
            } else event
        }
        Unit
    }

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


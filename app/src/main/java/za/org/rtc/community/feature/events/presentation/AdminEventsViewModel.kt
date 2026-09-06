package za.org.rtc.community.feature.events.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.events.domain.CommunityEvent
import za.org.rtc.community.feature.events.domain.CommunityEventDraft
import za.org.rtc.community.feature.events.domain.CommunityEventsRepository

data class AdminEventsUiState(
    val events: List<CommunityEvent> = emptyList(),
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AdminEventsViewModel @Inject constructor(
    private val repository: CommunityEventsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminEventsUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            repository.adminPage()
                .onSuccess { events -> _state.value = _state.value.copy(events = events, loading = false) }
                .onFailure { error -> _state.value = _state.value.copy(loading = false, message = SafeUiError.generic(error, "Administrator Events could not be loaded.")) }
        }
    }

    fun save(
        id: String?,
        title: String,
        description: String,
        startsAt: String,
        endsAt: String,
        locality: String?,
        venueLabel: String,
        isLocal: Boolean,
        publishAfterSave: Boolean,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, message = null)
            runCatching {
                val start = Instant.parse(startsAt)
                val end = Instant.parse(endsAt)
                require(end.isAfter(start)) { "Event end time must be after the start time." }
                CommunityEventDraft(
                    id = id,
                    title = title.trim(),
                    description = description.trim(),
                    startsAt = start,
                    endsAt = end,
                    locality = locality?.trim()?.takeIf(String::isNotEmpty),
                    venueLabel = venueLabel.trim(),
                    isLocal = isLocal,
                )
            }.fold(
                onSuccess = { draft ->
                    repository.upsert(draft)
                        .onSuccess { eventId ->
                            if (publishAfterSave) {
                                repository.publish(eventId)
                                    .onFailure { error ->
                                        _state.value = _state.value.copy(submitting = false, message = SafeUiError.generic(error, "Event saved but could not be published."))
                                        return@onSuccess
                                    }
                            }
                            _state.value = _state.value.copy(submitting = false, message = if (publishAfterSave) "Event saved and published." else "Event draft saved.")
                            load()
                        }
                        .onFailure { error -> _state.value = _state.value.copy(submitting = false, message = SafeUiError.generic(error, "Event could not be saved.")) }
                },
                onFailure = { error -> _state.value = _state.value.copy(submitting = false, message = error.message ?: "Review the Event dates and fields.") },
            )
        }
    }

    fun publish(eventId: String) {
        mutate(eventId, "Event published.") { repository.publish(it) }
    }

    fun cancel(eventId: String, reason: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, message = null)
            repository.cancel(eventId, reason)
                .onSuccess { _state.value = _state.value.copy(submitting = false, message = "Event cancelled."); load() }
                .onFailure { error -> _state.value = _state.value.copy(submitting = false, message = SafeUiError.generic(error, "Event could not be cancelled.")) }
        }
    }

    fun delete(eventId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, message = null)
            repository.delete(eventId)
                .onSuccess { _state.value = _state.value.copy(submitting = false, message = "Event deleted."); load() }
                .onFailure { error -> _state.value = _state.value.copy(submitting = false, message = SafeUiError.generic(error, "Event could not be deleted.")) }
        }
    }

    private fun mutate(eventId: String, successMessage: String, action: suspend (String) -> Result<Unit>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, message = null)
            action(eventId)
                .onSuccess { _state.value = _state.value.copy(submitting = false, message = successMessage); load() }
                .onFailure { error -> _state.value = _state.value.copy(submitting = false, message = SafeUiError.generic(error, "Event could not be updated.")) }
        }
    }
}

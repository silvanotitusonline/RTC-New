package za.org.rtc.community.feature.events.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.events.domain.CommunityEvent
import za.org.rtc.community.feature.events.domain.CommunityEventsRepository

data class CommunityEventsUiState(
    val events: List<CommunityEvent> = emptyList(),
    val locality: String = "",
    val loading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class CommunityEventsViewModel @Inject constructor(
    private val repository: CommunityEventsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityEventsUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(loading = true, message = null)
            repository.page(current.locality.trim().takeIf(String::isNotBlank))
                .onSuccess { events -> _state.value = _state.value.copy(events = events, loading = false) }
                .onFailure { error -> _state.value = _state.value.copy(
                    loading = false,
                    message = SafeUiError.serviceCentre(error, "Community Events could not be loaded."),
                ) }
        }
    }

    fun updateLocality(value: String) {
        _state.value = _state.value.copy(locality = value.take(120))
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

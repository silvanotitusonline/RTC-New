package za.org.rtc.community.feature.inbox.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.inbox.domain.ResidentInboxItem
import za.org.rtc.community.feature.inbox.domain.ResidentInboxRepository
import za.org.rtc.community.feature.inbox.domain.ResidentInboxTab

data class ResidentInboxUiState(
    val tab: ResidentInboxTab = ResidentInboxTab.UPDATES,
    val items: List<ResidentInboxItem> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ResidentInboxViewModel @Inject constructor(
    private val repository: ResidentInboxRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialTab: ResidentInboxTab = savedStateHandle.get<String>("tab")
        ?.lowercase()
        ?.let { tabArg ->
            if (tabArg == "messages") ResidentInboxTab.MESSAGES else ResidentInboxTab.UPDATES
        } ?: ResidentInboxTab.UPDATES

    private val _state = MutableStateFlow(ResidentInboxUiState(tab = initialTab))
    val state = _state.asStateFlow()

    init {
        load(initialTab)
    }

    fun load(tab: ResidentInboxTab = _state.value.tab) {
        viewModelScope.launch {
            _state.value = _state.value.copy(tab = tab, loading = true, message = null)
            repository.page(tab).onSuccess { items ->
                _state.value = _state.value.copy(items = items, loading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = SafeUiError.serviceCentre(error, "Your Inbox could not be loaded."),
                )
            }
        }
    }

    fun markRead(item: ResidentInboxItem) {
        if (item.isRead) return
        viewModelScope.launch {
            repository.markRead(item).onSuccess {
                _state.value = _state.value.copy(
                    items = _state.value.items.map { current ->
                        if (current.sourceType == item.sourceType && current.sourceId == item.sourceId) current.copy(isRead = true) else current
                    },
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(message = SafeUiError.serviceCentre(error, "The Inbox item could not be marked read."))
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val unreadItems = _state.value.items.filter { !it.isRead }
            unreadItems.forEach { item ->
                repository.markRead(item)
            }
            _state.value = _state.value.copy(
                items = _state.value.items.map { it.copy(isRead = true) }
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

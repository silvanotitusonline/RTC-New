package za.org.rtc.community.feature.explore

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.core.BaseViewModel
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.feature.community.CommunityRepository

/**
 * Drives Explore search: ranked post search plus hashtag and mention autocomplete.
 *
 * All work goes through [CommunityRepository], which already owns cursor
 * handling, signed-URL refresh and offline fallback.
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val repository: CommunityRepository,
) : BaseViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CommunityPost>>(emptyList())
    val results = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _hashtagSuggestions = MutableStateFlow<List<String>>(emptyList())
    val hashtagSuggestions = _hashtagSuggestions.asStateFlow()

    private val _mentionSuggestions = MutableStateFlow<List<String>>(emptyList())
    val mentionSuggestions = _mentionSuggestions.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    fun onQueryChanged(value: String) {
        _query.value = value
        val trimmed = value.trim()

        if (trimmed.length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            _results.value = emptyList()
            _isSearching.value = false
            _message.value = null
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            performSearch(trimmed)
        }
    }

    fun retry() {
        val trimmed = _query.value.trim()
        if (trimmed.length >= MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(trimmed) }
        }
    }

    fun refreshSuggestions(prefix: String) {
        val clean = prefix.trim()
        if (clean.length < MIN_SUGGESTION_LENGTH) {
            _hashtagSuggestions.value = emptyList()
            _mentionSuggestions.value = emptyList()
            return
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MILLIS)
            repository.getHashtagAutocomplete(clean)
                .onSuccess { _hashtagSuggestions.value = it }
            repository.getMentionAutocomplete(clean)
                .onSuccess { _mentionSuggestions.value = it }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private suspend fun performSearch(term: String) {
        _isSearching.value = true
        _message.value = null
        repository.searchPosts(query = term)
            .onSuccess { posts ->
                _results.value = posts
                _isSearching.value = false
                if (posts.isEmpty()) _message.value = "No matches for \"" + term + "\"."
            }
            .onFailure {
                _results.value = emptyList()
                _isSearching.value = false
                _message.value = "Search is unavailable right now. Pull to retry."
            }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MIN_SUGGESTION_LENGTH = 1
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val SUGGEST_DEBOUNCE_MILLIS = 150L
    }
}

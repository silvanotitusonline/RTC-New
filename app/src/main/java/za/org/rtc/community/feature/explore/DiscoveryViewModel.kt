
package za.org.rtc.community.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import za.org.rtc.community.core.BaseViewModel
import za.org.rtc.community.data.RtcRepository

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val repository: RtcRepository
) : BaseViewModel() {

    private val _trendingTags = MutableStateFlow<List<String>>(emptyList())
    val trendingTags = _trendingTags.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    init {
        refreshTrending()
    }

    fun refreshTrending() {
        launchSafe {
            repository.getTrendingHashtags().onSuccess { tags ->
                _trendingTags.value = tags
            }
        }
    }

    fun performSearch(query: String) {
        launchSafe {
            repository.searchCommunity(query).onSuccess { results ->
                _searchResults.value = results
            }
        }
    }
}

data class SearchResult(val id: String, val body: String, val username: String, val rank: Float)

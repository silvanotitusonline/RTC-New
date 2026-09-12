
package za.org.rtc.community.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.data.RtcRepository

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val repository: RtcRepository,
) : ViewModel() {
    private val _feedState = MutableStateFlow(CommunityFeedState(initialLoading = true))
    val feedState = _feedState.asStateFlow()

    private val _detailState = MutableStateFlow(CommunityDetailState())
    val detailState = _detailState.asStateFlow()

    init {
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _feedState.update { it.copy(initialLoading = true) }
            repository.getCommunityPosts()
                .onSuccess { posts ->
                    _feedState.update { it.copy(posts = posts, initialLoading = false) }
                }
                .onFailure { error ->
                    _feedState.update { it.copy(error = error.message, initialLoading = false) }
                }
        }
    }

    fun loadPostDetail(postId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true) }
            repository.getPostDetail(postId)
                .onSuccess { post ->
                    _detailState.update { it.copy(post = post, isLoading = false) }
                }
                .onFailure { error ->
                    _detailState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch {
            repository.toggleLike(postId)
                .onSuccess { updatedPost ->
                    _feedState.update { state ->
                        state.copy(posts = state.posts.map { if (it.id == postId) updatedPost else it })
                    }
                }
        }
    }
}

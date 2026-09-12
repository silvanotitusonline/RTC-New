
package za.org.rtc.community.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import za.org.rtc.community.core.BaseViewModel
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.data.RtcRepository

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val repository: RtcRepository,
) : BaseViewModel() {
    private val _feedState = MutableStateFlow(CommunityFeedState(initialLoading = true))
    val feedState = _feedState.asStateFlow()
    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications = _unreadNotifications.asStateFlow()

    private var currentCursor: String? = null

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        launchSafe {
            _feedState.update { it.copy(isLoadingMore = true) }
            repository.getCommunityPosts(cursor = currentCursor)
                .onSuccess { newPosts ->
                    currentCursor = newPosts.lastOrNull()?.createdAt
                    _feedState.update { state -> 
                        state.copy(
                            posts = state.posts + newPosts, 
                            initialLoading = false, 
                            isLoadingMore = false 
                        ) 
                    }
                }
                .onFailure { error ->
                    _feedState.update { it.copy(error = error.message, isLoadingMore = false) }
                }
        }
    }

    fun toggleLike(postId: String) {
        val previousPosts = _feedState.value.posts
        
        // OPTIMISTIC UPDATE
        _feedState.update { state ->
            state.copy(posts = state.posts.map { 
                if (it.id == postId) {
                    it.copy(isLiked = !it.isLiked, likeCount = if (it.isLiked) it.likeCount - 1 else it.likeCount + 1)
                } else it
            })
        }

        launchSafe {
            repository.toggleLike(postId).onFailure {
                // ROLLBACK on failure
                _feedState.update { it.copy(posts = previousPosts) }
            }
        }
    }
}

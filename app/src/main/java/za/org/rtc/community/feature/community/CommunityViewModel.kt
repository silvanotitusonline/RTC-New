package za.org.rtc.community.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.feature.publicreports.domain.PublicReportFilters
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val publicReportRepository: PublicReportRepository? = null,
) : ViewModel() {
    private val _feedState = MutableStateFlow(CommunityFeedState(initialLoading = true))
    val feedState = _feedState.asStateFlow()

    private val _detailState = MutableStateFlow(CommunityDetailState())
    val detailState = _detailState.asStateFlow()

    private val _pendingLikeIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingLikeIds = _pendingLikeIds.asStateFlow()

    private var refreshGeneration = 0L
    private var detailGeneration = 0L

    init {
        refreshFeed(initial = true)
        refreshPublicReports()
    }

    fun refresh() {
        refreshFeed(initial = false)
        refreshPublicReports()
    }

    fun refreshPublicReports() {
        val reportRepo = publicReportRepository ?: return
        _feedState.value = _feedState.value.copy(
            publicReportsLoading = true,
            publicReportsError = null,
        )
        viewModelScope.launch {
            val pageResult = reportRepo.page(
                filters = PublicReportFilters(),
                limit = 10,
            )
            val dashboardResult = reportRepo.dashboard()

            val current = _feedState.value
            _feedState.value = current.copy(
                publicReports = pageResult.getOrNull()?.items ?: current.publicReports,
                publicReportsDashboard = dashboardResult.getOrNull() ?: current.publicReportsDashboard,
                publicReportsLoading = false,
                publicReportsLastFetched = Instant.now(),
                publicReportsError = if (pageResult.isFailure && dashboardResult.isFailure) {
                    "Unable to refresh public reports"
                } else null,
            )
        }
    }

    fun dismissMutationError() {
        _feedState.value = _feedState.value.copy(mutationError = null)
    }

    fun dismissAppendError() {
        _feedState.value = _feedState.value.copy(appendError = null)
    }

    fun dismissDetailMessage() {
        _detailState.value = _detailState.value.copy(message = null, isSuccess = false)
    }

    suspend fun refreshMediaUrl(mediaId: String): String? = repository.refreshMediaUrl(mediaId).getOrNull()

    fun loadPostDetail(postId: String) {
        if (postId.isBlank()) return
        val generation = ++detailGeneration
        _detailState.value = CommunityDetailState(isLoading = true)
        viewModelScope.launch {
            val result = loadDetailSnapshot(postId)
            if (generation != detailGeneration) return@launch
            result.onSuccess { snapshot ->
                _detailState.value = snapshot
            }.onFailure {
                _detailState.value = CommunityDetailState(
                    isLoading = false,
                    message = "Community conversation could not be loaded. Please try again.",
                )
            }
        }
    }

    fun createComment(postId: String, body: String, parentId: String? = null) {
        if (_detailState.value.isCreatingComment) return
        _detailState.value = _detailState.value.copy(
            isCreatingComment = true,
            message = null,
            isSuccess = false,
        )
        viewModelScope.launch {
            repository.createComment(postId, body, parentId)
                .onSuccess {
                    refreshDetailAfterMutation(postId, "Comment posted.")
                }
                .onFailure {
                    _detailState.value = _detailState.value.copy(
                        isCreatingComment = false,
                        message = "Comment could not be posted. Refresh and try again.",
                        isSuccess = false,
                    )
                }
        }
    }

    fun updateComment(postId: String, commentId: String, body: String) {
        if (commentId in _detailState.value.pendingCommentIds) return
        _detailState.value = _detailState.value.copy(
            pendingCommentIds = _detailState.value.pendingCommentIds + commentId,
            message = null,
            isSuccess = false,
        )
        viewModelScope.launch {
            repository.updateComment(commentId, body)
                .onSuccess {
                    refreshDetailAfterMutation(postId, "Comment updated.")
                }
                .onFailure {
                    _detailState.value = _detailState.value.copy(
                        pendingCommentIds = _detailState.value.pendingCommentIds - commentId,
                        message = "Comment could not be updated. It may be outside the edit window.",
                        isSuccess = false,
                    )
                }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        if (commentId in _detailState.value.pendingCommentIds) return
        _detailState.value = _detailState.value.copy(
            pendingCommentIds = _detailState.value.pendingCommentIds + commentId,
            message = null,
            isSuccess = false,
        )
        viewModelScope.launch {
            repository.deleteComment(commentId)
                .onSuccess {
                    refreshDetailAfterMutation(postId, "Comment removed.")
                }
                .onFailure {
                    _detailState.value = _detailState.value.copy(
                        pendingCommentIds = _detailState.value.pendingCommentIds - commentId,
                        message = "Comment could not be removed. Please try again.",
                        isSuccess = false,
                    )
                }
        }
    }

    fun deletePost(postId: String, onDeleted: () -> Unit = {}) {
        val originalItems = _feedState.value.items
        val originalIndex = originalItems.indexOfFirst { it.id == postId }
        val originalPost = if (originalIndex >= 0) originalItems[originalIndex] else null

        // Optimistic deletion
        _feedState.value = _feedState.value.copy(
            items = originalItems.filterNot { it.id == postId }
        )
        if (_detailState.value.post?.id == postId) {
            _detailState.value = CommunityDetailState(
                isLoading = false,
                post = null,
                comments = emptyList(),
                message = "Post deleted.",
                isSuccess = true,
            )
        }

        viewModelScope.launch {
            repository.deletePost(postId)
                .onSuccess {
                    onDeleted()
                }
                .onFailure {
                    if (originalPost != null) {
                        val currentList = _feedState.value.items.toMutableList()
                        val insertAt = originalIndex.coerceAtMost(currentList.size)
                        currentList.add(insertAt, originalPost)
                        _feedState.value = _feedState.value.copy(items = currentList)
                    }
                    _feedState.value = _feedState.value.copy(
                        mutationError = "Post could not be deleted. Please try again."
                    )
                    if (_detailState.value.post?.id == postId) {
                        _detailState.value = _detailState.value.copy(
                            message = "Post could not be deleted. Please try again.",
                            isSuccess = false,
                        )
                    }
                }
        }
    }

    fun moderateComment(postId: String, commentId: String, reason: String) {
        if (commentId in _detailState.value.pendingCommentIds) return
        _detailState.value = _detailState.value.copy(
            pendingCommentIds = _detailState.value.pendingCommentIds + commentId,
            message = null,
            isSuccess = false,
        )
        viewModelScope.launch {
            repository.moderateComment(commentId, reason)
                .onSuccess {
                    refreshDetailAfterMutation(postId, "Comment removed by moderation.")
                }
                .onFailure {
                    _detailState.value = _detailState.value.copy(
                        pendingCommentIds = _detailState.value.pendingCommentIds - commentId,
                        message = "Comment could not be removed. Check your staff access and try again.",
                        isSuccess = false,
                    )
                }
        }
    }

    fun toggleLike(postId: String) {
        if (postId in _pendingLikeIds.value) return
        val feedOriginal = _feedState.value.items.firstOrNull { it.id == postId }
        val detailOriginal = _detailState.value.post?.takeIf { it.id == postId }
        val original = feedOriginal ?: detailOriginal ?: return
        val optimistic = original.optimisticLikeToggle()

        _pendingLikeIds.value = _pendingLikeIds.value + postId
        updatePostEverywhere(postId) { optimistic }
        _feedState.value = _feedState.value.copy(mutationError = null)
        _detailState.value = _detailState.value.copy(message = null, isSuccess = false)

        viewModelScope.launch {
            repository.toggleLike(postId)
                .onSuccess { outcome ->
                    updatePostEverywhere(postId) { current -> current.withLikeOutcome(outcome) }
                }
                .onFailure {
                    updateFeedPost(postId) { current ->
                        if (feedOriginal != null) feedOriginal else current
                    }
                    updateDetailPost(postId) { current ->
                        if (detailOriginal != null) detailOriginal else current
                    }
                    val error = "Your reaction could not be saved. Please try again."
                    _feedState.value = _feedState.value.copy(mutationError = error)
                    if (_detailState.value.post?.id == postId) {
                        _detailState.value = _detailState.value.copy(message = error, isSuccess = false)
                    }
                }
            _pendingLikeIds.value = _pendingLikeIds.value - postId
        }
    }

    fun toggleReaction(postId: String, emoji: String) {
        if (postId in _pendingLikeIds.value) return
        val feedOriginal = _feedState.value.items.firstOrNull { it.id == postId }
        val detailOriginal = _detailState.value.post?.takeIf { it.id == postId }
        val original = feedOriginal ?: detailOriginal ?: return
        val optimistic = original.optimisticReactionToggle(emoji)

        _pendingLikeIds.value = _pendingLikeIds.value + postId
        updatePostEverywhere(postId) { optimistic }
        _feedState.value = _feedState.value.copy(mutationError = null)
        _detailState.value = _detailState.value.copy(message = null, isSuccess = false)

        viewModelScope.launch {
            repository.toggleReaction(postId, emoji)
                .onSuccess { outcome ->
                    updatePostEverywhere(postId) { current -> current.withLikeOutcome(outcome) }
                }
                .onFailure {
                    updateFeedPost(postId) { current ->
                        if (feedOriginal != null) feedOriginal else current
                    }
                    updateDetailPost(postId) { current ->
                        if (detailOriginal != null) detailOriginal else current
                    }
                    val error = "Your reaction could not be saved. Please try again."
                    _feedState.value = _feedState.value.copy(mutationError = error)
                    if (_detailState.value.post?.id == postId) {
                        _detailState.value = _detailState.value.copy(message = error, isSuccess = false)
                    }
                }
            _pendingLikeIds.value = _pendingLikeIds.value - postId
        }
    }

    fun repostPost(postId: String) {
        val feedOriginal = _feedState.value.items.firstOrNull { it.id == postId }
        val detailOriginal = _detailState.value.post?.takeIf { it.id == postId }
        val original = feedOriginal ?: detailOriginal ?: return
        val optimistic = original.optimisticRepostToggle()

        updatePostEverywhere(postId) { optimistic }
        _feedState.value = _feedState.value.copy(mutationError = null)

        viewModelScope.launch {
            repository.repostPost(postId)
                .onSuccess { (reposted, count) ->
                    updatePostEverywhere(postId) { current ->
                        current.copy(isRepostedByViewer = reposted, repostCount = count)
                    }
                }
                .onFailure {
                    updateFeedPost(postId) { current -> if (feedOriginal != null) feedOriginal else current }
                    updateDetailPost(postId) { current -> if (detailOriginal != null) detailOriginal else current }
                    _feedState.value = _feedState.value.copy(mutationError = "Repost could not be completed. Please try again.")
                }
        }
    }

    fun toggleBookmark(postId: String) {
        val feedOriginal = _feedState.value.items.firstOrNull { it.id == postId }
        val detailOriginal = _detailState.value.post?.takeIf { it.id == postId }
        val original = feedOriginal ?: detailOriginal ?: return
        val optimistic = original.optimisticBookmarkToggle()

        updatePostEverywhere(postId) { optimistic }
        _feedState.value = _feedState.value.copy(mutationError = null)

        viewModelScope.launch {
            repository.toggleBookmark(postId)
                .onSuccess { (bookmarked, count) ->
                    updatePostEverywhere(postId) { current ->
                        current.copy(isBookmarkedByViewer = bookmarked, bookmarkCount = count)
                    }
                }
                .onFailure {
                    updateFeedPost(postId) { current -> if (feedOriginal != null) feedOriginal else current }
                    updateDetailPost(postId) { current -> if (detailOriginal != null) detailOriginal else current }
                    _feedState.value = _feedState.value.copy(mutationError = "Bookmark could not be saved. Please try again.")
                }
        }
    }

    fun searchPosts(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            clearSearch()
            return
        }
        _feedState.value = _feedState.value.copy(
            searchQuery = cleanQuery,
            isSearching = true,
        )
        viewModelScope.launch {
            repository.searchPosts(cleanQuery)
                .onSuccess { results ->
                    _feedState.value = _feedState.value.copy(
                        searchResults = results,
                        isSearching = false,
                    )
                }
                .onFailure {
                    _feedState.value = _feedState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        mutationError = "Search failed. Showing offline results if available.",
                    )
                }
        }
    }

    fun clearSearch() {
        _feedState.value = _feedState.value.copy(
            searchQuery = "",
            searchResults = null,
            isSearching = false,
        )
    }

    fun markNewPostsSeen() {
        _feedState.value = _feedState.value.copy(hasNewPosts = false)
    }

    fun loadNextPage() {
        val current = _feedState.value
        if (!current.hasMore || current.initialLoading || current.refreshing || current.appendLoading) return
        val cursor = current.nextCursor ?: return
        val generation = refreshGeneration
        _feedState.value = current.copy(appendLoading = true, appendError = null)

        viewModelScope.launch {
            repository.loadFeedPage(cursor, COMMUNITY_FEED_PAGE_SIZE)
                .onSuccess { page ->
                    if (generation == refreshGeneration) {
                        _feedState.value = _feedState.value.withPage(page, append = true)
                    }
                }
                .onFailure {
                    if (generation == refreshGeneration) {
                        _feedState.value = _feedState.value.copy(
                            appendLoading = false,
                            appendError = "More Community posts could not be loaded. Try again.",
                        )
                    }
                }
        }
    }

    private fun refreshFeed(initial: Boolean) {
        val generation = ++refreshGeneration
        val current = _feedState.value
        _feedState.value = current.copy(
            initialLoading = initial || current.items.isEmpty(),
            refreshing = !initial && current.items.isNotEmpty(),
            initialError = null,
            appendError = null,
        )

        viewModelScope.launch {
            repository.loadFeedPage(cursor = null, limit = COMMUNITY_FEED_PAGE_SIZE)
                .onSuccess { page ->
                    if (generation == refreshGeneration) {
                        _feedState.value = _feedState.value.withPage(page, append = false)
                    }
                }
                .onFailure {
                    if (generation == refreshGeneration) {
                        val state = _feedState.value
                        _feedState.value = state.copy(
                            initialLoading = false,
                            refreshing = false,
                            initialError = if (state.items.isEmpty()) "Community posts could not be loaded. Try again." else null,
                            appendError = if (state.items.isNotEmpty()) "Community posts could not be refreshed. Try again." else null,
                        )
                    }
                }
        }
    }

    private suspend fun loadDetailSnapshot(postId: String): Result<CommunityDetailState> = runCatching {
        coroutineScope {
            val post = async { repository.loadPost(postId).getOrThrow() }
            val comments = async { repository.loadComments(postId).getOrThrow() }
            CommunityDetailState(
                post = post.await(),
                comments = comments.await(),
                isLoading = false,
            )
        }
    }

    private suspend fun refreshDetailAfterMutation(postId: String, successMessage: String) {
        loadDetailSnapshot(postId)
            .onSuccess { snapshot ->
                _detailState.value = snapshot.copy(message = successMessage, isSuccess = true)
                snapshot.post?.let { updated ->
                    updateFeedPost(postId) { current ->
                        current.copy(
                            reactions = updated.reactions,
                            comments = updated.comments,
                            viewerHasLiked = updated.viewerHasLiked,
                        )
                    }
                }
            }
            .onFailure {
                _detailState.value = _detailState.value.copy(
                    isLoading = false,
                    isCreatingComment = false,
                    pendingCommentIds = emptySet(),
                    message = "The change was saved, but the conversation could not be refreshed. Pull to refresh and try again.",
                    isSuccess = false,
                )
            }
    }

    private fun updatePostEverywhere(postId: String, transform: (CommunityPost) -> CommunityPost) {
        updateFeedPost(postId, transform)
        updateDetailPost(postId, transform)
    }

    private fun updateFeedPost(postId: String, transform: (CommunityPost) -> CommunityPost) {
        val state = _feedState.value
        _feedState.value = state.copy(
            items = state.items.map { post -> if (post.id == postId) transform(post) else post },
        )
    }

    private fun updateDetailPost(postId: String, transform: (CommunityPost) -> CommunityPost) {
        val state = _detailState.value
        val post = state.post ?: return
        if (post.id == postId) _detailState.value = state.copy(post = transform(post))
    }
}

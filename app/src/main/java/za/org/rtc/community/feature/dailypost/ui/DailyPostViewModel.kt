package za.org.rtc.community.feature.dailypost.ui

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import za.org.rtc.community.MainActivity
import za.org.rtc.community.R
import za.org.rtc.community.feature.dailypost.data.DailyPostRepository
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.DailyPostComment
import za.org.rtc.community.feature.dailypost.domain.calculateEstimatedReadingTimeMinutes
import za.org.rtc.community.notifications.RTC_COMMUNITY_UPDATES_CHANNEL
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DailyPostViewModel @Inject constructor(
    private val repository: DailyPostRepository,
    @ApplicationContext private val context: Context,
    private val syncEngine: za.org.rtc.community.core.sync.SystemUpdateSyncEngine = za.org.rtc.community.core.sync.SystemUpdateSyncEngine(),
) : ViewModel() {

    enum class LiveUpdateStatus { CONNECTING, LIVE, UNAVAILABLE }

    val publishedArticles: StateFlow<List<DailyPostArticle>> = repository
        .observePublishedArticles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val allArticles: StateFlow<List<DailyPostArticle>> = repository
        .observeAllArticles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _selectedArticle = MutableStateFlow<DailyPostArticle?>(null)
    val selectedArticle: StateFlow<DailyPostArticle?> = _selectedArticle.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _comments = MutableStateFlow<List<DailyPostComment>>(emptyList())
    val comments: StateFlow<List<DailyPostComment>> = _comments.asStateFlow()

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()

    private val _commentPendingId = MutableStateFlow<String?>(null)
    val commentPendingId: StateFlow<String?> = _commentPendingId.asStateFlow()

    private val _commentsHasMore = MutableStateFlow(false)
    val commentsHasMore: StateFlow<Boolean> = _commentsHasMore.asStateFlow()

    private val _commentsLiveStatus = MutableStateFlow(LiveUpdateStatus.CONNECTING)
    val commentsLiveStatus: StateFlow<LiveUpdateStatus> = _commentsLiveStatus.asStateFlow()

    private var commentRealtimeJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repository.refreshPublishedArticles()
                repository.refreshAllArticles()
            }.onFailure {
                _statusMessage.value = "Daily Posts are offline. Showing the last synchronized content."
            }
        }
    }

    fun selectArticle(article: DailyPostArticle?) {
        _selectedArticle.value = article
    }

    fun loadArticleById(id: String) {
        viewModelScope.launch {
            _selectedArticle.value = repository.getArticle(id)
            loadCommentsInternal(id, reset = true)
        }
    }

    fun refreshComments(articleId: String) {
        viewModelScope.launch { loadCommentsInternal(articleId, showLoading = false, reset = true) }
    }

    fun observeComments(articleId: String) {
        commentRealtimeJob?.cancel()
        commentRealtimeJob = viewModelScope.launch {
            repository.observeCommentChanges(articleId)
                .onStart { _commentsLiveStatus.value = LiveUpdateStatus.CONNECTING }
                .onEach { _commentsLiveStatus.value = LiveUpdateStatus.LIVE }
                .debounce(250)
                .catch {
                    _commentsLiveStatus.value = LiveUpdateStatus.UNAVAILABLE
                    _statusMessage.value = "Live comment updates are unavailable. Use refresh to check for new comments."
                }
                .collectLatest { loadCommentsInternal(articleId, showLoading = false, reset = true) }
        }
    }

    fun stopObservingComments() {
        commentRealtimeJob?.cancel()
        commentRealtimeJob = null
    }

    fun loadOlderComments(articleId: String) {
        if (!_commentsHasMore.value || _commentPendingId.value != null) return
        viewModelScope.launch {
            val last = _comments.value.lastOrNull() ?: return@launch
            loadCommentsInternal(
                articleId = articleId,
                showLoading = false,
                reset = false,
                afterCreatedAt = Instant.ofEpochMilli(last.createdAtEpochMillis).toString(),
                afterId = last.id,
            )
        }
    }

    fun submitComment(articleId: String, body: String, parentId: String? = null, onSuccess: () -> Unit = {}) {
        val cleanBody = body.trim()
        if (cleanBody.isBlank() || cleanBody.length > 2000) {
            _statusMessage.value = "Comments must contain between 1 and 2,000 characters."
            return
        }
        viewModelScope.launch {
            _commentPendingId.value = "new"
            runCatching {
                repository.createComment(articleId, cleanBody, parentId)
                loadCommentsInternal(articleId, showLoading = false, reset = true)
                _statusMessage.value = "Comment posted."
                onSuccess()
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The comment could not be posted. Nothing was changed."
            }
            _commentPendingId.value = null
        }
    }

    fun updateComment(articleId: String, commentId: String, body: String, onSuccess: () -> Unit = {}) {
        val cleanBody = body.trim()
        if (cleanBody.isBlank() || cleanBody.length > 2000) {
            _statusMessage.value = "Comments must contain between 1 and 2,000 characters."
            return
        }
        viewModelScope.launch {
            _commentPendingId.value = commentId
            runCatching {
                repository.updateComment(commentId, cleanBody)
                loadCommentsInternal(articleId, showLoading = false, reset = true)
                _statusMessage.value = "Comment updated."
                onSuccess()
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The comment could not be updated."
            }
            _commentPendingId.value = null
        }
    }

    fun deleteComment(articleId: String, commentId: String) {
        viewModelScope.launch {
            _commentPendingId.value = commentId
            runCatching {
                repository.deleteComment(commentId)
                loadCommentsInternal(articleId, showLoading = false, reset = true)
                _statusMessage.value = "Comment removed."
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The comment could not be removed."
            }
            _commentPendingId.value = null
        }
    }

    fun moderateComment(articleId: String, commentId: String, reason: String) {
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            _statusMessage.value = "A moderation reason is required."
            return
        }
        viewModelScope.launch {
            _commentPendingId.value = commentId
            runCatching {
                repository.moderateComment(commentId, cleanReason)
                loadCommentsInternal(articleId, showLoading = false, reset = true)
                _statusMessage.value = "Comment hidden and recorded for moderation audit."
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The comment could not be moderated."
            }
            _commentPendingId.value = null
        }
    }

    fun reportComment(articleId: String, commentId: String, reasonCode: String, detail: String) {
        viewModelScope.launch {
            _commentPendingId.value = commentId
            runCatching {
                repository.reportComment(commentId, reasonCode, detail)
                _statusMessage.value = "Report submitted for moderation review."
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The report could not be submitted."
            }
            _commentPendingId.value = null
        }
    }

    private suspend fun loadCommentsInternal(
        articleId: String,
        showLoading: Boolean = true,
        reset: Boolean,
        afterCreatedAt: String? = null,
        afterId: String? = null,
    ) {
        if (showLoading) _commentsLoading.value = true
        runCatching { repository.loadComments(articleId, afterCreatedAt, afterId, limit = 100) }
            .onSuccess { page ->
                _comments.value = if (reset) page.comments else (_comments.value + page.comments).distinctBy { it.id }
                _commentsHasMore.value = page.hasMore
            }
            .onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "Comments could not be loaded. Pull to try again."
            }
        if (showLoading) _commentsLoading.value = false
    }

    override fun onCleared() {
        stopObservingComments()
        super.onCleared()
    }

    fun publishArticle(article: DailyPostArticle, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val estimatedReadTime = calculateEstimatedReadingTimeMinutes(
                    title = article.title,
                    subtitle = article.subtitle,
                    content = article.content,
                    keyHighlights = article.keyHighlights
                )
                val articleToPublish = article.copy(readTimeMinutes = estimatedReadTime)
                val published = repository.publishArticle(articleToPublish)
                _statusMessage.value = "Article published and synchronized to the Daily Post feed."
                syncEngine.triggerSystemWideUpdate(
                    za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.DailyPostPublished(published.id, published.title)
                )
                triggerPublishNotification(published)
                onSuccess()
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The article could not be published. Nothing was changed."
            }
        }
    }

    fun saveDraft(article: DailyPostArticle, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val estimatedReadTime = calculateEstimatedReadingTimeMinutes(
                    title = article.title,
                    subtitle = article.subtitle,
                    content = article.content,
                    keyHighlights = article.keyHighlights
                )
                repository.saveArticle(article.copy(isPublished = false, readTimeMinutes = estimatedReadTime))
                _statusMessage.value = "Draft saved to the editorial workspace."
                onSuccess()
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The draft could not be saved. Nothing was changed."
            }
        }
    }

    private fun triggerPublishNotification(article: DailyPostArticle) {
        runCatching {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = MainActivity.ACTION_OPEN_DAILY_POST
                data = Uri.parse("rtc://daily-post/article/${article.id}")
                putExtra(MainActivity.EXTRA_DAILY_POST_ID, article.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                article.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, RTC_COMMUNITY_UPDATES_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("📢 Daily Post: ${article.title.take(70)}")
                .setContentText((if (article.subtitle.isNotBlank()) article.subtitle else article.content).take(140))
                .setStyle(NotificationCompat.BigTextStyle().bigText("${article.subtitle}\n\n${article.content.take(300)}..."))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(article.id.hashCode(), notification)
            }
        }
    }

    fun deleteArticle(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteArticle(id)
                if (_selectedArticle.value?.id == id) _selectedArticle.value = null
                _statusMessage.value = "Article archived and removed from the resident feed."
            }.onFailure { error ->
                _statusMessage.value = error.message?.takeIf { it.isNotBlank() }
                    ?: "The article could not be archived."
            }
        }
    }

    fun toggleLike(id: String) {
        viewModelScope.launch {
            repository.toggleLike(id)
            // Update selected article if it matches
            _selectedArticle.value?.let { current ->
                if (current.id == id) {
                    val newLiked = !current.viewerHasLiked
                    val newCount = if (newLiked) current.reactionsCount + 1 else maxOf(0, current.reactionsCount - 1)
                    _selectedArticle.value = current.copy(viewerHasLiked = newLiked, reactionsCount = newCount)
                }
            }
        }
    }

    fun dismissStatusMessage() {
        _statusMessage.value = null
    }
}

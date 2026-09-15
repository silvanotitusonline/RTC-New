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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.org.rtc.community.MainActivity
import za.org.rtc.community.R
import za.org.rtc.community.feature.dailypost.data.DailyPostRepository
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.calculateEstimatedReadingTimeMinutes
import za.org.rtc.community.notifications.RTC_COMMUNITY_UPDATES_CHANNEL
import javax.inject.Inject

@HiltViewModel
class DailyPostViewModel @Inject constructor(
    private val repository: DailyPostRepository,
    @ApplicationContext private val context: Context,
    private val syncEngine: za.org.rtc.community.core.sync.SystemUpdateSyncEngine = za.org.rtc.community.core.sync.SystemUpdateSyncEngine(),
) : ViewModel() {

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

    fun selectArticle(article: DailyPostArticle?) {
        _selectedArticle.value = article
    }

    fun loadArticleById(id: String) {
        viewModelScope.launch {
            _selectedArticle.value = repository.getArticle(id)
        }
    }

    fun publishArticle(article: DailyPostArticle, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val estimatedReadTime = calculateEstimatedReadingTimeMinutes(
                title = article.title,
                subtitle = article.subtitle,
                content = article.content,
                keyHighlights = article.keyHighlights
            )
            val articleToPublish = article.copy(
                isPublished = true,
                readTimeMinutes = estimatedReadTime,
                publishedAtEpochMillis = System.currentTimeMillis()
            )
            repository.publishArticle(articleToPublish)
            _statusMessage.value = "Article successfully published to Daily Post!"
            syncEngine.triggerSystemWideUpdate(
                za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.DailyPostPublished(articleToPublish.id, articleToPublish.title)
            )
            triggerPublishNotification(articleToPublish)
            onSuccess()
        }
    }

    fun saveDraft(article: DailyPostArticle, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val estimatedReadTime = calculateEstimatedReadingTimeMinutes(
                title = article.title,
                subtitle = article.subtitle,
                content = article.content,
                keyHighlights = article.keyHighlights
            )
            val draftToSave = article.copy(
                isPublished = false,
                readTimeMinutes = estimatedReadTime
            )
            repository.saveArticle(draftToSave)
            _statusMessage.value = "Draft saved successfully."
            onSuccess()
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
            repository.deleteArticle(id)
            if (_selectedArticle.value?.id == id) {
                _selectedArticle.value = null
            }
            _statusMessage.value = "Article deleted."
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

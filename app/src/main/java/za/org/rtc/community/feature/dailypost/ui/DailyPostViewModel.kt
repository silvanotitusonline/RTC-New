package za.org.rtc.community.feature.dailypost.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.org.rtc.community.feature.dailypost.data.DailyPostRepository
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import javax.inject.Inject

@HiltViewModel
class DailyPostViewModel @Inject constructor(
    private val repository: DailyPostRepository,
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
            repository.publishArticle(article)
            _statusMessage.value = "Article successfully published to Daily Post!"
            onSuccess()
        }
    }

    fun saveDraft(article: DailyPostArticle, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveArticle(article.copy(isPublished = false))
            _statusMessage.value = "Draft saved successfully."
            onSuccess()
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

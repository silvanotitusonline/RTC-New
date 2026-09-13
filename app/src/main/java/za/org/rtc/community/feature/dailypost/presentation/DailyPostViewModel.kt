package za.org.rtc.community.feature.dailypost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.feature.dailypost.domain.*

data class DailyPostUiState(
    val items: List<DailyPost> = emptyList(),
    val selected: DailyPost? = null,
    val media: List<DailyPostMedia> = emptyList(),
    val comments: List<DailyPostComment> = emptyList(),
    val translation: DailyPostTranslation? = null,
    val narrationUrl: String? = null,
    val preview: DailyPost? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val translating: Boolean = false,
    val narrating: Boolean = false,
    val message: String? = null,
    val cursor: DailyPostCursor? = null,
)

@HiltViewModel
class DailyPostViewModel @Inject constructor(
    private val repository: DailyPostRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DailyPostUiState())
    val state: StateFlow<DailyPostUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, message = null, cursor = null) }
        viewModelScope.launch {
            repository.page(limit = 20)
                .onSuccess { page -> _state.update { it.copy(items = page.items, cursor = page.nextCursor, loading = false) } }
                .onFailure { error -> _state.update { it.copy(loading = false, message = error.safeMessage("The Daily Post could not be loaded.")) } }
        }
    }

    fun loadMore() {
        val cursor = _state.value.cursor ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.page(cursor, 20)
                .onSuccess { page -> _state.update { current ->
                    val combined = (current.items + page.items).distinctBy(DailyPost::id)
                    current.copy(items = combined, cursor = page.nextCursor, loadingMore = false)
                } }
                .onFailure { error -> _state.update { it.copy(loadingMore = false, message = error.safeMessage("More stories could not be loaded.")) } }
        }
    }

    fun open(postId: String) {
        _state.update { it.copy(selected = null, media = emptyList(), comments = emptyList(), translation = null, narrationUrl = null, loading = true, message = null) }
        viewModelScope.launch {
            val post = repository.get(postId).getOrElse { error ->
                _state.update { it.copy(loading = false, message = error.safeMessage("Publication could not be opened.")) }
                return@launch
            }
            if (post == null) {
                _state.update { it.copy(loading = false, message = "Publication is no longer available.") }
                return@launch
            }
            val comments = repository.comments(postId).getOrDefault(emptyList())
            val media = repository.media(postId).getOrDefault(emptyList())
            _state.update { it.copy(selected = post, media = media, comments = comments, loading = false) }
        }
    }

    fun closeDetail() = _state.update { it.copy(selected = null, media = emptyList(), comments = emptyList(), translation = null, narrationUrl = null) }

    fun addComment(body: String, parentId: String? = null) {
        val post = _state.value.selected ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            repository.addComment(post.id, body, parentId)
                .onSuccess { reloadComments(post.id) }
                .onFailure { error -> _state.update { it.copy(message = error.safeMessage("Comment could not be posted.")) } }
        }
    }

    fun updateComment(commentId: String, body: String) {
        val post = _state.value.selected ?: return
        viewModelScope.launch {
            repository.updateComment(commentId, body)
                .onSuccess { reloadComments(post.id) }
                .onFailure { error -> _state.update { it.copy(message = error.safeMessage("Comment could not be updated.")) } }
        }
    }

    fun deleteComment(commentId: String) {
        val post = _state.value.selected ?: return
        viewModelScope.launch {
            repository.deleteComment(commentId)
                .onSuccess { reloadComments(post.id) }
                .onFailure { error -> _state.update { it.copy(message = error.safeMessage("Comment could not be removed.")) } }
        }
    }

    fun translate(targetLanguage: String) {
        val post = _state.value.selected ?: return
        _state.update { it.copy(translating = true, translation = null, narrationUrl = null, message = null) }
        viewModelScope.launch {
            repository.translate(post.id, targetLanguage)
                .onSuccess { translation -> _state.update { it.copy(translation = translation, translating = false) } }
                .onFailure { error -> _state.update { it.copy(translating = false, message = error.safeMessage("Translation is unavailable.")) } }
        }
    }

    fun narrate(targetLanguage: String? = _state.value.translation?.targetLanguage) {
        val post = _state.value.selected ?: return
        _state.update { it.copy(narrating = true, narrationUrl = null, message = null) }
        viewModelScope.launch {
            repository.narration(post.id, targetLanguage)
                .onSuccess { url -> _state.update { it.copy(narrationUrl = url, narrating = false) } }
                .onFailure { error -> _state.update { it.copy(narrating = false, message = error.safeMessage("AI narration is unavailable.")) } }
        }
    }

    fun clearTranslation() = _state.update { it.copy(translation = null, narrationUrl = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun checkPreview() {
        viewModelScope.launch {
            repository.nextUnseenPreview().onSuccess { preview -> _state.update { it.copy(preview = preview) } }
        }
    }

    fun dismissPreview(open: Boolean) {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(preview = null) }
        viewModelScope.launch {
            repository.markPreviewPresented(preview.id)
            if (open) open(preview.id)
        }
    }

    private suspend fun reloadComments(postId: String) {
        repository.comments(postId).onSuccess { comments -> _state.update { it.copy(comments = comments) } }
    }
}

data class DailyPostStudioState(
    val publications: List<DailyPost> = emptyList(),
    val filter: DailyPostState? = null,
    val draft: DailyPostDraft = DailyPostDraft(),
    val media: List<DailyPostMedia> = emptyList(),
    val editing: Boolean = false,
    val previewing: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DailyPostStudioViewModel @Inject constructor(
    private val repository: DailyPostRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DailyPostStudioState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh(filter: DailyPostState? = _state.value.filter) {
        _state.update { it.copy(filter = filter, working = true, message = null) }
        viewModelScope.launch {
            repository.adminPage(filter)
                .onSuccess { rows -> _state.update { it.copy(publications = rows, working = false) } }
                .onFailure { error -> _state.update { it.copy(working = false, message = error.safeMessage("Publications could not be loaded.")) } }
        }
    }

    fun newDraft(template: DailyPostTemplate) {
        val blocks = template.starterBlocks.mapIndexed { index, type -> DailyPostBlock("block-${index + 1}", type) }
        _state.update { it.copy(draft = DailyPostDraft(templateKey = template.key, blocks = blocks), media = emptyList(), editing = true, previewing = false, message = null) }
    }

    fun edit(post: DailyPost) {
        _state.update { it.copy(
            draft = DailyPostDraft(post.id, post.publicationType, post.templateKey, post.canonicalLanguage, post.headline, post.excerpt, post.blocks, post.quotedPostId, post.pushEnabled, post.previewPopupEnabled, post.scheduledFor),
            media = emptyList(), editing = true, previewing = false, message = null,
        ) }
        viewModelScope.launch {
            repository.media(post.id).onSuccess { media -> _state.update { it.copy(media = media) } }
        }
    }

    fun updateDraft(transform: (DailyPostDraft) -> DailyPostDraft) = _state.update { it.copy(draft = transform(it.draft)) }
    fun preview(enabled: Boolean) = _state.update { it.copy(previewing = enabled) }
    fun closeEditor() = _state.update { it.copy(editing = false, previewing = false, draft = DailyPostDraft(), media = emptyList()) }

    fun save(onSaved: (() -> Unit)? = null) {
        val draft = _state.value.draft
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            repository.saveDraft(draft)
                .onSuccess { id ->
                    _state.update { it.copy(working = false, draft = it.draft.copy(id = id), message = "Draft saved.") }
                    repository.media(id).onSuccess { media -> _state.update { it.copy(media = media) } }
                    refresh(_state.value.filter)
                    onSaved?.invoke()
                }
                .onFailure { error -> _state.update { it.copy(working = false, message = error.safeMessage("Draft could not be saved.")) } }
        }
    }

    fun uploadMedia(upload: DailyPostMediaUpload, blockId: String? = null) {
        val currentDraft = _state.value.draft
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val postId = currentDraft.id ?: repository.saveDraft(currentDraft).getOrElse { error ->
                _state.update { it.copy(working = false, message = error.safeMessage("Save the draft before uploading media.")) }
                return@launch
            }
            if (currentDraft.id == null) _state.update { it.copy(draft = it.draft.copy(id = postId)) }
            repository.uploadMedia(postId, upload, _state.value.media.size)
                .onSuccess { media ->
                    _state.update { state ->
                        val updatedBlocks = if (blockId == null) state.draft.blocks else state.draft.blocks.map { block ->
                            if (block.id == blockId) block.copy(mediaIds = (block.mediaIds + media.id).distinct()) else block
                        }
                        state.copy(
                            working = false,
                            media = state.media + media,
                            draft = state.draft.copy(blocks = updatedBlocks),
                            message = "Media added. Save the draft to persist its layout placement.",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(working = false, message = error.safeMessage("Media could not be uploaded.")) } }
        }
    }

    fun deleteMedia(mediaId: String) {
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            repository.deleteMedia(mediaId)
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            working = false,
                            media = state.media.filterNot { it.id == mediaId },
                            draft = state.draft.copy(blocks = state.draft.blocks.map { block -> block.copy(mediaIds = block.mediaIds.filterNot { it == mediaId }) }),
                            message = "Media removed.",
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(working = false, message = error.safeMessage("Media could not be removed.")) } }
        }
    }

    fun publishNow() = persistThen { id, draft -> repository.publishNow(id, draft.pushEnabled, draft.previewPopupEnabled) }

    fun schedule(instant: Instant) = persistThen { id, draft -> repository.schedule(id, instant.toString(), draft.pushEnabled, draft.previewPopupEnabled) }

    fun archive(post: DailyPost) {
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            repository.archive(post.id)
                .onSuccess { _state.update { it.copy(working = false, message = "Publication archived.") }; refresh(_state.value.filter) }
                .onFailure { error -> _state.update { it.copy(working = false, message = error.safeMessage("Publication could not be archived.")) } }
        }
    }

    private fun persistThen(action: suspend (String, DailyPostDraft) -> Result<Unit>) {
        val draft = _state.value.draft
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val id = repository.saveDraft(draft).getOrElse { error ->
                _state.update { it.copy(working = false, message = error.safeMessage("Draft could not be saved.")) }
                return@launch
            }
            action(id, draft.copy(id = id))
                .onSuccess { _state.update { it.copy(working = false, editing = false, previewing = false, draft = DailyPostDraft(), media = emptyList(), message = "Publication updated.") }; refresh(_state.value.filter) }
                .onFailure { error -> _state.update { it.copy(working = false, message = error.safeMessage("Publication action failed.")) } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}

private fun Throwable.safeMessage(fallback: String): String = message?.takeIf { it.length in 3..240 } ?: fallback

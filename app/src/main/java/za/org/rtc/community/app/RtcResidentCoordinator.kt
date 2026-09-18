package za.org.rtc.community.app

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.core.DraftArea
import za.org.rtc.community.core.ModerationReason
import za.org.rtc.community.data.RtcRepository

internal class RtcResidentCoordinator(
    private val repository: RtcRepository,
    private val scope: CoroutineScope,
    private val showGlobalMessage: (String, Boolean, Boolean) -> Unit,
) {
    private val _pendingCommunityAlertId = MutableStateFlow<String?>(null)
    val pendingCommunityAlertId = _pendingCommunityAlertId.asStateFlow()
    private val _pendingCommunityPostId = MutableStateFlow<String?>(null)
    val pendingCommunityPostId = _pendingCommunityPostId.asStateFlow()
    private val _pendingPublicReportId = MutableStateFlow<String?>(null)
    val pendingPublicReportId = _pendingPublicReportId.asStateFlow()
    private val _pendingDailyPostId = MutableStateFlow<String?>(null)
    val pendingDailyPostId = _pendingDailyPostId.asStateFlow()

    fun openDailyPostFromDeepLink(articleId: String?) {
        _pendingDailyPostId.value = articleId?.trim()?.takeIf { value ->
            value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
        }
    }

    fun consumePendingDailyPost() {
        _pendingDailyPostId.value = null
    }

    fun openPublicReportFromDeepLink(reportId: String?) {
        _pendingPublicReportId.value = reportId?.trim()?.takeIf { value ->
            value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
        }
    }

    fun consumePendingPublicReport() {
        _pendingPublicReportId.value = null
    }
    private val _communityActionUi = MutableStateFlow(CommunityActionUiState())
    val communityActionUi = _communityActionUi.asStateFlow()
    private val _supportUi = MutableStateFlow(WorkflowSubmissionUiState())
    val supportUi = _supportUi.asStateFlow()
    private val _assignedSupportCaseUi = MutableStateFlow(WorkflowSubmissionUiState())
    val assignedSupportCaseUi = _assignedSupportCaseUi.asStateFlow()

    fun openCommunityAlertFromSystemNotification(alertId: String?) {
        _pendingCommunityAlertId.value = alertId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun consumePendingCommunityAlert() {
        _pendingCommunityAlertId.value = null
    }

    fun openCommunityPostFromDeepLink(postId: String?) {
        _pendingCommunityPostId.value = postId?.trim()?.takeIf { value ->
            value.length <= 128 && value.all { it.isLetterOrDigit() || it in "-_" }
        }
    }

    fun consumePendingCommunityPost() {
        _pendingCommunityPostId.value = null
    }

    val lastSyncedEpochMillis: StateFlow<Long> = repository.lastSyncedEpochMillis
    val isSyncingLiveUpdates: StateFlow<Boolean> = repository.isSyncingLiveUpdates
    val syncCount: StateFlow<Int> = repository.syncCount

    fun triggerSystemWideUpdate(
        event: za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent = za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.GlobalSystemRefresh
    ) {
        repository.triggerSystemWideUpdate(event)
    }

    fun refreshLiveContent() {
        scope.launch { repository.refreshLiveContent() }
    }

    fun loadMoreProjects() {
        scope.launch { repository.loadMoreProjects() }
    }

    fun loadMoreCentres() {
        scope.launch { repository.loadMoreCentres() }
    }

    fun loadMoreOpportunities() {
        scope.launch { repository.loadMoreOpportunities() }
    }

    fun searchPublicContent(query: String) {
        scope.launch { repository.searchPublicContent(query) }
    }

    fun clearPublicSearch() = repository.clearPublicSearch()

    fun loadCommunityPostDetail(postId: String) {
        scope.launch { repository.loadCommunityPostDetail(postId) }
    }

    fun acceptCommunityGuidelines() {
        scope.launch {
            _communityActionUi.value = CommunityActionUiState(
                action = CommunityAction.GUIDELINES,
                isWorking = true,
            )
            repository.acceptCommunityGuidelines()
                .onSuccess {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.GUIDELINES,
                        isSuccess = true,
                        message = "Community Guidelines accepted. You can now post and comment.",
                    )
                }
                .onFailure {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.GUIDELINES,
                        message = SafeUiError.community(
                            it,
                            "Community Guidelines could not be saved. Refresh and try again.",
                        ),
                    )
                }
        }
    }

    fun dismissCommunityActionUi() {
        _communityActionUi.value = CommunityActionUiState()
    }

    fun createCommunityComment(postId: String, body: String) {
        scope.launch {
            _communityActionUi.value = CommunityActionUiState(
                action = CommunityAction.COMMENT,
                isWorking = true,
            )
            repository.createCommunityComment(postId, body)
                .onSuccess {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.COMMENT,
                        isSuccess = true,
                        message = "Comment posted.",
                    )
                }
                .onFailure {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.COMMENT,
                        message = SafeUiError.community(
                            it,
                            "Comment could not be posted. Refresh and try again.",
                        ),
                    )
                }
        }
    }

    fun toggleCommunityPostLike(postId: String) {
        scope.launch {
            _communityActionUi.value = CommunityActionUiState(
                action = CommunityAction.LIKE,
                isWorking = true,
            )
            repository.toggleCommunityPostLike(postId)
                .onSuccess {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.LIKE,
                        isSuccess = true,
                        message = "Reaction updated from the Community server.",
                    )
                }
                .onFailure { error ->
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.LIKE,
                        message = SafeUiError.community(
                            error,
                            "Reaction could not be updated. Refresh and try again.",
                        ),
                    )
                }
        }
    }

    fun updateCommunityComment(postId: String, commentId: String, body: String) {
        scope.launch {
            repository.updateCommunityComment(postId, commentId, body)
                .onFailure {
                    showGlobalMessage(
                        SafeUiError.community(
                            it,
                            "Comment could not be updated. It may be more than one hour old.",
                        ),
                        false,
                        false,
                    )
                }
        }
    }

    fun deleteCommunityComment(postId: String, commentId: String) {
        scope.launch {
            repository.deleteCommunityComment(postId, commentId)
                .onFailure {
                    showGlobalMessage(
                        SafeUiError.community(it, "Comment could not be removed."),
                        false,
                        false,
                    )
                }
        }
    }

    fun dismissLiveContentMessage() = repository.dismissLiveContentMessage()

    fun markNotificationsRead() {
        scope.launch {
            repository.markAllNotificationsRead()
                .onFailure {
                    showGlobalMessage("Alert read status could not be updated.", false, false)
                }
        }
    }

    fun loadCommunityAlertDetail(alertId: String) {
        scope.launch { repository.loadCommunityAlertDetail(alertId) }
    }

    fun saveDraft(area: DraftArea, title: String = "", body: String) {
        scope.launch { repository.saveDraft(area, title, body) }
    }

    fun discardDraft(area: DraftArea) {
        scope.launch { repository.discardDraft(area) }
    }

    fun reportCommunityPost(postId: String, reason: ModerationReason, detail: String) {
        scope.launch {
            showGlobalMessage("", false, true)
            repository.reportCommunityPost(postId, reason, detail)
                .onSuccess { showGlobalMessage("Report sent to the moderation team.", true, false) }
                .onFailure {
                    showGlobalMessage(
                        SafeUiError.community(it, "The report could not be sent."),
                        false,
                        false,
                    )
                }
        }
    }

    fun createPost(text: String, mediaUris: List<Uri> = emptyList(), clientPostId: String = java.util.UUID.randomUUID().toString()) {
        scope.launch {
            _communityActionUi.value = CommunityActionUiState(
                action = CommunityAction.POST,
                isWorking = true,
            )
            repository.createPost(text, mediaUris, clientPostId)
                .onSuccess {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.POST,
                        isSuccess = true,
                        message = "Community post published.",
                    )
                }
                .onFailure {
                    _communityActionUi.value = CommunityActionUiState(
                        action = CommunityAction.POST,
                        message = SafeUiError.community(
                            it,
                            "Community post could not be published. Please review the message and try again.",
                        ),
                    )
                }
        }
    }

    fun submitSupportRequest(title: String, detail: String) {
        scope.launch {
            _supportUi.value = WorkflowSubmissionUiState(isWorking = true)
            repository.submitSupportRequest(title, detail)
                .onSuccess {
                    _supportUi.value = WorkflowSubmissionUiState(
                        message = "Support request submitted.",
                        isSuccess = true,
                    )
                }
                .onFailure {
                    _supportUi.value = WorkflowSubmissionUiState(
                        message = SafeUiError.generic(it, "Support request could not be submitted.")
                    )
                }
        }
    }

    fun loadSupportCaseMessages(caseId: String) {
        scope.launch { repository.loadSupportCaseMessages(caseId) }
    }

    fun refreshAssignedSupportCases() {
        scope.launch {
            _assignedSupportCaseUi.value = WorkflowSubmissionUiState(isWorking = true)
            repository.refreshAssignedSupportCases()
                .onSuccess { _assignedSupportCaseUi.value = WorkflowSubmissionUiState() }
                .onFailure { error ->
                    _assignedSupportCaseUi.value = WorkflowSubmissionUiState(
                        message = SafeUiError.generic(
                            error,
                            "Assigned support cases could not be refreshed.",
                        )
                    )
                }
        }
    }

    fun updateAssignedSupportCaseState(caseId: String, state: String, note: String) {
        scope.launch {
            _assignedSupportCaseUi.value = WorkflowSubmissionUiState(isWorking = true)
            repository.updateAssignedSupportCaseState(caseId, state, note)
                .onSuccess {
                    _assignedSupportCaseUi.value = WorkflowSubmissionUiState(
                        message = "The assigned case was updated and refreshed from the server.",
                        isSuccess = true,
                    )
                }
                .onFailure { error ->
                    _assignedSupportCaseUi.value = WorkflowSubmissionUiState(
                        message = SafeUiError.generic(error, "The assigned case could not be updated.")
                    )
                }
        }
    }

    fun dismissAssignedSupportCaseMessage() {
        _assignedSupportCaseUi.value = WorkflowSubmissionUiState()
    }

    fun addSupportCaseMessage(caseId: String, body: String) {
        scope.launch {
            repository.addSupportCaseMessage(caseId, body)
                .onFailure {
                    _supportUi.value = WorkflowSubmissionUiState(
                        message = SafeUiError.generic(it, "Message could not be sent.")
                    )
                }
        }
    }

    fun dismissSupportMessage() {
        _supportUi.value = WorkflowSubmissionUiState()
    }

    suspend fun refreshCommunityMediaUrl(mediaId: String): String? =
        repository.refreshCommunityMediaUrl(mediaId).getOrNull()
}

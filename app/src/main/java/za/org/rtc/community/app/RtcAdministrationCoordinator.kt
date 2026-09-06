package za.org.rtc.community.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.core.AccessManagementUiState
import za.org.rtc.community.core.AdminAccountLookupPurpose
import za.org.rtc.community.core.AdminAnalyticsPeriod
import za.org.rtc.community.core.AdminAnalyticsUiState
import za.org.rtc.community.core.CommunityAlertCategory
import za.org.rtc.community.core.CommunityAlertState
import za.org.rtc.community.core.OperationsUiState
import za.org.rtc.community.data.RtcRepository

internal class RtcAdministrationCoordinator(
    private val repository: RtcRepository,
    private val scope: CoroutineScope,
    private val showGlobalMessage: (String, Boolean, Boolean) -> Unit,
) {
    private val _aiUi = MutableStateFlow(AiUiState())
    val aiUi = _aiUi.asStateFlow()
    private val _accessManagementUi = MutableStateFlow(AccessManagementUiState())
    val accessManagementUi = _accessManagementUi.asStateFlow()
    private val _adminAnalyticsUi = MutableStateFlow(AdminAnalyticsUiState())
    val adminAnalyticsUi = _adminAnalyticsUi.asStateFlow()
    private val _operationsUi = MutableStateFlow(OperationsUiState())
    val operationsUi = _operationsUi.asStateFlow()
    private val _workPreferencesUi = MutableStateFlow(WorkflowSubmissionUiState())
    val workPreferencesUi = _workPreferencesUi.asStateFlow()

    fun searchAccessManagedAccount(email: String) {
        scope.launch {
            _accessManagementUi.value = AccessManagementUiState(isWorking = true)
            repository.searchAccessManagedAccount(email)
                .onSuccess { account ->
                    _accessManagementUi.value = AccessManagementUiState(
                        message = if (account == null) "No verified RTC Community account was found for that email." else null,
                        isSuccess = account != null,
                    )
                }
                .onFailure { error ->
                    _accessManagementUi.value = AccessManagementUiState(
                        message = SafeUiError.generic(error, "The account search could not be completed.")
                    )
                }
        }
    }

    fun saveAccessRoleAssignment(targetUserId: String, requestedRole: String, reason: String) {
        scope.launch {
            _accessManagementUi.value = AccessManagementUiState(isWorking = true)
            repository.saveAccessRoleAssignment(targetUserId, requestedRole, reason)
                .onSuccess { message ->
                    repository.clearAccessManagedAccount()
                    _accessManagementUi.value = AccessManagementUiState(message = message, isSuccess = true)
                }
                .onFailure { error ->
                    _accessManagementUi.value = AccessManagementUiState(
                        message = SafeUiError.generic(error, "The role change could not be saved.")
                    )
                }
        }
    }

    fun decideAccessRoleChangeRequest(requestId: String, approve: Boolean, decisionReason: String? = null) {
        scope.launch {
            _accessManagementUi.value = AccessManagementUiState(isWorking = true)
            repository.decideAccessRoleChangeRequest(requestId, approve, decisionReason)
                .onSuccess { message ->
                    _accessManagementUi.value = AccessManagementUiState(message = message, isSuccess = true)
                }
                .onFailure { error ->
                    _accessManagementUi.value = AccessManagementUiState(
                        message = SafeUiError.generic(error, "The access request could not be decided.")
                    )
                }
        }
    }

    fun refreshAccessManagement() {
        scope.launch {
            _accessManagementUi.value = AccessManagementUiState(isWorking = true)
            repository.refreshAccessManagement()
                .onSuccess { _accessManagementUi.value = AccessManagementUiState() }
                .onFailure { error ->
                    _accessManagementUi.value = AccessManagementUiState(
                        message = SafeUiError.generic(error, "Access Management could not be refreshed.")
                    )
                }
        }
    }

    fun clearAccessManagedAccount() = repository.clearAccessManagedAccount()

    fun dismissAccessManagementMessage() {
        _accessManagementUi.value = AccessManagementUiState()
    }

    fun refreshAdminPrivacyAnalytics(period: AdminAnalyticsPeriod) {
        scope.launch {
            _adminAnalyticsUi.value = AdminAnalyticsUiState(isWorking = true)
            repository.refreshAdminPrivacyAnalytics(period)
                .onSuccess { _adminAnalyticsUi.value = AdminAnalyticsUiState() }
                .onFailure { error ->
                    _adminAnalyticsUi.value = AdminAnalyticsUiState(
                        message = SafeUiError.generic(error, "Privacy Analytics could not be refreshed.")
                    )
                }
        }
    }

    fun lookupAdminPrivacyAccount(email: String, purpose: AdminAccountLookupPurpose, explanation: String) {
        scope.launch {
            _adminAnalyticsUi.value = AdminAnalyticsUiState(isWorking = true)
            repository.adminPrivacyExactAccountLookup(email, purpose.wireValue, explanation)
                .onSuccess {
                    _adminAnalyticsUi.value = AdminAnalyticsUiState(
                        message = "The purpose-controlled account lookup was recorded in the immutable audit trail.",
                        isSuccess = true,
                    )
                }
                .onFailure { error ->
                    _adminAnalyticsUi.value = AdminAnalyticsUiState(
                        message = SafeUiError.generic(error, "The account lookup could not be completed.")
                    )
                }
        }
    }

    fun clearAdminPrivacyAccountLookup() = repository.clearAdminPrivacyAccountLookup()

    fun dismissAdminPrivacyAnalyticsMessage() {
        _adminAnalyticsUi.value = AdminAnalyticsUiState()
    }

    fun refreshOperationsHub() {
        scope.launch {
            _operationsUi.value = OperationsUiState(isWorking = true)
            repository.refreshOperationsHub()
                .onSuccess { _operationsUi.value = OperationsUiState() }
                .onFailure { error ->
                    _operationsUi.value = OperationsUiState(
                        message = SafeUiError.generic(error, "Operations Hub could not be refreshed.")
                    )
                }
        }
    }

    fun saveStaffWorkPreferences(queueOrder: List<String>, assignedWorkNotifications: Boolean, availabilityStatus: String) {
        scope.launch {
            _workPreferencesUi.value = WorkflowSubmissionUiState(isWorking = true)
            repository.saveStaffWorkPreferences(queueOrder, assignedWorkNotifications, availabilityStatus)
                .onSuccess {
                    _workPreferencesUi.value = WorkflowSubmissionUiState(
                        message = "Work preferences saved.",
                        isSuccess = true,
                    )
                }
                .onFailure { error ->
                    _workPreferencesUi.value = WorkflowSubmissionUiState(
                        message = SafeUiError.generic(error, "Work preferences could not be saved.")
                    )
                }
        }
    }

    fun dismissWorkPreferencesMessage() {
        _workPreferencesUi.value = WorkflowSubmissionUiState()
    }

    fun claimOperationsWorkItem(workItemId: String) = executeOperationsAction {
        repository.claimOperationsWorkItem(workItemId)
    }

    fun releaseOperationsWorkItem(workItemId: String, reason: String) = executeOperationsAction {
        repository.releaseOperationsWorkItem(workItemId, reason)
    }

    fun markOperationsWorkReadyForReview(workItemId: String, note: String) = executeOperationsAction {
        repository.markOperationsWorkReadyForReview(workItemId, note)
    }

    fun reassignOperationsWorkItem(workItemId: String, ownerId: String, reason: String) = executeOperationsAction {
        repository.reassignOperationsWorkItem(workItemId, ownerId, reason)
    }

    fun createOperationalIncident(title: String, impactSummary: String, severity: String) = executeOperationsAction {
        repository.createOperationalIncident(title, impactSummary, severity).map { Unit }
    }

    fun updateOperationalIncident(incidentId: String, state: String, closingSummary: String? = null) = executeOperationsAction {
        repository.updateOperationalIncident(incidentId, state, closingSummary)
    }

    fun decideModerationReport(reportId: String, decision: String, reason: String) = executeOperationsAction {
        repository.decideModerationReport(reportId, decision, reason)
    }

    fun decideModerationAppeal(appealId: String, decision: String, reason: String) = executeOperationsAction {
        repository.decideModerationAppeal(appealId, decision, reason)
    }

    fun createEditorialNoticeDraft(title: String, body: String, category: String, safetySensitive: Boolean) {
        scope.launch {
            _operationsUi.value = OperationsUiState(isWorking = true)
            repository.createEditorialNoticeDraft(title, body, category, safetySensitive)
                .onSuccess {
                    _operationsUi.value = OperationsUiState(
                        message = "Draft created. Submit it when it is ready for review.",
                        isSuccess = true,
                    )
                }
                .onFailure { error ->
                    _operationsUi.value = OperationsUiState(
                        message = SafeUiError.generic(error, "The notice draft could not be created.")
                    )
                }
        }
    }

    fun submitEditorialNotice(noticeId: String, note: String = "Submitted for review.") = executeOperationsAction {
        repository.submitEditorialNotice(noticeId, note)
    }

    fun reviewEditorialNotice(
        noticeId: String,
        outcome: String,
        note: String,
        publishMode: String = "PUBLISH",
        scheduledAt: String? = null,
    ) = executeOperationsAction {
        repository.reviewEditorialNotice(noticeId, outcome, note, publishMode, scheduledAt)
    }

    fun retireEditorialNotice(noticeId: String, reason: String) = executeOperationsAction {
        repository.retireEditorialNotice(noticeId, reason)
    }

    fun setOperationalControl(
        controlType: String,
        enabled: Boolean,
        reason: String,
        displayMessage: String,
        expiresAt: String?,
        auditNote: String,
        confirmation: String,
    ) {
        scope.launch {
            _operationsUi.value = OperationsUiState(isWorking = true)
            repository.setOperationalControl(
                controlType,
                enabled,
                reason,
                displayMessage,
                expiresAt,
                auditNote,
                confirmation,
            ).onSuccess {
                _operationsUi.value = OperationsUiState(
                    message = "The protected control was updated and recorded.",
                    isSuccess = true,
                )
            }.onFailure { error ->
                _operationsUi.value = OperationsUiState(
                    message = SafeUiError.generic(error, "The protected control could not be updated.")
                )
            }
        }
    }

    fun refreshAdministrativeActivity(category: String? = null) = executeOperationsAction {
        repository.refreshAdministrativeActivity(category)
    }

    fun dismissOperationsMessage() {
        _operationsUi.value = OperationsUiState()
    }

    private fun executeOperationsAction(action: suspend () -> Result<Unit>) {
        scope.launch {
            _operationsUi.value = OperationsUiState(isWorking = true)
            action()
                .onSuccess { _operationsUi.value = OperationsUiState(isSuccess = true) }
                .onFailure { error ->
                    _operationsUi.value = OperationsUiState(
                        message = SafeUiError.generic(error, "The operation could not be completed.")
                    )
                }
        }
    }

    fun submitNotice(title: String, summary: String) {
        scope.launch {
            repository.submitNotice(title, summary)
                .onFailure {
                    showGlobalMessage(
                        SafeUiError.generic(it, "Notice could not be submitted."),
                        false,
                        false,
                    )
                }
        }
    }

    fun createCommunityAlert(
        category: CommunityAlertCategory,
        title: String,
        summary: String,
        body: String,
        scheduledAt: String? = null,
        expiresAt: String? = null,
        state: CommunityAlertState = CommunityAlertState.ORIGINAL,
        originalAlertId: String? = null,
        correctionReason: String? = null,
        publishConfirmation: String,
        emergencyConfirmation: String? = null,
        emergencyReason: String? = null,
    ) {
        scope.launch {
            showGlobalMessage("", false, true)
            repository.createCommunityAlert(
                category,
                title,
                summary,
                body,
                scheduledAt,
                expiresAt,
                state,
                originalAlertId,
                correctionReason,
                publishConfirmation,
                emergencyConfirmation,
                emergencyReason,
            ).onSuccess {
                showGlobalMessage(
                    if (scheduledAt == null) "Community alert published." else "Community alert scheduled.",
                    true,
                    false,
                )
            }.onFailure {
                showGlobalMessage(
                    SafeUiError.generic(it, "Community alert could not be created."),
                    false,
                    false,
                )
            }
        }
    }

    fun proposeAiAction(prompt: String) {
        scope.launch {
            _aiUi.value = AiUiState(isWorking = true)
            repository.createAiProposal(prompt)
                .onSuccess {
                    _aiUi.value = AiUiState(
                        message = "Proposal prepared for review. No production change has occurred.",
                        isSuccess = true,
                    )
                }
                .onFailure {
                    _aiUi.value = AiUiState(
                        message = SafeUiError.generic(
                            it,
                            "RTC AI could not prepare a proposal. No content was changed.",
                        )
                    )
                }
        }
    }

    fun discardAiProposal(proposalId: String) = repository.discardAiProposal(proposalId)

    fun confirmAiProposal(proposalId: String) {
        scope.launch {
            _aiUi.value = AiUiState(isWorking = true)
            repository.confirmAiProposal(proposalId)
                .onSuccess { draftsCreated ->
                    _aiUi.value = AiUiState(
                        message = "$draftsCreated guarded draft(s) created by the audited server transaction.",
                        isSuccess = true,
                    )
                }
                .onFailure {
                    _aiUi.value = AiUiState(
                        message = SafeUiError.generic(
                            it,
                            "The proposal could not be confirmed. No content was changed.",
                        )
                    )
                }
        }
    }

    fun dismissAiMessage() {
        _aiUi.value = AiUiState()
    }
}

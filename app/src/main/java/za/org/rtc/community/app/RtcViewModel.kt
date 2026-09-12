package za.org.rtc.community.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.core.AdminAccountLookupPurpose
import za.org.rtc.community.core.AdminAnalyticsPeriod
import za.org.rtc.community.core.CommunityAlertCategory
import za.org.rtc.community.core.CommunityAlertState
import za.org.rtc.community.core.DraftArea
import za.org.rtc.community.core.ModerationReason
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.data.RtcRepository
import javax.inject.Inject

/**
 * Compatibility facade for the configured application root.
 *
 * Feature and workflow implementation belongs to the scoped coordinators below. This class keeps
 * the stable screen-facing API while preventing authentication, administration, resident workflow,
 * Firebase, and safe-error implementation from reconverging into one application god-object.
 */
@HiltViewModel
class RtcViewModel @Inject constructor(
    private val repository: RtcRepository,
    private val supabase: SupabaseClient,
    @ApplicationContext applicationContext: Context,
) : ViewModel() {
    val session = repository.session
    val notifications = repository.notifications
    val cases = repository.cases
    val supportCaseMessages = repository.supportCaseMessages
    val assignedSupportCases = repository.assignedSupportCases
    val posts = repository.posts
    val communityPostDetail = repository.communityPostDetail
    val communityComments = repository.communityComments
    val communityGuidelinesAccepted = repository.communityGuidelinesAccepted
    val isInteractiveTutorialVisible = repository.isInteractiveTutorialVisible
    fun showInteractiveTutorial() = repository.showInteractiveTutorial()
    fun dismissInteractiveTutorial(markCompleted: Boolean = true) = repository.dismissInteractiveTutorial(markCompleted)
    val communityAlerts = repository.communityAlerts
    val communityAlertDetail = repository.communityAlertDetail
    val communityAlertDashboard = repository.communityAlertDashboard
    val notices = repository.notices
    val events = repository.events
    fun toggleEventRsvp(eventId: String) {
        viewModelScope.launch { repository.toggleEventRsvp(eventId) }
    }
    val helpArticles = repository.helpArticles
    val workQueue = repository.workQueue
    val aiProposals = repository.aiProposals
    val drafts = repository.drafts
    val pendingSyncCount = repository.pendingSyncCount
    val dashboardMetrics = repository.dashboardMetrics
    val projectsPage = repository.projectsPage
    val centresPage = repository.centresPage
    val opportunitiesPage = repository.opportunitiesPage
    val publicSearchResults = repository.publicSearchResults
    val isLiveContentLoading = repository.isLiveContentLoading
    val liveContentMessage = repository.liveContentMessage
        .map(::sanitizeLiveContentMessage)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accessManagedAccount = repository.accessManagedAccount
    val accessRoleChangeRequests = repository.accessRoleChangeRequests
    val accessRoleAuditEvents = repository.accessRoleAuditEvents
    val adminAnalyticsDashboard = repository.adminAnalyticsDashboard
    val adminAnalyticsLocalities = repository.adminAnalyticsLocalities
    val adminAnalyticsAccountProfile = repository.adminAnalyticsAccountProfile
    val adminAnalyticsAuditEvents = repository.adminAnalyticsAuditEvents
    val staffWorkPreferences = repository.staffWorkPreferences
    val operationsWorkQueue = repository.operationsWorkQueue
    val operationsControls = repository.operationsControls
    val operationalIncidents = repository.operationalIncidents
    val systemHealth = repository.systemHealth
    val administrativeActivity = repository.administrativeActivity
    val moderationQueue = repository.moderationQueue
    val moderationAppeals = repository.moderationAppeals
    val editorialNotices = repository.editorialNotices

    private val authenticationCoordinator = RtcAuthenticationCoordinator(
        repository = repository,
        supabase = supabase,
        applicationContext = applicationContext,
        scope = viewModelScope,
        onStaffAuthenticated = { administrationCoordinator.refreshOperationsHub() },
    )
    private val administrationCoordinator: RtcAdministrationCoordinator by lazy {
        RtcAdministrationCoordinator(repository, viewModelScope, authenticationCoordinator::showGlobalMessage)
    }
    private val residentCoordinator by lazy {
        RtcResidentCoordinator(repository, viewModelScope, authenticationCoordinator::showGlobalMessage)
    }

    val authenticationUi get() = authenticationCoordinator.authenticationUi
    val isSessionRestoring get() = authenticationCoordinator.isSessionRestoring
    val administratorMfaUi get() = authenticationCoordinator.administratorMfaUi
    val passwordUi get() = authenticationCoordinator.passwordUi
    val passwordRecoveryActive get() = authenticationCoordinator.passwordRecoveryActive
    val notificationPermissionPrompt get() = authenticationCoordinator.notificationPermissionPrompt
    val declaredLocalityUi get() = authenticationCoordinator.declaredLocalityUi
    val feedbackUi get() = authenticationCoordinator.feedbackUi
    val aiUi get() = administrationCoordinator.aiUi
    val accessManagementUi get() = administrationCoordinator.accessManagementUi
    val adminAnalyticsUi get() = administrationCoordinator.adminAnalyticsUi
    val operationsUi get() = administrationCoordinator.operationsUi
    val workPreferencesUi get() = administrationCoordinator.workPreferencesUi
    val pendingCommunityAlertId get() = residentCoordinator.pendingCommunityAlertId
    val pendingCommunityPostId get() = residentCoordinator.pendingCommunityPostId
    val communityActionUi get() = residentCoordinator.communityActionUi
    val supportUi get() = residentCoordinator.supportUi
    val assignedSupportCaseUi get() = residentCoordinator.assignedSupportCaseUi

    init {
        viewModelScope.launch {
            authenticationCoordinator.restoreSession()
            // Authenticated Community views must refresh only after saved-session restoration.
            repository.refreshLiveContent()
            if (repository.session.value.role.isStaff) administrationCoordinator.refreshOperationsHub()
            authenticationCoordinator.registerCurrentFcmToken()
        }
    }

    fun consumeNotificationPermissionPrompt() = authenticationCoordinator.consumeNotificationPermissionPrompt()
    fun signInWithEmail(email: String, password: String) = authenticationCoordinator.signInWithEmail(email, password)
    fun signInWithGoogleIdToken(idToken: String, nonce: String) = authenticationCoordinator.signInWithGoogleIdToken(idToken, nonce)
    fun reportGoogleSignInError(message: String) = authenticationCoordinator.reportGoogleSignInError(message)
    fun signUpWithEmail(email: String, password: String, displayName: String) =
        authenticationCoordinator.signUpWithEmail(email, password, displayName)
    fun dismissAuthenticationMessage() = authenticationCoordinator.dismissAuthenticationMessage()
    fun beginDevelopmentResidentSession() {
        if (!BuildConfig.DEBUG) return
        switchRole(UserRole.RESIDENT_A)
    }
    fun requestPasswordRecovery(email: String) = authenticationCoordinator.requestPasswordRecovery(email)
    fun beginPasswordRecovery() = authenticationCoordinator.beginPasswordRecovery()
    fun updatePassword(newPassword: String, currentPassword: String? = null) =
        authenticationCoordinator.updatePassword(newPassword, currentPassword)
    fun dismissPasswordUi() = authenticationCoordinator.dismissPasswordUi()
    fun finishPasswordRecovery() = authenticationCoordinator.finishPasswordRecovery()
    fun signOutToPublicWelcome() = authenticationCoordinator.signOutToPublicWelcome()
    fun enrollSystemAdministratorTotp() = authenticationCoordinator.enrollSystemAdministratorTotp()
    fun verifySystemAdministratorTotp(factorId: String?, code: String) =
        authenticationCoordinator.verifySystemAdministratorTotp(factorId, code)
    fun dismissAdministratorMfaUi() = authenticationCoordinator.dismissAdministratorMfaUi()
    fun uploadProfilePhoto(uri: Uri) = authenticationCoordinator.uploadProfilePhoto(uri)
    fun deleteProfilePhoto() = authenticationCoordinator.deleteProfilePhoto()
    fun switchRole(role: UserRole) = authenticationCoordinator.switchRole(role)
    fun toggleReadingMode() = authenticationCoordinator.toggleReadingMode()
    fun setTheme(preference: ThemePreference) = authenticationCoordinator.setTheme(preference)
    fun setDynamicColor(enabled: Boolean) = authenticationCoordinator.setDynamicColor(enabled)
    fun updateProfile(displayName: String, bio: String, interests: List<String>) =
        authenticationCoordinator.updateProfile(displayName, bio, interests)
    fun setNotificationPreference(kind: String, enabled: Boolean) =
        authenticationCoordinator.setNotificationPreference(kind, enabled)
    fun saveDeclaredLocality(locality: String?) = authenticationCoordinator.saveDeclaredLocality(locality)
    fun dismissDeclaredLocalityMessage() = authenticationCoordinator.dismissDeclaredLocalityMessage()
    fun submitFeedback(message: String, screenshotUri: Uri? = null) =
        authenticationCoordinator.submitFeedback(message, screenshotUri)
    fun dismissFeedbackMessage() = authenticationCoordinator.dismissFeedbackMessage()

    fun searchAccessManagedAccount(email: String) = administrationCoordinator.searchAccessManagedAccount(email)
    fun saveAccessRoleAssignment(targetUserId: String, requestedRole: String, reason: String) =
        administrationCoordinator.saveAccessRoleAssignment(targetUserId, requestedRole, reason)
    fun decideAccessRoleChangeRequest(requestId: String, approve: Boolean, decisionReason: String? = null) =
        administrationCoordinator.decideAccessRoleChangeRequest(requestId, approve, decisionReason)
    fun refreshAccessManagement() = administrationCoordinator.refreshAccessManagement()
    fun clearAccessManagedAccount() = administrationCoordinator.clearAccessManagedAccount()
    fun dismissAccessManagementMessage() = administrationCoordinator.dismissAccessManagementMessage()
    fun refreshAdminPrivacyAnalytics(period: AdminAnalyticsPeriod) =
        administrationCoordinator.refreshAdminPrivacyAnalytics(period)
    fun lookupAdminPrivacyAccount(email: String, purpose: AdminAccountLookupPurpose, explanation: String) =
        administrationCoordinator.lookupAdminPrivacyAccount(email, purpose, explanation)
    fun clearAdminPrivacyAccountLookup() = administrationCoordinator.clearAdminPrivacyAccountLookup()
    fun dismissAdminPrivacyAnalyticsMessage() = administrationCoordinator.dismissAdminPrivacyAnalyticsMessage()
    fun refreshOperationsHub() = administrationCoordinator.refreshOperationsHub()
    fun saveStaffWorkPreferences(
        queueOrder: List<String>,
        assignedWorkNotifications: Boolean,
        availabilityStatus: String,
    ) = administrationCoordinator.saveStaffWorkPreferences(queueOrder, assignedWorkNotifications, availabilityStatus)
    fun dismissWorkPreferencesMessage() = administrationCoordinator.dismissWorkPreferencesMessage()
    fun claimOperationsWorkItem(workItemId: String) = administrationCoordinator.claimOperationsWorkItem(workItemId)
    fun releaseOperationsWorkItem(workItemId: String, reason: String) =
        administrationCoordinator.releaseOperationsWorkItem(workItemId, reason)
    fun markOperationsWorkReadyForReview(workItemId: String, note: String) =
        administrationCoordinator.markOperationsWorkReadyForReview(workItemId, note)
    fun reassignOperationsWorkItem(workItemId: String, ownerId: String, reason: String) =
        administrationCoordinator.reassignOperationsWorkItem(workItemId, ownerId, reason)
    fun createOperationalIncident(title: String, impactSummary: String, severity: String) =
        administrationCoordinator.createOperationalIncident(title, impactSummary, severity)
    fun updateOperationalIncident(incidentId: String, state: String, closingSummary: String? = null) =
        administrationCoordinator.updateOperationalIncident(incidentId, state, closingSummary)
    fun decideModerationReport(reportId: String, decision: String, reason: String) =
        administrationCoordinator.decideModerationReport(reportId, decision, reason)
    fun decideModerationAppeal(appealId: String, decision: String, reason: String) =
        administrationCoordinator.decideModerationAppeal(appealId, decision, reason)
    fun createEditorialNoticeDraft(title: String, body: String, category: String, safetySensitive: Boolean) =
        administrationCoordinator.createEditorialNoticeDraft(title, body, category, safetySensitive)
    fun submitEditorialNotice(noticeId: String, note: String = "Submitted for review.") =
        administrationCoordinator.submitEditorialNotice(noticeId, note)
    fun reviewEditorialNotice(
        noticeId: String,
        outcome: String,
        note: String,
        publishMode: String = "PUBLISH",
        scheduledAt: String? = null,
    ) = administrationCoordinator.reviewEditorialNotice(noticeId, outcome, note, publishMode, scheduledAt)
    fun retireEditorialNotice(noticeId: String, reason: String) =
        administrationCoordinator.retireEditorialNotice(noticeId, reason)
    fun setOperationalControl(
        controlType: String,
        enabled: Boolean,
        reason: String,
        displayMessage: String,
        expiresAt: String?,
        auditNote: String,
        confirmation: String,
    ) = administrationCoordinator.setOperationalControl(
        controlType,
        enabled,
        reason,
        displayMessage,
        expiresAt,
        auditNote,
        confirmation,
    )
    fun refreshAdministrativeActivity(category: String? = null) =
        administrationCoordinator.refreshAdministrativeActivity(category)
    fun dismissOperationsMessage() = administrationCoordinator.dismissOperationsMessage()
    fun submitNotice(title: String, summary: String) = administrationCoordinator.submitNotice(title, summary)
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
    ) = administrationCoordinator.createCommunityAlert(
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
    )
    fun proposeAiAction(prompt: String) = administrationCoordinator.proposeAiAction(prompt)
    fun discardAiProposal(proposalId: String) = administrationCoordinator.discardAiProposal(proposalId)
    fun confirmAiProposal(proposalId: String) = administrationCoordinator.confirmAiProposal(proposalId)
    fun dismissAiMessage() = administrationCoordinator.dismissAiMessage()

    fun openCommunityAlertFromSystemNotification(alertId: String?) =
        residentCoordinator.openCommunityAlertFromSystemNotification(alertId)
    fun consumePendingCommunityAlert() = residentCoordinator.consumePendingCommunityAlert()
    fun openCommunityPostFromDeepLink(postId: String?) = residentCoordinator.openCommunityPostFromDeepLink(postId)
    fun consumePendingCommunityPost() = residentCoordinator.consumePendingCommunityPost()
    fun openPublicReportFromDeepLink(reportId: String?) = residentCoordinator.openPublicReportFromDeepLink(reportId)
    fun consumePendingPublicReport() = residentCoordinator.consumePendingPublicReport()
    fun refreshLiveContent() = residentCoordinator.refreshLiveContent()
    fun loadMoreProjects() = residentCoordinator.loadMoreProjects()
    fun loadMoreCentres() = residentCoordinator.loadMoreCentres()
    fun loadMoreOpportunities() = residentCoordinator.loadMoreOpportunities()
    fun searchPublicContent(query: String) = residentCoordinator.searchPublicContent(query)
    fun clearPublicSearch() = residentCoordinator.clearPublicSearch()
    fun loadCommunityPostDetail(postId: String) = residentCoordinator.loadCommunityPostDetail(postId)
    fun acceptCommunityGuidelines() = residentCoordinator.acceptCommunityGuidelines()
    fun dismissCommunityActionUi() = residentCoordinator.dismissCommunityActionUi()
    fun createCommunityComment(postId: String, body: String) = residentCoordinator.createCommunityComment(postId, body)
    fun toggleCommunityPostLike(postId: String) = residentCoordinator.toggleCommunityPostLike(postId)
    fun updateCommunityComment(postId: String, commentId: String, body: String) =
        residentCoordinator.updateCommunityComment(postId, commentId, body)
    fun deleteCommunityComment(postId: String, commentId: String) =
        residentCoordinator.deleteCommunityComment(postId, commentId)
    fun dismissLiveContentMessage() = residentCoordinator.dismissLiveContentMessage()
    fun markNotificationsRead() = residentCoordinator.markNotificationsRead()
    fun loadCommunityAlertDetail(alertId: String) = residentCoordinator.loadCommunityAlertDetail(alertId)
    fun saveDraft(area: DraftArea, title: String = "", body: String) = residentCoordinator.saveDraft(area, title, body)
    fun discardDraft(area: DraftArea) = residentCoordinator.discardDraft(area)
    fun reportCommunityPost(postId: String, reason: ModerationReason, detail: String) =
        residentCoordinator.reportCommunityPost(postId, reason, detail)
    fun createPost(text: String, mediaUris: List<Uri> = emptyList()) = residentCoordinator.createPost(text, mediaUris)
    fun submitSupportRequest(title: String, detail: String) = residentCoordinator.submitSupportRequest(title, detail)
    fun loadSupportCaseMessages(caseId: String) = residentCoordinator.loadSupportCaseMessages(caseId)
    fun refreshAssignedSupportCases() = residentCoordinator.refreshAssignedSupportCases()
    fun updateAssignedSupportCaseState(caseId: String, state: String, note: String) =
        residentCoordinator.updateAssignedSupportCaseState(caseId, state, note)
    fun dismissAssignedSupportCaseMessage() = residentCoordinator.dismissAssignedSupportCaseMessage()
    fun addSupportCaseMessage(caseId: String, body: String) = residentCoordinator.addSupportCaseMessage(caseId, body)
    fun dismissSupportMessage() = residentCoordinator.dismissSupportMessage()
    suspend fun refreshCommunityMediaUrl(mediaId: String): String? = residentCoordinator.refreshCommunityMediaUrl(mediaId)

    private fun sanitizeLiveContentMessage(message: String?): String? = when {
        message?.startsWith("Live community information could not be refreshed:") == true ->
            "Live community information could not be refreshed. Check your connection and try again."
        message?.startsWith("Community conversation could not be loaded:") == true ->
            "Community conversation could not be loaded. Check your connection and try again."
        else -> message
    }
}

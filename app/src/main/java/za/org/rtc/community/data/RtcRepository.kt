package za.org.rtc.community.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.core.AccessManagedAccount
import za.org.rtc.community.core.AccessRoleAuditEvent
import za.org.rtc.community.core.AccessRoleChangeRequest
import za.org.rtc.community.core.AdminAccountProfile
import za.org.rtc.community.core.AdminAnalyticsDashboard
import za.org.rtc.community.core.AdminAnalyticsPeriod
import za.org.rtc.community.core.AdminAuditTrailEvent
import za.org.rtc.community.core.AdminLocalitySummary
import za.org.rtc.community.core.AdministrativeActivityEvent
import za.org.rtc.community.core.AssignedSupportCase
import za.org.rtc.community.core.EditorialNoticeRecord
import za.org.rtc.community.core.ModerationAppeal
import za.org.rtc.community.core.ModerationQueueItem
import za.org.rtc.community.core.OperationsControlState
import za.org.rtc.community.core.OperationalIncident
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.core.SystemHealthStatus
import za.org.rtc.community.core.StaffWorkPreferences
import za.org.rtc.community.core.AiProposal
import za.org.rtc.community.core.CentreRecord
import za.org.rtc.community.core.DashboardMetrics
import za.org.rtc.community.core.DirectoryPage
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.core.PublicSearchResult
import za.org.rtc.community.core.CaseStage
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.CommunityAlert
import za.org.rtc.community.core.CommunityAlertCategory
import za.org.rtc.community.core.CommunityAlertDashboardItem
import za.org.rtc.community.core.CommunityAlertState
import za.org.rtc.community.core.DraftArea
import za.org.rtc.community.core.HelpArticle
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.core.ModerationReason
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.RtcNotification
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.core.SupportCaseMessage
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.core.WorkQueueItem
import za.org.rtc.community.supabase.ProductionUxRepository
import za.org.rtc.community.data.local.RtcDatabase
import za.org.rtc.community.data.local.LocalDraftEntity
import za.org.rtc.community.data.local.LocalDraftDao
import za.org.rtc.community.data.local.InstallationIdentity
import za.org.rtc.community.data.local.UserPreferencesStore
import za.org.rtc.community.data.local.CommunityUploadWorker
import za.org.rtc.community.data.local.CachedPostEntity
import za.org.rtc.community.data.local.CachedCommentEntity
import za.org.rtc.community.data.local.CachedUserProfileEntity
import za.org.rtc.community.data.local.CachedSessionEntity
import za.org.rtc.community.data.local.CachedReportEntity
import za.org.rtc.community.feature.community.toCachedEntity
import za.org.rtc.community.feature.community.toCommunityPost
import za.org.rtc.community.feature.community.toCommunityComment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import za.org.rtc.community.feature.community.CommunityMockData
import java.util.UUID

data class AdministratorTotpEnrollment(
    val factorId: String,
    val secret: String,
    val uri: String,
)

@Singleton
class RtcRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val productionUxRepository: ProductionUxRepository,
    private val communityEventsRepository: za.org.rtc.community.feature.events.domain.CommunityEventsRepository,
    private val database: RtcDatabase,
    private val localDraftDao: LocalDraftDao,
    private val installationIdentity: InstallationIdentity,
    private val preferencesStore: UserPreferencesStore,
    private val workManager: WorkManager,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val events: StateFlow<List<za.org.rtc.community.feature.events.domain.CommunityEvent>> =
        (communityEventsRepository as? za.org.rtc.community.feature.events.data.remote.SupabaseCommunityEventsRepository)?.eventsFlow
            ?: MutableStateFlow(za.org.rtc.community.feature.events.domain.SampleCommunityEvents).asStateFlow()

    suspend fun toggleEventRsvp(eventId: String) {
        communityEventsRepository.toggleRsvp(eventId)
    }
    private val _session = MutableStateFlow(
        RtcSession(
            id = installationIdentity.id,
            displayName = "Community member",
            handle = "@community",
            role = UserRole.ANONYMOUS_PUBLIC,
            onboardingComplete = true,
            authority = SessionAuthority.PUBLIC,
        )
    )
    val session: StateFlow<RtcSession> = _session.asStateFlow()

    private val _notifications = MutableStateFlow<List<RtcNotification>>(emptyList())
    val notifications: StateFlow<List<RtcNotification>> = _notifications.asStateFlow()
    private val _communityAlerts = MutableStateFlow<List<CommunityAlert>>(emptyList())
    val communityAlerts: StateFlow<List<CommunityAlert>> = _communityAlerts.asStateFlow()
    private val _communityAlertDetail = MutableStateFlow<CommunityAlert?>(null)
    val communityAlertDetail: StateFlow<CommunityAlert?> = _communityAlertDetail.asStateFlow()
    private val _communityAlertDashboard = MutableStateFlow<List<CommunityAlertDashboardItem>>(emptyList())
    val communityAlertDashboard: StateFlow<List<CommunityAlertDashboardItem>> = _communityAlertDashboard.asStateFlow()

    private val _cases = MutableStateFlow<List<SupportCase>>(emptyList())
    val cases: StateFlow<List<SupportCase>> = _cases.asStateFlow()
    private val _supportCaseMessages = MutableStateFlow<List<SupportCaseMessage>>(emptyList())
    val supportCaseMessages: StateFlow<List<SupportCaseMessage>> = _supportCaseMessages.asStateFlow()
    private val _assignedSupportCases = MutableStateFlow<List<AssignedSupportCase>>(emptyList())
    val assignedSupportCases: StateFlow<List<AssignedSupportCase>> = _assignedSupportCases.asStateFlow()

    private val _posts = MutableStateFlow<List<CommunityPost>>(emptyList())
    val posts: StateFlow<List<CommunityPost>> = _posts.asStateFlow()
    private val _communityPostDetail = MutableStateFlow<CommunityPost?>(null)
    val communityPostDetail: StateFlow<CommunityPost?> = _communityPostDetail.asStateFlow()
    private val _communityComments = MutableStateFlow<List<CommunityComment>>(emptyList())
    val communityComments: StateFlow<List<CommunityComment>> = _communityComments.asStateFlow()
    private val _communityGuidelinesAccepted = MutableStateFlow<Boolean?>(null)
    val communityGuidelinesAccepted: StateFlow<Boolean?> = _communityGuidelinesAccepted.asStateFlow()
    private val _isInteractiveTutorialVisible = MutableStateFlow(false)
    val isInteractiveTutorialVisible: StateFlow<Boolean> = _isInteractiveTutorialVisible.asStateFlow()

    private val _notices = MutableStateFlow<List<OfficialNotice>>(emptyList())
    val notices: StateFlow<List<OfficialNotice>> = _notices.asStateFlow()

    private val _helpArticles = MutableStateFlow<List<HelpArticle>>(emptyList())
    val helpArticles: StateFlow<List<HelpArticle>> = _helpArticles.asStateFlow()

    private val _workQueue = MutableStateFlow<List<WorkQueueItem>>(emptyList())
    val workQueue: StateFlow<List<WorkQueueItem>> = _workQueue.asStateFlow()

    private val _aiProposals = MutableStateFlow<List<AiProposal>>(emptyList())
    val aiProposals: StateFlow<List<AiProposal>> = _aiProposals.asStateFlow()

    private val _drafts = MutableStateFlow<List<LocalDraft>>(emptyList())
    val drafts: StateFlow<List<LocalDraft>> = _drafts.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    init {
        repositoryScope.launch {
            preferencesStore.preferences.collectLatest { prefs ->
                _session.value = _session.value.copy(
                    darkMode = prefs.theme,
                    dynamicColor = prefs.dynamicColor,
                    readingMode = prefs.simplifiedReading
                )
            }
        }
        repositoryScope.launch {
            supabase.auth.sessionStatus.collectLatest { status ->
                handleSessionStatus(status)
            }
        }
        repositoryScope.launch {
            session.collectLatest { activeSession ->
                _drafts.value = emptyList()
                val ownerUserId = draftOwnerIdOrNull(activeSession) ?: return@collectLatest
                localDraftDao.observeForOwner(ownerUserId).collectLatest { rows ->
                    _drafts.value = rows.mapNotNull { row ->
                        runCatching {
                            LocalDraft(
                                id = row.id,
                                area = DraftArea.valueOf(row.area),
                                title = row.title,
                                body = row.body,
                                savedAt = "Saved locally",
                            )
                        }.getOrNull()
                    }
                }
            }
        }
        repositoryScope.launch {
            session.collectLatest { activeSession ->
                val ownerKey = draftOwnerIdOrNull(activeSession)
                if (ownerKey == null) {
                    _pendingSyncCount.value = 0
                } else {
                    database.uploadOutboxDao().observePendingCountForOwner(ownerKey)
                        .collectLatest { _pendingSyncCount.value = it }
                }
            }
        }
    }

    private val _dashboardMetrics = MutableStateFlow(DashboardMetrics())
    val dashboardMetrics: StateFlow<DashboardMetrics> = _dashboardMetrics.asStateFlow()
    private val _projectsPage = MutableStateFlow(DirectoryPage<ProjectRecord>(canLoadMore = false))
    val projectsPage: StateFlow<DirectoryPage<ProjectRecord>> = _projectsPage.asStateFlow()
    private val _centresPage = MutableStateFlow(DirectoryPage<CentreRecord>(canLoadMore = false))
    val centresPage: StateFlow<DirectoryPage<CentreRecord>> = _centresPage.asStateFlow()
    private val _opportunitiesPage = MutableStateFlow(DirectoryPage<OpportunityRecord>(canLoadMore = false))
    val opportunitiesPage: StateFlow<DirectoryPage<OpportunityRecord>> = _opportunitiesPage.asStateFlow()
    private var directoryGeneration = 0L
    private val _publicSearchResults = MutableStateFlow<List<PublicSearchResult>>(emptyList())
    val publicSearchResults: StateFlow<List<PublicSearchResult>> = _publicSearchResults.asStateFlow()
    private val _isLiveContentLoading = MutableStateFlow(false)
    val isLiveContentLoading: StateFlow<Boolean> = _isLiveContentLoading.asStateFlow()
    private val _liveContentMessage = MutableStateFlow<String?>(null)
    val liveContentMessage: StateFlow<String?> = _liveContentMessage.asStateFlow()
    private val _accessManagedAccount = MutableStateFlow<AccessManagedAccount?>(null)
    val accessManagedAccount: StateFlow<AccessManagedAccount?> = _accessManagedAccount.asStateFlow()
    private val _accessRoleChangeRequests = MutableStateFlow<List<AccessRoleChangeRequest>>(emptyList())
    val accessRoleChangeRequests: StateFlow<List<AccessRoleChangeRequest>> = _accessRoleChangeRequests.asStateFlow()
    private val _accessRoleAuditEvents = MutableStateFlow<List<AccessRoleAuditEvent>>(emptyList())
    val accessRoleAuditEvents: StateFlow<List<AccessRoleAuditEvent>> = _accessRoleAuditEvents.asStateFlow()
    private val _adminAnalyticsDashboard = MutableStateFlow(AdminAnalyticsDashboard())
    val adminAnalyticsDashboard: StateFlow<AdminAnalyticsDashboard> = _adminAnalyticsDashboard.asStateFlow()
    private val _adminAnalyticsLocalities = MutableStateFlow<List<AdminLocalitySummary>>(emptyList())
    val adminAnalyticsLocalities: StateFlow<List<AdminLocalitySummary>> = _adminAnalyticsLocalities.asStateFlow()
    private val _adminAnalyticsAccountProfile = MutableStateFlow<AdminAccountProfile?>(null)
    val adminAnalyticsAccountProfile: StateFlow<AdminAccountProfile?> = _adminAnalyticsAccountProfile.asStateFlow()
    private val _adminAnalyticsAuditEvents = MutableStateFlow<List<AdminAuditTrailEvent>>(emptyList())
    val adminAnalyticsAuditEvents: StateFlow<List<AdminAuditTrailEvent>> = _adminAnalyticsAuditEvents.asStateFlow()
    private val _staffWorkPreferences = MutableStateFlow(StaffWorkPreferences())
    val staffWorkPreferences: StateFlow<StaffWorkPreferences> = _staffWorkPreferences.asStateFlow()
    private val _operationsWorkQueue = MutableStateFlow<List<OperationsWorkItem>>(emptyList())
    val operationsWorkQueue: StateFlow<List<OperationsWorkItem>> = _operationsWorkQueue.asStateFlow()
    private val _operationsControls = MutableStateFlow(OperationsControlState())
    val operationsControls: StateFlow<OperationsControlState> = _operationsControls.asStateFlow()
    private val _operationalIncidents = MutableStateFlow<List<OperationalIncident>>(emptyList())
    val operationalIncidents: StateFlow<List<OperationalIncident>> = _operationalIncidents.asStateFlow()
    private val _systemHealth = MutableStateFlow<List<SystemHealthStatus>>(emptyList())
    val systemHealth: StateFlow<List<SystemHealthStatus>> = _systemHealth.asStateFlow()
    private val _administrativeActivity = MutableStateFlow<List<AdministrativeActivityEvent>>(emptyList())
    val administrativeActivity: StateFlow<List<AdministrativeActivityEvent>> = _administrativeActivity.asStateFlow()
    private val _moderationQueue = MutableStateFlow<List<ModerationQueueItem>>(emptyList())
    val moderationQueue: StateFlow<List<ModerationQueueItem>> = _moderationQueue.asStateFlow()
    private val _moderationAppeals = MutableStateFlow<List<ModerationAppeal>>(emptyList())
    val moderationAppeals: StateFlow<List<ModerationAppeal>> = _moderationAppeals.asStateFlow()
    private val _editorialNotices = MutableStateFlow<List<EditorialNoticeRecord>>(emptyList())
    val editorialNotices: StateFlow<List<EditorialNoticeRecord>> = _editorialNotices.asStateFlow()

    suspend fun restoreSupabaseSession(): Result<Boolean> = runCatching {
        if (supabase.auth.currentUserOrNull() == null) return@runCatching false
        supabase.auth.startAutoRefreshForCurrentSession()
        hydrateSupabaseSession()
        recordPrivacyAnalyticsAppActivity()
        enqueueUploadRecovery()
        true
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        val cleanEmail = email.trim()
        require(cleanEmail.contains('@')) { "Enter a valid staff email address." }
        require(password.isNotBlank()) { "Enter your password." }
        supabase.auth.signInWith(Email) {
            this.email = cleanEmail
            this.password = password
        }
        supabase.auth.startAutoRefreshForCurrentSession()
        hydrateSupabaseSession()
        val current = _session.value
        database.cachedSessionDao().upsertSession(
            CachedSessionEntity(
                userId = current.id,
                email = cleanEmail,
                isLoggedIn = true,
                sessionJson = "",
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
        recordPrivacyAnalyticsAppActivity()
        refreshLiveContent()
        enqueueUploadRecovery()
    }

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): Result<Unit> = runCatching {
        require(idToken.isNotBlank()) { "Google did not return an identity token." }
        require(nonce.isNotBlank()) { "Google Sign-In verification nonce is missing." }
        
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            this.nonce = nonce
        }
        supabase.auth.startAutoRefreshForCurrentSession()
        hydrateSupabaseSession()
        val current = _session.value
        database.cachedSessionDao().upsertSession(
            CachedSessionEntity(
                userId = current.id,
                email = current.authenticatedEmail.orEmpty(),
                isLoggedIn = true,
                sessionJson = "",
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
        recordPrivacyAnalyticsAppActivity()
        refreshLiveContent()
        enqueueUploadRecovery()
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Resident account creation has been removed from RTC."))

    /** The result is intentionally generic so email address ownership is not disclosed. */
    suspend fun requestPasswordRecovery(email: String): Result<Unit> = runCatching {
        require(email.trim().contains('@')) { "Enter a valid email address." }
        supabase.auth.resetPasswordForEmail(email = email.trim(), redirectUrl = "rtc://community")
    }

    /** A recovery session may omit currentPassword; standard password changes must supply it. */
    suspend fun updatePassword(newPassword: String, currentPassword: String? = null): Result<Unit> = runCatching {
        requireStrongPassword(newPassword)
        supabase.auth.updateUser {
            password = newPassword
            this.currentPassword = currentPassword?.takeIf(String::isNotBlank)
        }
    }

    suspend fun signOutToPublicWelcome(): Result<Unit> {
        val wasSupabaseAuthenticated = _session.value.authority == SessionAuthority.SUPABASE_AUTH
        val remoteSignOutResult = if (wasSupabaseAuthenticated) {
            runCatching { supabase.auth.signOut() }
        } else {
            Result.success(Unit)
        }
        if (wasSupabaseAuthenticated && remoteSignOutResult.isFailure) {
            runCatching { supabase.auth.clearSession() }
        }
        database.cachedSessionDao().logoutAll()
        clearAccountScopedSessionState()
        return remoteSignOutResult
    }

    /**
     * The shared secret is returned to the presentation layer once and is intentionally never
     * written to app storage, analytics, logs, or the database by the client.
     */
    suspend fun enrollSystemAdministratorTotp(): Result<AdministratorTotpEnrollment> = runCatching {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Use a verified Supabase session to enroll MFA." }
        require(_session.value.role == UserRole.SYSTEM_ADMIN) { "TOTP enrollment is reserved for System Administrators." }
        val factor = supabase.auth.mfa.enroll(FactorType.TOTP, "RTC Community Administrator") {
            issuer = "RTC Community"
        }
        AdministratorTotpEnrollment(
            factorId = factor.id,
            secret = factor.data.secret,
            uri = factor.data.uri,
        )
    }

    suspend fun verifySystemAdministratorTotp(factorId: String?, code: String): Result<Unit> = runCatching {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Use a verified Supabase session to verify MFA." }
        require(_session.value.role == UserRole.SYSTEM_ADMIN) { "TOTP verification is reserved for System Administrators." }
        require(code.trim().length in 6..8 && code.trim().all(Char::isDigit)) { "Enter the current code from your authenticator app." }
        val activeFactorId = factorId ?: supabase.auth.mfa.retrieveFactorsForCurrentUser()
            .firstOrNull { it.isVerified }
            ?.id
            ?: error("An enrolled authenticator is required.")
        val challenge = supabase.auth.mfa.createChallenge(activeFactorId)
        supabase.auth.mfa.verifyChallenge(activeFactorId, challenge.id, code.trim())
        hydrateSupabaseSession()
    }

    private suspend fun handleSessionStatus(status: SessionStatus) {
        when (status) {
            is SessionStatus.NotAuthenticated -> {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                    clearAccountScopedSessionState()
                }
            }
            is SessionStatus.RefreshFailure -> {
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
                    clearAccountScopedSessionState()
                }
            }
            is SessionStatus.Authenticated -> {
                if (status.source is SessionSource.Storage || status.source is SessionSource.Refresh) {
                    runCatching { hydrateSupabaseSession() }
                        .onFailure { clearAccountScopedSessionState() }
                }
            }
            else -> Unit
        }
    }

    private fun clearAccountScopedSessionState() {
        _session.value = publicSession()
        _cases.value = emptyList()
        _supportCaseMessages.value = emptyList()
        _assignedSupportCases.value = emptyList()
        _workQueue.value = emptyList()
        _aiProposals.value = emptyList()
        _posts.value = emptyList()
        _communityPostDetail.value = null
        _communityComments.value = emptyList()
        _communityGuidelinesAccepted.value = null
        _isInteractiveTutorialVisible.value = false
        _drafts.value = emptyList()
        _accessManagedAccount.value = null
        _accessRoleChangeRequests.value = emptyList()
        _accessRoleAuditEvents.value = emptyList()
        _adminAnalyticsDashboard.value = AdminAnalyticsDashboard()
        _adminAnalyticsLocalities.value = emptyList()
        _adminAnalyticsAccountProfile.value = null
        _adminAnalyticsAuditEvents.value = emptyList()
        _staffWorkPreferences.value = StaffWorkPreferences()
        _operationsWorkQueue.value = emptyList()
        _operationsControls.value = OperationsControlState()
        _operationalIncidents.value = emptyList()
        _systemHealth.value = emptyList()
        _administrativeActivity.value = emptyList()
        _moderationQueue.value = emptyList()
        _moderationAppeals.value = emptyList()
        _editorialNotices.value = emptyList()
        _communityAlerts.value = emptyList()
        _communityAlertDetail.value = null
        _communityAlertDashboard.value = emptyList()
        _notifications.value = emptyList()
        _pendingSyncCount.value = 0
        _isLiveContentLoading.value = false
        _liveContentMessage.value = null
        repositoryScope.launch { preferencesStore.clearUserRoles() }
    }

    /** Synthetic role switching is retained solely for debug builds. */
    fun setRole(role: UserRole) {
        if (!BuildConfig.DEBUG || _session.value.authority != SessionAuthority.DEVELOPMENT_ADAPTER) return
        if (role != UserRole.CASE_STAFF) _assignedSupportCases.value = emptyList()
        _session.value = _session.value.copy(
            role = role,
            displayName = when (role) {
                UserRole.CONTENT_EDITOR -> "Content Editor"
                UserRole.MODERATOR -> "Moderator"
                UserRole.SYSTEM_ADMIN -> "System Administrator"
                UserRole.CASE_STAFF -> "Case Staff"
                else -> "Resident A"
            },
            handle = when (role) {
                UserRole.CONTENT_EDITOR -> "@contenteditor"
                UserRole.MODERATOR -> "@moderator"
                UserRole.SYSTEM_ADMIN -> "@systemadmin"
                UserRole.CASE_STAFF -> "@casestaff"
                else -> "@residenta"
            }
        )
    }

    @Serializable
    private data class UserRoleRow(val role: String)

    private suspend fun hydrateSupabaseSession() {
        val user = supabase.auth.currentUserOrNull() ?: error("A verified Supabase session is required.")
        val email = user.email ?: "staff@rtc.community"

        val resolvedRoles = runCatching {
            supabase.from("user_roles")
                .select { filter { eq("user_id", user.id) } }
                .decodeList<UserRoleRow>()
                .map { mapSupabaseRole(it.role) }
                .distinct()
        }.getOrDefault(emptyList())

        val role = resolvedRoles.maxByOrNull(::rolePrecedence) ?: UserRole.RESIDENT_A
        val isAdmin = role == UserRole.SYSTEM_ADMIN
        val displayName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: email.substringBefore("@")
        val persistedProfile = productionUxRepository.ownPersistedProfile().getOrNull()
        val persistedExperience = productionUxRepository.ownExperiencePreferences().getOrNull()
        val supportNotifications = productionUxRepository.ownSupportNotificationPreference().getOrNull() ?: true
        val communityNotifications = productionUxRepository.ownOrdinaryAlertPreference().getOrNull() ?: true
        val declaredLocality = productionUxRepository.ownDeclaredLocality().getOrNull()
        if (role != UserRole.CASE_STAFF) _assignedSupportCases.value = emptyList()
        val resolvedAvatarUrl = productionUxRepository.resolveProfileAvatarUrl(user.id).getOrNull()
        val finalDisplayName = persistedProfile?.displayName ?: displayName
        val finalBio = persistedProfile?.bio.orEmpty()
        val finalInterests = persistedProfile?.interests.orEmpty()

        _session.value = RtcSession(
            id = user.id,
            displayName = finalDisplayName,
            handle = "@${email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "")}",
            bio = finalBio,
            interests = finalInterests,
            role = role,
            onboardingComplete = true,
            darkMode = persistedExperience?.themePreference ?: _session.value.darkMode,
            readingMode = persistedExperience?.readingMode ?: _session.value.readingMode,
            supportNotifications = supportNotifications,
            communityNotifications = communityNotifications,
            declaredLocality = declaredLocality,
            authority = SessionAuthority.SUPABASE_AUTH,
            authenticatedEmail = user.email,
            administratorMfaStatus = administratorMfaStatus(role),
            avatarUrl = resolvedAvatarUrl,
        )
        database.cachedUserProfileDao().insertProfile(
            CachedUserProfileEntity(
                userId = user.id,
                email = user.email ?: email,
                displayName = finalDisplayName,
                bio = finalBio,
                interestsJson = finalInterests.joinToString(","),
                avatarUrl = resolvedAvatarUrl,
                role = role.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
        database.cachedSessionDao().upsertSession(
            CachedSessionEntity(
                userId = user.id,
                email = user.email ?: email,
                isLoggedIn = true,
                sessionJson = "",
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
        runCatching { preferencesStore.cacheUserRoles(user.id, role, isAdmin) }
    }

    private suspend fun administratorMfaStatus(role: UserRole): AdministratorMfaStatus {
        if (role != UserRole.SYSTEM_ADMIN) return AdministratorMfaStatus.NOT_REQUIRED
        val factors = runCatching { supabase.auth.mfa.retrieveFactorsForCurrentUser() }.getOrDefault(emptyList())
        if (factors.none { it.isVerified }) return AdministratorMfaStatus.NOT_REQUIRED
        val accessToken = supabase.auth.currentSessionOrNull()?.accessToken
            ?: return AdministratorMfaStatus.NOT_REQUIRED
        val assurance = runCatching { supabase.auth.mfa.getAuthenticatorAssuranceLevel(accessToken) }.getOrNull()
        return if (assurance?.current == AuthenticatorAssuranceLevel.AAL2) {
            AdministratorMfaStatus.VERIFIED
        } else {
            AdministratorMfaStatus.NOT_REQUIRED
        }
    }

    private fun requireStrongPassword(password: String) {
        require(password.length >= 8) { "Use at least 8 characters." }
        require(password.any(Char::isLowerCase)) { "Include a lower-case letter." }
        require(password.any(Char::isUpperCase)) { "Include an upper-case letter." }
        require(password.any(Char::isDigit)) { "Include a number." }
        require(password.any { !it.isLetterOrDigit() }) { "Include a symbol." }
    }

    private fun rolePrecedence(role: UserRole): Int = when (role) {
        UserRole.SYSTEM_ADMIN -> 700
        UserRole.EVIDENCE_REVIEWER -> 600
        UserRole.MODERATOR -> 500
        UserRole.CONTENT_EDITOR -> 400
        UserRole.CASE_STAFF -> 300
        UserRole.RESIDENT_A, UserRole.RESIDENT_B -> 100
        UserRole.ANONYMOUS_PUBLIC -> 0
    }

    private fun mapSupabaseRole(role: String?): UserRole = when (role) {
        "CASE_STAFF" -> UserRole.CASE_STAFF
        "CONTENT_EDITOR" -> UserRole.CONTENT_EDITOR
        "MODERATOR" -> UserRole.MODERATOR
        "EVIDENCE_REVIEWER" -> UserRole.EVIDENCE_REVIEWER
        "SYSTEM_ADMIN" -> UserRole.SYSTEM_ADMIN
        "RESIDENT", "RESIDENT_A", "RESIDENT_B", null -> UserRole.RESIDENT_A
        else -> UserRole.RESIDENT_A
    }

    private fun publicSession() = RtcSession(
        id = installationIdentity.id,
        displayName = "Community member",
        handle = "@community",
        role = UserRole.ANONYMOUS_PUBLIC,
        onboardingComplete = true,
        darkMode = _session.value.darkMode,
        readingMode = _session.value.readingMode,
        dynamicColor = _session.value.dynamicColor,
        authority = SessionAuthority.PUBLIC,
    )

    private fun draftOwnerIdOrNull(session: RtcSession): String? = when (session.authority) {
        SessionAuthority.SUPABASE_AUTH -> "staff:${session.id}"
        SessionAuthority.DEVELOPMENT_ADAPTER -> "development:${session.id}"
        SessionAuthority.PUBLIC -> installationIdentity.localOwnerKey
    }

    suspend fun refreshLiveContent() {
        _isLiveContentLoading.value = true
        _liveContentMessage.value = null
        val refreshGeneration = ++directoryGeneration
        var sessionRefreshFailure: Throwable? = null
        if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            runCatching {
                hydrateSupabaseSession()
                recordPrivacyAnalyticsAppActivity()
                refreshCommunityGuidelinesStatus()
            }.onFailure { sessionRefreshFailure = it }
        } else if (_session.value.authority == SessionAuthority.DEVELOPMENT_ADAPTER) {
            refreshCommunityGuidelinesStatus()
        } else {
            _communityGuidelinesAccepted.value = null
        }
        val metrics = productionUxRepository.getDashboardMetrics()
        val projects = productionUxRepository.listProjects(offset = 0)
        val centres = productionUxRepository.listCentres(offset = 0)
        val opportunities = productionUxRepository.listOpportunities(offset = 0)
        val notices = productionUxRepository.publishedOfficialNotices()
        val helpArticles = productionUxRepository.publishedHelpArticles()
        // Community views deliberately grant SELECT only to authenticated users. Public startup
        // must not convert that policy boundary into a misleading empty/error state.
        val communityPosts = runCatching {
            productionUxRepository.publishedCommunityPosts().getOrThrow()
        }.getOrElse { emptyList() }

        val alerts = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.communityAlertInbox()
        } else {
            Result.success(emptyList())
        }
        val supportCases = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.mySupportCases()
        } else Result.success(emptyList())
        val alertDashboard = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
            && _session.value.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)
        ) productionUxRepository.communityAlertDashboard() else Result.success(emptyList())
        val assignedSupportCases = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH
            && _session.value.role == UserRole.CASE_STAFF
        ) productionUxRepository.listAssignedSupportCases() else Result.success(emptyList())
        metrics.onSuccess { _dashboardMetrics.value = it }
        if (refreshGeneration == directoryGeneration) {
            projects.onSuccess { _projectsPage.value = it }
            centres.onSuccess { _centresPage.value = it }
            opportunities.onSuccess { _opportunitiesPage.value = it }
        }
        notices.onSuccess { _notices.value = it }
        helpArticles.onSuccess { _helpArticles.value = it }

        val samplePosts = if (BuildConfig.DEBUG) CommunityMockData.getSamplePosts(context) else emptyList()
        val cachedEntities = database.cachedPostDao().getAllPosts()
        if (cachedEntities.isEmpty()) {
            database.cachedPostDao().insertPosts(samplePosts.map { it.toCachedEntity() })
        }
        if (communityPosts.isNotEmpty()) {
            database.cachedPostDao().insertPosts(communityPosts.map { it.toCachedEntity() })
        }
        val allRoomPosts = database.cachedPostDao().getAllPosts().map { it.toCommunityPost() }
        _posts.value = allRoomPosts

        val loadedCases = supportCases.getOrDefault(emptyList())
        _cases.value = loadedCases
        alerts.onSuccess { loaded ->
            val effectiveAlerts = loaded
            _communityAlerts.value = effectiveAlerts
            val alertNotifications = effectiveAlerts.map { alert ->
                RtcNotification(
                    id = alert.notificationId,
                    title = alert.title,
                    message = alert.summary,
                    createdAt = alert.publishedAt ?: alert.createdAt,
                    unread = alert.unread,
                    route = MainDestination.HOME,
                    alertId = alert.id,
                )
            }
            val caseNotifications = loadedCases.map { caseItem ->
                RtcNotification(
                    id = "case_${caseItem.id}",
                    title = "Support Ticket: ${caseItem.title}",
                    message = "Stage: ${caseItem.stage.label}",
                    createdAt = caseItem.updatedAt,
                    unread = caseItem.stage != za.org.rtc.community.core.CaseStage.RESOLVED,
                    route = MainDestination.SUPPORT,
                    alertId = null,
                )
            }
            _notifications.value = (alertNotifications + caseNotifications).sortedByDescending { it.createdAt }
        }
        alertDashboard.onSuccess { loaded ->
            _communityAlertDashboard.value = loaded
        }
        assignedSupportCases.onSuccess { loaded ->
            _assignedSupportCases.value = loaded
        }
        _liveContentMessage.value = null
        _isLiveContentLoading.value = false
    }

    suspend fun loadMoreProjects() = loadMore(
        current = _projectsPage.value,
        generation = directoryGeneration,
        currentState = { _projectsPage.value },
        request = productionUxRepository::listProjects,
        update = { _projectsPage.value = it },
    )

    suspend fun loadMoreCentres() = loadMore(
        current = _centresPage.value,
        generation = directoryGeneration,
        currentState = { _centresPage.value },
        request = productionUxRepository::listCentres,
        update = { _centresPage.value = it },
    )

    suspend fun loadMoreOpportunities() = loadMore(
        current = _opportunitiesPage.value,
        generation = directoryGeneration,
        currentState = { _opportunitiesPage.value },
        request = productionUxRepository::listOpportunities,
        update = { _opportunitiesPage.value = it },
    )

    suspend fun searchPublicContent(query: String) {
        _isLiveContentLoading.value = true
        productionUxRepository.searchPublicContent(query)
            .onSuccess {
                _publicSearchResults.value = it
                if (_session.value.authority == SessionAuthority.SUPABASE_AUTH && query.trim().length >= 2) {
                    runCatching { productionUxRepository.recordPrivacyAnalyticsActivity("DIRECTORY_SEARCH") }
                }
            }
            .onFailure { _liveContentMessage.value = "Search could not be completed. Please try again." }
        _isLiveContentLoading.value = false
    }

    suspend fun loadCommunityPostDetail(postId: String) {
        _communityPostDetail.value = null
        _communityComments.value = emptyList()
        _isLiveContentLoading.value = true
        _liveContentMessage.value = null

        val remotePost = runCatching { productionUxRepository.communityPost(postId).getOrNull() }.getOrNull()
        val remoteComments = runCatching { productionUxRepository.communityComments(postId).getOrNull() }.getOrNull()

        if (remotePost != null) {
            database.cachedPostDao().insertPost(remotePost.toCachedEntity())
        }
        if (!remoteComments.isNullOrEmpty()) {
            database.cachedCommentDao().insertComments(remoteComments.map { it.toCachedEntity() })
        }

        val post = remotePost
            ?: database.cachedPostDao().getPostById(postId)?.toCommunityPost()
            ?: _posts.value.firstOrNull { it.id == postId }
            ?: if (BuildConfig.DEBUG) CommunityMockData.getSamplePosts(context).firstOrNull { it.id == postId } else null

        val roomComments = database.cachedCommentDao().getCommentsForPost(postId).map { it.toCommunityComment() }
        val comments = if (roomComments.isNotEmpty()) {
            roomComments
        } else if (!remoteComments.isNullOrEmpty()) {
            remoteComments
        } else {
            val samples = if (BuildConfig.DEBUG) CommunityMockData.getSampleComments(postId) else emptyList()
            if (samples.isNotEmpty()) database.cachedCommentDao().insertComments(samples.map { it.toCachedEntity() })
            samples
        }

        _communityPostDetail.value = post
        _communityComments.value = comments
        _isLiveContentLoading.value = false
    }

    suspend fun createCommunityComment(postId: String, body: String): Result<Unit> {
        val cleanBody = body.trim()
        if (cleanBody.isBlank()) return Result.success(Unit)
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Commenting")
        return productionUxRepository.createCommunityComment(postId, cleanBody).mapCatching {
            loadCommunityPostDetail(postId)
        }
    }

    suspend fun toggleCommunityPostLike(postId: String): Result<Unit> {
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Reactions")
        return productionUxRepository.toggleCommunityPostLike(postId).mapCatching {
            loadCommunityPostDetail(postId)
            refreshLiveContent()
        }
    }

    suspend fun searchAccessManagedAccount(email: String): Result<AccessManagedAccount?> =
        productionUxRepository.searchVerifiedAccessAccount(email).also { result ->
            if (result.isSuccess) _accessManagedAccount.value = result.getOrNull()
        }

    suspend fun saveAccessRoleAssignment(
        targetUserId: String,
        requestedRole: String,
        reason: String,
    ): Result<String> = productionUxRepository.saveAccessRoleAssignment(targetUserId, requestedRole, reason)
        .mapCatching { outcome ->
            refreshAccessManagement()
            outcome.message
        }

    suspend fun decideAccessRoleChangeRequest(
        requestId: String,
        approve: Boolean,
        decisionReason: String? = null,
    ): Result<String> = productionUxRepository.decideAccessRoleChangeRequest(requestId, approve, decisionReason)
        .mapCatching { outcome ->
            refreshAccessManagement()
            outcome.message
        }

    suspend fun refreshAccessManagement(): Result<Unit> = runCatching {
        val requests = productionUxRepository.accessRoleChangeRequests().getOrElse { throw it }
        val auditEvents = productionUxRepository.accessRoleAuditEvents().getOrElse { throw it }
        _accessRoleChangeRequests.value = requests
        _accessRoleAuditEvents.value = auditEvents
    }

    fun clearAccessManagedAccount() {
        _accessManagedAccount.value = null
    }

    /** Refreshes aggregate-only metrics and the server-suppressed locality summary. */
    suspend fun refreshAdminPrivacyAnalytics(period: AdminAnalyticsPeriod): Result<Unit> = runCatching {
        val metrics = productionUxRepository.adminAnalyticsMetrics(period.wireValue).getOrElse {
            RtcMockData.getSampleAdminAnalyticsDashboard().metrics
        }
        val localities = productionUxRepository.adminAnalyticsLocalities(period.wireValue).getOrElse {
            RtcMockData.getSampleAdminLocalities()
        }
        val auditTrail = productionUxRepository.adminAnalyticsAuditTrail().getOrElse {
            RtcMockData.getSampleAdminAuditEvents()
        }
        _adminAnalyticsDashboard.value = AdminAnalyticsDashboard(period = period, metrics = metrics)
        _adminAnalyticsLocalities.value = localities
        _adminAnalyticsAuditEvents.value = auditTrail
    }

    suspend fun adminPrivacyExactAccountLookup(
        email: String,
        purpose: String,
        explanation: String,
    ): Result<AdminAccountProfile> = productionUxRepository
        .adminAnalyticsExactAccountLookup(email, purpose, explanation)
        .mapCatching { profile ->
            _adminAnalyticsAccountProfile.value = profile
            _adminAnalyticsAuditEvents.value = productionUxRepository.adminAnalyticsAuditTrail().getOrElse { throw it }
            profile
        }

    fun clearAdminPrivacyAccountLookup() {
        _adminAnalyticsAccountProfile.value = null
    }

    /**
     * Loads only the signed-in staff member's authorised Operations Hub data. The server remains
     * authoritative for role, current-session, MFA, and item ownership checks.
     */
    suspend fun refreshOperationsHub(): Result<Unit> = runCatching {
        val queue = productionUxRepository.operationsWorkQueue().getOrElse {
            RtcMockData.getSampleOperationsWorkQueue()
        }
        _operationsWorkQueue.value = queue
        _staffWorkPreferences.value = productionUxRepository.workPreferences().getOrElse { StaffWorkPreferences() }

        _moderationQueue.value = productionUxRepository.moderationQueue().getOrElse {
            RtcMockData.getSampleModerationQueue()
        }
        _moderationAppeals.value = productionUxRepository.moderationAppeals().getOrElse {
            RtcMockData.getSampleModerationAppeals()
        }

        _editorialNotices.value = productionUxRepository.editorialNotices().getOrElse {
            RtcMockData.getSampleEditorialNotices()
        }

        _operationsControls.value = productionUxRepository.activeOperationsControls().getOrElse { OperationsControlState() }
        _operationalIncidents.value = productionUxRepository.operationalIncidents().getOrElse {
            RtcMockData.getSampleOperationalIncidents()
        }
        _systemHealth.value = productionUxRepository.systemHealth().getOrElse {
            RtcMockData.getSampleSystemHealth()
        }
        _administrativeActivity.value = productionUxRepository.administrativeActivity().getOrElse {
            RtcMockData.getSampleAdministrativeActivity()
        }
    }

    suspend fun saveStaffWorkPreferences(
        queueOrder: List<String>,
        assignedWorkNotifications: Boolean,
        availabilityStatus: String,
    ): Result<Unit> = productionUxRepository.saveWorkPreferences(queueOrder, assignedWorkNotifications, availabilityStatus)
        .mapCatching {
            _staffWorkPreferences.value = StaffWorkPreferences(queueOrder, assignedWorkNotifications, availabilityStatus)
        }

    suspend fun submitFeedback(
        message: String,
        appVersion: String,
        deviceSummary: String,
        screenshotUri: Uri? = null,
    ): Result<Unit> = runCatching {
        val attachmentPath = screenshotUri?.let { uri ->
            productionUxRepository.uploadFeedbackScreenshot(uri).getOrElse { throw it }
        }
        try {
            productionUxRepository.submitFeedback(message, appVersion, deviceSummary, attachmentPath).getOrElse { throw it }
        } catch (error: Throwable) {
            attachmentPath?.let { productionUxRepository.deleteFeedbackScreenshot(it) }
            throw error
        }
    }

    suspend fun claimOperationsWorkItem(workItemId: String): Result<Unit> =
        productionUxRepository.claimOperationsWorkItem(workItemId).mapCatching { refreshOperationsHub().getOrElse { throw it } }

    suspend fun releaseOperationsWorkItem(workItemId: String, reason: String): Result<Unit> =
        productionUxRepository.releaseOperationsWorkItem(workItemId, reason).mapCatching { refreshOperationsHub().getOrElse { throw it } }

    suspend fun markOperationsWorkReadyForReview(workItemId: String, note: String): Result<Unit> =
        productionUxRepository.markOperationsWorkReadyForReview(workItemId, note).mapCatching { refreshOperationsHub().getOrElse { throw it } }

    suspend fun reassignOperationsWorkItem(workItemId: String, ownerId: String, reason: String): Result<Unit> =
        productionUxRepository.reassignOperationsWorkItem(workItemId, ownerId, reason).mapCatching { refreshOperationsHub().getOrElse { throw it } }

    suspend fun decideModerationReport(reportId: String, decision: String, reason: String): Result<Unit> =
        productionUxRepository.moderationDecideReport(reportId, decision, reason).mapCatching {
            refreshOperationsHub().getOrElse { throw it }
            refreshLiveContent()
        }

    suspend fun decideModerationAppeal(appealId: String, decision: String, reason: String): Result<Unit> =
        productionUxRepository.moderationDecideAppeal(appealId, decision, reason).mapCatching {
            refreshOperationsHub().getOrElse { throw it }
            refreshLiveContent()
        }

    suspend fun createEditorialNoticeDraft(
        title: String,
        body: String,
        category: String,
        safetySensitive: Boolean,
        correctsNoticeId: String? = null,
    ): Result<String> = productionUxRepository.editorialCreateDraft(
        title, body, category, safetySensitive, correctsNoticeId
    ).mapCatching { noticeId ->
        refreshOperationsHub().getOrElse { throw it }
        noticeId
    }

    suspend fun submitEditorialNotice(noticeId: String, note: String = "Submitted for review."): Result<Unit> =
        productionUxRepository.editorialSubmitNotice(noticeId, note).mapCatching { refreshOperationsHub().getOrElse { throw it } }

    suspend fun reviewEditorialNotice(
        noticeId: String,
        outcome: String,
        note: String,
        publishMode: String = "PUBLISH",
        scheduledAt: String? = null,
    ): Result<Unit> = productionUxRepository.editorialReviewNotice(
        noticeId, outcome, note, publishMode, scheduledAt
    ).mapCatching {
        refreshOperationsHub().getOrElse { throw it }
        refreshLiveContent()
    }

    suspend fun retireEditorialNotice(noticeId: String, reason: String): Result<Unit> =
        productionUxRepository.editorialRetireNotice(noticeId, reason).mapCatching {
            refreshOperationsHub().getOrElse { throw it }
            refreshLiveContent()
        }

    suspend fun createOperationalIncident(title: String, impactSummary: String, severity: String): Result<String> =
        productionUxRepository.createOperationalIncident(title, impactSummary, severity).mapCatching { incidentId ->
            refreshOperationsHub().getOrElse { throw it }
            incidentId
        }

    suspend fun updateOperationalIncident(incidentId: String, state: String, closingSummary: String? = null): Result<Unit> =
        productionUxRepository.updateOperationalIncident(incidentId, state, closingSummary).mapCatching { refreshOperationsHub().getOrElse { throw it } }

    suspend fun setOperationalControl(
        controlType: String,
        enabled: Boolean,
        reason: String,
        displayMessage: String,
        expiresAt: String?,
        auditNote: String,
        confirmation: String,
    ): Result<String> = productionUxRepository.setOperationalControl(
        controlType = controlType,
        enabled = enabled,
        reason = reason,
        displayMessage = displayMessage,
        expiresAt = expiresAt,
        confirmation = confirmation,
        auditNote = auditNote,
    ).mapCatching { controlId ->
        refreshOperationsHub().getOrElse { throw it }
        refreshLiveContent()
        controlId
    }

    suspend fun refreshAdministrativeActivity(category: String? = null): Result<Unit> =
        productionUxRepository.administrativeActivity(category).mapCatching { events ->
            _administrativeActivity.value = events
        }

    /** APP_ACTIVE is capped by the database to one signal per signed-in account per day. */
    private suspend fun recordPrivacyAnalyticsAppActivity() {
        if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            runCatching { productionUxRepository.recordPrivacyAnalyticsActivity("APP_ACTIVE") }
        }
    }

    suspend fun acceptCommunityGuidelines(): Result<Unit> {
        val userId = _session.value.id
        preferencesStore.setCommunityGuidelinesAccepted(userId, true)
        _communityGuidelinesAccepted.value = true
        return if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.acceptCommunityGuidelines().onSuccess {
                _communityGuidelinesAccepted.value = true
            }.recoverCatching {
                _communityGuidelinesAccepted.value = true
            }
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun refreshCommunityGuidelinesStatus() {
        val userId = _session.value.id
        val locallyAccepted = preferencesStore.isCommunityGuidelinesAccepted(userId)
        if (locallyAccepted) {
            _communityGuidelinesAccepted.value = true
            checkAndTriggerOnboardingTutorial(userId)
            return
        }
        if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            val remoteStatus = productionUxRepository
                .communityGuidelinesAcceptedStatus()
                .getOrDefault(false)
            if (remoteStatus) {
                preferencesStore.setCommunityGuidelinesAccepted(userId, true)
            }
            _communityGuidelinesAccepted.value = remoteStatus
        } else {
            _communityGuidelinesAccepted.value = locallyAccepted
        }
        checkAndTriggerOnboardingTutorial(userId)
    }

    suspend fun checkAndTriggerOnboardingTutorial(userId: String) {
        if (_session.value.role == UserRole.ANONYMOUS_PUBLIC) {
            _isInteractiveTutorialVisible.value = false
            return
        }
        val completed = preferencesStore.isInteractiveTutorialCompleted(userId)
        if (!completed) {
            _isInteractiveTutorialVisible.value = true
        }
    }

    fun showInteractiveTutorial() {
        _isInteractiveTutorialVisible.value = true
    }

    fun dismissInteractiveTutorial(markCompleted: Boolean = true) {
        _isInteractiveTutorialVisible.value = false
        if (markCompleted) {
            val userId = _session.value.id
            repositoryScope.launch {
                preferencesStore.setInteractiveTutorialCompleted(userId, true)
            }
        }
    }

    suspend fun updateCommunityComment(postId: String, commentId: String, body: String): Result<Unit> =
        productionUxRepository.updateCommunityComment(commentId, body).also { result ->
            if (result.isSuccess) loadCommunityPostDetail(postId)
        }

    suspend fun deleteCommunityComment(postId: String, commentId: String): Result<Unit> =
        productionUxRepository.deleteCommunityComment(commentId).also { result ->
            if (result.isSuccess) loadCommunityPostDetail(postId)
        }

    fun clearPublicSearch() { _publicSearchResults.value = emptyList() }

    suspend fun uploadProfilePhoto(uri: Uri): Result<Unit> {
        val user = _session.value
        if (user.authority == SessionAuthority.DEVELOPMENT_ADAPTER) {
            _session.value = _session.value.copy(avatarUrl = uri.toString())
            return Result.success(Unit)
        }
        require(user.authority == SessionAuthority.SUPABASE_AUTH) { "Sign in with your account to add a profile photo." }
        return productionUxRepository.uploadProfilePhoto(uri, user.id).mapCatching {
            // Rehydrate from the persisted profile field and rebuild the feed/detail projections so
            // Account, Community cards, comments, and post detail all receive the same fresh URL.
            hydrateSupabaseSession()
            refreshLiveContent()
            _communityPostDetail.value?.id?.let { loadCommunityPostDetail(it) }
        }
    }

    suspend fun deleteProfilePhoto(): Result<Unit> {
        val user = _session.value
        if (user.authority == SessionAuthority.DEVELOPMENT_ADAPTER) {
            _session.value = _session.value.copy(avatarUrl = null)
            return Result.success(Unit)
        }
        require(user.authority == SessionAuthority.SUPABASE_AUTH) { "Sign in with your account to remove a profile photo." }
        return productionUxRepository.deleteProfilePhoto(user.id).mapCatching {
            hydrateSupabaseSession()
            refreshLiveContent()
            _communityPostDetail.value?.id?.let { loadCommunityPostDetail(it) }
        }
    }

    private suspend fun <T> loadMore(
        current: DirectoryPage<T>,
        generation: Long,
        currentState: () -> DirectoryPage<T>,
        request: suspend (Int) -> Result<DirectoryPage<T>>,
        update: (DirectoryPage<T>) -> Unit,
    ) {
        if (!current.canLoadMore) return
        request(current.offset + current.items.size).onSuccess { next ->
            if (generation != directoryGeneration || currentState() != current) return@onSuccess
            update(
                next.copy(
                    items = (current.items + next.items).distinct(),
                    offset = current.offset,
                )
            )
        }.onFailure { _liveContentMessage.value = "More directory information could not be loaded. Please try again." }
    }

    fun dismissLiveContentMessage() { _liveContentMessage.value = null }

    suspend fun toggleReadingMode(): Result<Unit> =
        saveExperiencePreferences(readingMode = !_session.value.readingMode, themePreference = _session.value.darkMode)

    suspend fun setTheme(preference: ThemePreference): Result<Unit> =
        saveExperiencePreferences(readingMode = _session.value.readingMode, themePreference = preference)

    suspend fun setDynamicColor(enabled: Boolean): Result<Unit> = runCatching {
        _session.value = _session.value.copy(dynamicColor = enabled)
        preferencesStore.setDynamicColor(enabled)
    }

    private suspend fun saveExperiencePreferences(readingMode: Boolean, themePreference: ThemePreference): Result<Unit> = runCatching {
        val persisted = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.saveOwnExperiencePreferences(readingMode, themePreference).getOrThrow()
        } else {
            za.org.rtc.community.supabase.PersistedExperiencePreferences(readingMode, themePreference)
        }
        _session.value = _session.value.copy(readingMode = persisted.readingMode, darkMode = persisted.themePreference)
        preferencesStore.setReadingMode(persisted.readingMode)
        preferencesStore.setTheme(persisted.themePreference)
    }

    suspend fun updateProfile(displayName: String, bio: String, interests: List<String>): Result<Unit> = runCatching {
        val cleanName = displayName.trim()
        val cleanBio = bio.trim()
        val cleanInterests = interests.map(String::trim).filter(String::isNotBlank).distinct().take(8)
        require(cleanName.length in 2..120) { "Enter a display name between 2 and 120 characters." }
        require(cleanBio.length <= 600) { "Keep the bio to 600 characters or fewer." }

        val currentSession = _session.value
        database.cachedUserProfileDao().insertProfile(
            CachedUserProfileEntity(
                userId = currentSession.id,
                email = currentSession.authenticatedEmail,
                displayName = cleanName,
                bio = cleanBio,
                interestsJson = cleanInterests.joinToString(","),
                avatarUrl = currentSession.avatarUrl,
                role = currentSession.role.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )

        if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.saveOwnProfile(cleanName, cleanBio, cleanInterests).getOrThrow()
            hydrateSupabaseSession()
            _session.value = _session.value.copy(
                displayName = cleanName,
                bio = cleanBio,
                interests = cleanInterests,
            )
            refreshLiveContent()
            _communityPostDetail.value?.id?.let { postId -> loadCommunityPostDetail(postId) }
        } else {
            _session.value = _session.value.copy(
                displayName = cleanName,
                bio = cleanBio,
                interests = cleanInterests,
            )
        }
    }

    suspend fun setNotificationPreference(kind: String, enabled: Boolean): Result<Unit> = runCatching {
        require(kind == "support") { "This notification preference is not supported." }
        val persisted = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.saveOwnSupportNotificationPreference(enabled).getOrThrow()
        } else {
            enabled
        }
        _session.value = _session.value.copy(supportNotifications = persisted)
    }

    suspend fun setDeclaredLocality(locality: String?): Result<Unit> =
        productionUxRepository.saveOwnDeclaredLocality(locality).also { result ->
            if (result.isSuccess) {
                _session.value = _session.value.copy(
                    declaredLocality = locality?.trim()?.takeIf(String::isNotBlank)
                )
            }
        }

    suspend fun markAllNotificationsRead(): Result<Unit> = runCatching {
        _communityAlerts.value.filter { it.unread }.forEach { alert ->
            productionUxRepository.markCommunityAlertRead(alert.notificationId).getOrThrow()
        }
        refreshLiveContent()
    }

    suspend fun loadCommunityAlertDetail(alertId: String) {
        _communityAlertDetail.value = null
        if (_session.value.authority != SessionAuthority.SUPABASE_AUTH) {
            _liveContentMessage.value = "Sign in to view Community alerts."
            return
        }
        productionUxRepository.communityAlert(alertId)
            .onSuccess { alert ->
                _communityAlertDetail.value = alert
                alert?.takeIf { it.unread }?.let { productionUxRepository.markCommunityAlertRead(it.notificationId) }
                refreshLiveContent()
            }
            .onFailure { _liveContentMessage.value = "This Community alert is no longer available." }
    }

    suspend fun setOrdinaryAlertPreference(enabled: Boolean): Result<Unit> = runCatching {
        val persisted = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.saveOrdinaryAlertPreference(enabled).getOrThrow()
        } else {
            enabled
        }
        _session.value = _session.value.copy(communityNotifications = persisted)
    }

    suspend fun createCommunityAlert(
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
    ): Result<String> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Sign in with an authorised staff account to publish alerts." }
        return productionUxRepository.createCommunityAlert(
            category, title, summary, body, scheduledAt, expiresAt, null, state, originalAlertId,
            correctionReason, publishConfirmation, emergencyConfirmation, emergencyReason,
        ).also { result -> if (result.isSuccess) refreshLiveContent() }
    }

    suspend fun saveDraft(area: DraftArea, title: String = "", body: String) {
        if (title.isBlank() && body.isBlank()) return
        val ownerUserId = draftOwnerIdOrNull(_session.value) ?: return
        val existing = _drafts.value.firstOrNull { it.area == area }
        localDraftDao.upsert(
            LocalDraftEntity(
                ownerUserId = ownerUserId,
                area = area.name,
                id = existing?.id ?: "draft-${area.name.lowercase()}-${System.currentTimeMillis()}",
                title = title.trim(),
                body = body.trim(),
                savedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun discardDraft(area: DraftArea) {
        val ownerUserId = draftOwnerIdOrNull(_session.value) ?: return
        localDraftDao.deleteAreaForOwner(ownerUserId, area.name)
    }

    suspend fun reportCommunityPost(postId: String, reason: ModerationReason, detail: String): Result<Unit> {
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Community reporting")
        return productionUxRepository.reportCommunityPost(postId, reason, detail)
    }

    suspend fun registerFcmDevice(token: String, appVersion: String?): Result<Unit> {
        if (_session.value.authority != SessionAuthority.SUPABASE_AUTH) return Result.success(Unit)
        return productionUxRepository.registerFcmDevice(token, appVersion)
    }

    suspend fun createPost(text: String, mediaUris: List<Uri> = emptyList()): Result<String> {
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Community publishing")
        return productionUxRepository.createCommunityPost(text.trim(), mediaUris).mapCatching { confirmedPostId ->
            discardDraft(DraftArea.COMMUNITY)
            refreshLiveContent()
            confirmedPostId
        }
    }

    private fun <T> anonymousResidentWriteUnavailable(feature: String): Result<T> =
        Result.failure(IllegalStateException("ANONYMOUS_READ_ONLY:$feature"))

    suspend fun submitSupportRequest(title: String, detail: String): Result<String> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Sign in to create a support request." }
        return productionUxRepository.submitSupportCase(title, detail).also { result ->
            if (result.isSuccess) { discardDraft(DraftArea.SUPPORT); refreshSupportCases() }
        }
    }

    suspend fun refreshSupportCases(): Result<Unit> = productionUxRepository.mySupportCases().map { loaded -> _cases.value = loaded }

    /** Assignment-scoped data is never simulated or reused across session/role changes. */
    suspend fun refreshAssignedSupportCases(): Result<Unit> {
        if (_session.value.authority != SessionAuthority.SUPABASE_AUTH || _session.value.role != UserRole.CASE_STAFF) {
            _assignedSupportCases.value = emptyList()
            return Result.success(Unit)
        }
        return productionUxRepository.listAssignedSupportCases().map { loaded ->
            _assignedSupportCases.value = loaded
        }
    }

    suspend fun updateAssignedSupportCaseState(caseId: String, state: String, note: String): Result<Unit> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Use a verified Supabase session to update an assigned support case." }
        require(_session.value.role == UserRole.CASE_STAFF) { "Assigned case staff access is required." }
        return productionUxRepository.updateAssignedSupportCaseState(caseId, state, note).map {
            // Do not mutate local case state optimistically; reflect only a server-confirmed refresh.
            refreshAssignedSupportCases().getOrThrow()
        }
    }

    suspend fun loadSupportCaseMessages(caseId: String): Result<Unit> = productionUxRepository.supportCaseMessages(caseId).map { _supportCaseMessages.value = it }

    suspend fun addSupportCaseMessage(caseId: String, body: String): Result<Unit> =
        productionUxRepository.addSupportCaseMessage(caseId, body).map { loadSupportCaseMessages(caseId).getOrThrow() }

    suspend fun submitNotice(title: String, summary: String): Result<Unit> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Sign in to submit a notice." }
        return productionUxRepository.submitResidentNotice(title, summary, "Community Updates", false).also { result ->
            if (result.isSuccess) { discardDraft(DraftArea.NOTICE); refreshLiveContent() }
        }
    }

    private fun enqueueUploadRecovery() {
        val request = OneTimeWorkRequestBuilder<CommunityUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("rtc-community-upload-recovery", ExistingWorkPolicy.KEEP, request)
    }

    fun discardAiProposal(proposalId: String) {
        _aiProposals.value = _aiProposals.value.map { proposal ->
            if (proposal.id == proposalId && proposal.status == "Awaiting review") proposal.copy(status = "Discarded") else proposal
        }
    }

    suspend fun createAiProposal(prompt: String): Result<AiProposal> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "RTC AI requires a verified Supabase session." }
        require(_session.value.role.canUseAi) { "RTC AI is not available for this role." }
        return productionUxRepository.createAiProposal(prompt).onSuccess { proposal ->
            _aiProposals.value = listOf(proposal) + _aiProposals.value.filterNot { it.id == proposal.id }
        }
    }

    suspend fun confirmAiProposal(proposalId: String): Result<Int> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "RTC AI confirmation requires a verified Supabase session." }
        require(_session.value.role.canUseAi) { "RTC AI is not available for this role." }
        return productionUxRepository.confirmAiProposal(proposalId).onSuccess { draftsCreated ->
            _aiProposals.value = _aiProposals.value.map { proposal ->
                if (proposal.id == proposalId) proposal.copy(
                    status = "Confirmed — audited drafts created",
                    affectedRecords = proposal.affectedRecords + "$draftsCreated guarded draft(s) created by the server transaction",
                ) else proposal
            }
        }
    }

    suspend fun refreshCommunityMediaUrl(mediaId: String): Result<String> =
        productionUxRepository.refreshCommunityMediaUrl(mediaId)
}

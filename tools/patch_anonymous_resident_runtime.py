from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def load(path: str) -> tuple[Path, str]:
    p = ROOT / path
    return p, p.read_text(encoding="utf-8")


def save(p: Path, text: str) -> None:
    p.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def sub_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return updated


# 1. Anonymous resident root: remove resident authentication wall and allow public content deep links.
p, text = load("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")
text = text.replace("import za.org.rtc.community.feature.account.PublicWelcomeScreen\n", "")
text = text.replace("    val authenticationUi by viewModel.authenticationUi.collectAsStateWithLifecycle()\n", "")
text = text.replace("    val passwordUi by viewModel.passwordUi.collectAsStateWithLifecycle()\n", "")
text = sub_once(
    text,
    r"\n    if \(session\.role == UserRole\.ANONYMOUS_PUBLIC\) \{.*?\n    \}\n\n    val navController = rememberNavController\(\)",
    "\n    val navController = rememberNavController()",
    "remove resident auth wall",
)
text = replace_once(
    text,
    """    LaunchedEffect(pendingCommunityAlertId, session.authority) {
        pendingCommunityAlertId?.takeIf { session.authority == SessionAuthority.SUPABASE_AUTH }?.let { alertId ->
            navController.navigateOverlay(RtcRoute.alertDetail(alertId))
            viewModel.consumePendingCommunityAlert()
        }
    }
    LaunchedEffect(pendingCommunityPostId, session.authority) {
        pendingCommunityPostId?.takeIf { session.authority == SessionAuthority.SUPABASE_AUTH }?.let { postId ->
            navController.navigateOverlay(communityPostRoute(postId))
            viewModel.consumePendingCommunityPost()
        }
    }
""",
    """    LaunchedEffect(pendingCommunityAlertId) {
        pendingCommunityAlertId?.let { alertId ->
            navController.navigateOverlay(RtcRoute.alertDetail(alertId))
            viewModel.consumePendingCommunityAlert()
        }
    }
    LaunchedEffect(pendingCommunityPostId) {
        pendingCommunityPostId?.let { postId ->
            navController.navigateOverlay(communityPostRoute(postId))
            viewModel.consumePendingCommunityPost()
        }
    }
""",
    "public community deep links",
)
text = replace_once(
    text,
    """    LaunchedEffect(session.role, passwordRecoveryActive) {
        val intendedRoute = pendingPublicRoute
        if (!isStaff && intendedRoute != null) {
            navController.navigatePrimary(intendedRoute)
            pendingPublicRoute = null
        }
        if (passwordRecoveryActive) navController.navigateOverlay(RtcRoute.ACCOUNT)
    }
""",
    """    LaunchedEffect(session.role, passwordRecoveryActive, route) {
        val intendedRoute = pendingPublicRoute
        if (!isStaff && intendedRoute != null) {
            navController.navigatePrimary(intendedRoute)
            pendingPublicRoute = null
        }
        if (isStaff && route == RtcRoute.STAFF_ACCESS) {
            navController.navigatePrimary(RtcRoute.OPERATIONS_HUB)
        }
        if (passwordRecoveryActive && isStaff) navController.navigateOverlay(RtcRoute.ACCOUNT)
    }
""",
    "staff login navigation",
)
text = text.replace(
    "if (isInteractiveTutorialVisible && session.role != UserRole.ANONYMOUS_PUBLIC) {",
    "if (isInteractiveTutorialVisible && !isStaff) {",
)
save(p, text)

# 2. Navigation graph: add staff access route and anonymous account hub wiring.
p, text = load("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt")
text = text.replace(
    "import za.org.rtc.community.feature.administration.SystemHealthScreen\n",
    "import za.org.rtc.community.feature.administration.SystemHealthScreen\nimport za.org.rtc.community.feature.administration.StaffAccessScreen\n",
)
text = replace_once(
    text,
    "    val communityActionUi by viewModel.communityActionUi.collectAsStateWithLifecycle()\n",
    "    val communityActionUi by viewModel.communityActionUi.collectAsStateWithLifecycle()\n    val authenticationUi by viewModel.authenticationUi.collectAsStateWithLifecycle()\n",
    "collect staff auth state",
)
text = replace_once(
    text,
    """        composable(RtcRoute.ACCOUNT) {
            AccountScreen(
                viewModel = viewModel,
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onHelp = { navController.navigateOverlay(RtcRoute.HELP) },
                onMarketplace = { navController.navigateOverlay(it) },
            )
        }
""",
    """        composable(RtcRoute.ACCOUNT) {
            AccountScreen(
                isRefreshing = isLiveContentLoading,
                onRefresh = viewModel::refreshLiveContent,
                onHelp = { navController.navigateOverlay(RtcRoute.HELP) },
                onMarketplace = { navController.navigateOverlay(it) },
                onStaffAccess = { navController.navigateOverlay(RtcRoute.STAFF_ACCESS) },
            )
        }
        composable(RtcRoute.STAFF_ACCESS) {
            StaffAccessScreen(
                authenticationUi = authenticationUi,
                onSignIn = viewModel::signInWithEmail,
                onDismissMessage = viewModel::dismissAuthenticationMessage,
                onBack = { navController.popBackStack() },
            )
        }
""",
    "wire staff access",
)
save(p, text)

# 3. Authentication coordinator: only privileged staff sessions may survive or complete sign-in.
p, text = load("app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt")
text = replace_once(
    text,
    """            if (result.getOrDefault(false) && supabase.auth.currentUserOrNull() == null) {
                // The repository may still contain a legacy cached-session fallback. Clear it rather
                // than allowing that local identity to cross the authentication trust boundary.
                repository.signOutToPublicWelcome()
                _authenticationUi.value = AuthenticationUiState(
                    message = "Your previous sign-in could not be verified. Please sign in again.",
                )
                Result.success(false)
            } else {
                result
            }
""",
    """            if (result.getOrDefault(false) && supabase.auth.currentUserOrNull() == null) {
                repository.signOutToPublicWelcome()
                Result.success(false)
            } else if (result.getOrDefault(false) && !repository.session.value.role.isStaff) {
                // Resident accounts are no longer part of the application runtime. A previously
                // persisted non-staff Supabase session is discarded and the app stays anonymous.
                repository.signOutToPublicWelcome()
                Result.success(false)
            } else {
                if (result.getOrDefault(false)) onStaffAuthenticated()
                result
            }
""",
    "staff-only restored session",
)
text = replace_once(
    text,
    """        _authenticationUi.value = AuthenticationUiState()
        _notificationPermissionPrompt.value = true
        if (repository.session.value.role.isStaff) onStaffAuthenticated()
        registerCurrentFcmToken()
""",
    """        if (!repository.session.value.role.isStaff) {
            scope.launch { repository.signOutToPublicWelcome() }
            _authenticationUi.value = AuthenticationUiState(
                message = "Staff access is restricted to authorised RTC staff accounts.",
            )
            return
        }
        _authenticationUi.value = AuthenticationUiState(isSuccess = true, message = "Staff access verified.")
        _notificationPermissionPrompt.value = true
        onStaffAuthenticated()
        registerCurrentFcmToken()
""",
    "staff-only completed authentication",
)
text = sub_once(
    text,
    r"    /\*\*\n     \* Registration goes directly to Supabase\..*?\n    fun signUpWithEmail\(email: String, password: String, displayName: String\) \{.*?\n    \}\n\n    fun dismissAuthenticationMessage",
    """    /** Resident self-registration was removed with authenticated resident identity. */
    fun signUpWithEmail(email: String, password: String, displayName: String) {
        _authenticationUi.value = AuthenticationUiState(
            message = "Resident accounts are no longer required. Authorised staff accounts are provisioned through RTC administration.",
        )
    }

    fun dismissAuthenticationMessage""",
    "remove resident signup",
)
save(p, text)

# 4. Repository: installation continuity, strict auth, production truth, server-first writes.
p, text = load("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt")
text = text.replace(
    "import za.org.rtc.community.data.local.LocalDraftDao\n",
    "import za.org.rtc.community.data.local.LocalDraftDao\nimport za.org.rtc.community.data.local.InstallationIdentity\n",
)
text = replace_once(
    text,
    """    private val localDraftDao: LocalDraftDao,
    private val preferencesStore: UserPreferencesStore,
""",
    """    private val localDraftDao: LocalDraftDao,
    private val installationIdentity: InstallationIdentity,
    private val preferencesStore: UserPreferencesStore,
""",
    "inject installation identity",
)
text = replace_once(
    text,
    """        RtcSession(
            id = "public-visitor",
            displayName = "Public visitor",
            handle = "@visitor",
            role = UserRole.ANONYMOUS_PUBLIC,
            onboardingComplete = false
        )
""",
    """        RtcSession(
            id = installationIdentity.id,
            displayName = "Community member",
            handle = "@community",
            role = UserRole.ANONYMOUS_PUBLIC,
            onboardingComplete = true,
            authority = SessionAuthority.PUBLIC,
        )
""",
    "anonymous initial session",
)
text = replace_once(
    text,
    """        repositoryScope.launch {
            session.collectLatest { activeSession ->
                if (activeSession.authority != SessionAuthority.SUPABASE_AUTH) {
                    _pendingSyncCount.value = 0
                } else {
                    database.uploadOutboxDao().observePendingCountForOwner(activeSession.id)
                        .collectLatest { _pendingSyncCount.value = it }
                }
            }
        }
""",
    """        repositoryScope.launch {
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
""",
    "anonymous pending sync owner",
)
text = sub_once(
    text,
    r"    suspend fun restoreSupabaseSession\(\): Result<Boolean> = runCatching \{.*?\n    \}\n\n    suspend fun signInWithEmail",
    """    suspend fun restoreSupabaseSession(): Result<Boolean> = runCatching {
        if (supabase.auth.currentUserOrNull() == null) return@runCatching false
        supabase.auth.startAutoRefreshForCurrentSession()
        hydrateSupabaseSession()
        recordPrivacyAnalyticsAppActivity()
        enqueueUploadRecovery()
        true
    }

    suspend fun signInWithEmail""",
    "strict restore session",
)
text = sub_once(
    text,
    r"    suspend fun signInWithEmail\(email: String, password: String\): Result<Unit> = runCatching \{.*?\n    \}\n\n    suspend fun signInWithGoogleIdToken",
    """    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
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

    suspend fun signInWithGoogleIdToken""",
    "strict email sign in",
)
text = sub_once(
    text,
    r"    /\*\*\n     \* Email confirmation remains enabled in Supabase\..*?\n    suspend fun signUpWithEmail\(email: String, password: String, displayName: String\): Result<Unit> = runCatching \{.*?\n    \}\n\n    /\*\* The result is intentionally generic",
    """    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Resident account creation has been removed from RTC."))

    /** The result is intentionally generic""",
    "remove repository resident signup",
)
text = sub_once(
    text,
    r"        val email = user\.email \?: \"resident@rtc\.community\"\n        val isAdminEmail = .*?\n\n        val resolvedRoles = runCatching",
    """        val email = user.email ?: "staff@rtc.community"

        val resolvedRoles = runCatching""",
    "remove client admin claims",
)
text = replace_once(
    text,
    """        val isAdmin = isAdminUserClaim || isAdminAppClaim || hasAdminRoleClaim || isAdminEmail || resolvedRoles.contains(UserRole.SYSTEM_ADMIN)
        val role = if (isAdmin) UserRole.SYSTEM_ADMIN else (resolvedRoles.singleOrNull() ?: UserRole.RESIDENT_A)
        val displayName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: (if (isAdminEmail) "Silvano Titus (Admin)" else email.substringBefore("@"))
""",
    """        val role = resolvedRoles.maxByOrNull(::rolePrecedence) ?: UserRole.RESIDENT_A
        val isAdmin = role == UserRole.SYSTEM_ADMIN
        val displayName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: email.substringBefore("@")
""",
    "server role authority",
)
text = text.replace(
    "handle = if (isAdminEmail) \"@silvano_admin\" else \"@${email.substringBefore(\"@\").lowercase().replace(Regex(\"[^a-z0-9_]\"), \"\")}\"",
    "handle = \"@${email.substringBefore(\"@\").lowercase().replace(Regex(\"[^a-z0-9_]\"), \"\")}\"",
)
text = sub_once(
    text,
    r"    private fun publicSession\(\) = RtcSession\(.*?\n    \)\n\n    private fun draftOwnerIdOrNull\(session: RtcSession\): String\? = when \(session\.authority\) \{.*?\n    \}",
    """    private fun publicSession() = RtcSession(
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
    }""",
    "installation draft owner",
)
text = text.replace(
    """        val alerts = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.communityAlertInbox()
        } else {
            Result.success(RtcMockData.getSampleAlerts())
        }
        val supportCases = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.mySupportCases()
        } else Result.success(RtcMockData.getSampleSupportCases())
""",
    """        val alerts = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.communityAlertInbox()
        } else {
            Result.success(emptyList())
        }
        val supportCases = if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            productionUxRepository.mySupportCases()
        } else Result.success(emptyList())
""",
)
text = text.replace(
    ") productionUxRepository.communityAlertDashboard() else Result.success(RtcMockData.getSampleAlertDashboard())",
    ") productionUxRepository.communityAlertDashboard() else Result.success(emptyList())",
)
text = text.replace(
    ") productionUxRepository.listAssignedSupportCases() else Result.success(RtcMockData.getSampleAssignedCases())",
    ") productionUxRepository.listAssignedSupportCases() else Result.success(emptyList())",
)
text = text.replace(
    "        val samplePosts = CommunityMockData.getSamplePosts(context)\n",
    "        val samplePosts = if (BuildConfig.DEBUG) CommunityMockData.getSamplePosts(context) else emptyList()\n",
)
text = text.replace(
    """        val loadedCases = supportCases.getOrDefault(emptyList()).ifEmpty {
            RtcMockData.getSampleSupportCases()
        }
""",
    "        val loadedCases = supportCases.getOrDefault(emptyList())\n",
)
text = text.replace(
    "            val effectiveAlerts = loaded.ifEmpty { RtcMockData.getSampleAlerts() }\n",
    "            val effectiveAlerts = loaded\n",
)
text = text.replace(
    """            _notifications.value = (alertNotifications + caseNotifications).sortedByDescending { it.createdAt }.ifEmpty {
                RtcMockData.getSampleNotifications()
            }
""",
    "            _notifications.value = (alertNotifications + caseNotifications).sortedByDescending { it.createdAt }\n",
)
text = text.replace(
    "            _communityAlertDashboard.value = loaded.ifEmpty { RtcMockData.getSampleAlertDashboard() }\n",
    "            _communityAlertDashboard.value = loaded\n",
)
text = text.replace(
    "            _assignedSupportCases.value = loaded.ifEmpty { RtcMockData.getSampleAssignedCases() }\n",
    "            _assignedSupportCases.value = loaded\n",
)
text = text.replace(
    "            ?: CommunityMockData.getSamplePosts(context).firstOrNull { it.id == postId }\n",
    "            ?: if (BuildConfig.DEBUG) CommunityMockData.getSamplePosts(context).firstOrNull { it.id == postId } else null\n",
)
text = text.replace(
    """            val samples = CommunityMockData.getSampleComments(postId)
            if (samples.isNotEmpty()) {
                database.cachedCommentDao().insertComments(samples.map { it.toCachedEntity() })
            }
            samples
""",
    """            val samples = if (BuildConfig.DEBUG) CommunityMockData.getSampleComments(postId) else emptyList()
            if (samples.isNotEmpty()) database.cachedCommentDao().insertComments(samples.map { it.toCachedEntity() })
            samples
""",
)
text = sub_once(
    text,
    r"    suspend fun createCommunityComment\(postId: String, body: String\): Result<Unit> \{.*?\n    \}\n\n    suspend fun toggleCommunityPostLike",
    """    suspend fun createCommunityComment(postId: String, body: String): Result<Unit> {
        val cleanBody = body.trim()
        if (cleanBody.isBlank()) return Result.success(Unit)
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Commenting")
        return productionUxRepository.createCommunityComment(postId, cleanBody).mapCatching {
            loadCommunityPostDetail(postId)
        }
    }

    suspend fun toggleCommunityPostLike""",
    "server-first comment",
)
text = sub_once(
    text,
    r"    suspend fun toggleCommunityPostLike\(postId: String\): Result<Unit> \{.*?\n    \}\n\n    suspend fun searchAccessManagedAccount",
    """    suspend fun toggleCommunityPostLike(postId: String): Result<Unit> {
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Reactions")
        return productionUxRepository.toggleCommunityPostLike(postId).mapCatching {
            loadCommunityPostDetail(postId)
            refreshLiveContent()
        }
    }

    suspend fun searchAccessManagedAccount""",
    "server-first like",
)
text = sub_once(
    text,
    r"    suspend fun reportCommunityPost\(postId: String, reason: ModerationReason, detail: String\): Result<Unit> \{.*?\n    \}\n\n    suspend fun registerFcmDevice",
    """    suspend fun reportCommunityPost(postId: String, reason: ModerationReason, detail: String): Result<Unit> {
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Community reporting")
        return productionUxRepository.reportCommunityPost(postId, reason, detail)
    }

    suspend fun registerFcmDevice""",
    "server-first report mutation",
)
text = sub_once(
    text,
    r"    suspend fun createPost\(text: String, mediaUris: List<Uri> = emptyList\(\)\): Result<String> \{.*?\n    \}\n\n    suspend fun submitSupportRequest",
    """    suspend fun createPost(text: String, mediaUris: List<Uri> = emptyList()): Result<String> {
        if (_session.value.authority == SessionAuthority.PUBLIC) return anonymousResidentWriteUnavailable("Community publishing")
        return productionUxRepository.createCommunityPost(text.trim(), mediaUris).mapCatching { confirmedPostId ->
            discardDraft(DraftArea.COMMUNITY)
            refreshLiveContent()
            confirmedPostId
        }
    }

    private fun <T> anonymousResidentWriteUnavailable(feature: String): Result<T> =
        Result.failure(IllegalStateException("ANONYMOUS_READ_ONLY:$feature"))

    suspend fun submitSupportRequest""",
    "server-first community post",
)
save(p, text)

# 5. UI-safe anonymous capability message.
p, text = load("app/src/main/java/za/org/rtc/community/app/SafeUiError.kt")
text = replace_once(
    text,
    """        return when {
            detail.contains("permission", ignoreCase = true) ||
""",
    """        return when {
            detail.contains("ANONYMOUS_READ_ONLY", ignoreCase = true) ->
                "RTC no longer requires resident accounts. This Community action is read-only until its anonymous server safety contract is enabled."
            detail.contains("permission", ignoreCase = true) ||
""",
    "anonymous community capability copy",
)
save(p, text)

# 6. Strengthen anonymous runtime contracts.
p, text = load("tools/tests/test_anonymous_resident_runtime.py")
text = replace_once(
    text,
    """    assert "resident_requires_authentication" not in nav_sources.lower()
    assert "requireResidentAuthentication" not in nav_sources
""",
    """    assert "resident_requires_authentication" not in nav_sources.lower()
    assert "requireResidentAuthentication" not in nav_sources
    app = read("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")
    assert "PublicWelcomeScreen(" not in app
    assert "if (session.role == UserRole.ANONYMOUS_PUBLIC)" not in app
""",
    "resident launch contract",
)
text += """

def test_resident_signup_is_not_a_runtime_feature():
    coordinator = read("app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt")
    assert "Resident accounts are no longer required" in coordinator
    account = read("app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt")
    assert "No resident account required" in account
    assert "Staff & administrator access" in account


def test_public_session_uses_installation_continuity_not_account_authority():
    repository = read("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt")
    assert "installationIdentity.localOwnerKey" in repository
    assert 'displayName = "Community member"' in repository
    assert "SessionAuthority.PUBLIC" in repository
    assert 'ANONYMOUS_READ_ONLY' in repository
"""
save(p, text)

print("Anonymous resident runtime patch applied.")

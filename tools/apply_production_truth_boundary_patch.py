from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROD = ROOT / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt"
RTC = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"


def replace_between(text: str, start: str, end: str, replacement: str) -> str:
    i = text.find(start)
    if i < 0:
        raise RuntimeError(f"missing start marker: {start}")
    j = text.find(end, i)
    if j < 0:
        raise RuntimeError(f"missing end marker after {start}: {end}")
    return text[:i] + replacement.rstrip() + "\n\n" + text[j:]


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected exactly one occurrence, found {count}: {old[:120]!r}")
    return text.replace(old, new, 1)


prod = PROD.read_text(encoding="utf-8")
prod = prod.replace("import za.org.rtc.community.data.RtcMockData\n", "")

replacements = [
("    suspend fun mySupportCases()", "    /**", '''    suspend fun mySupportCases(): Result<List<SupportCase>> = runCatching {
        supabase.postgrest.rpc("list_my_support_cases")
            .decodeList<SupportCaseRow>()
            .map { it.toSupportCase() }
    }'''),
("    suspend fun listAssignedSupportCases()", "    suspend fun updateAssignedSupportCaseState", '''    suspend fun listAssignedSupportCases(): Result<List<AssignedSupportCase>> = runCatching {
        supabase.postgrest.rpc("list_assigned_support_cases")
            .decodeList<AssignedSupportCaseRow>()
            .map { row ->
                AssignedSupportCase(
                    id = row.id,
                    title = row.title,
                    category = row.category,
                    state = row.state,
                    priority = row.priority,
                    locationLabel = row.locationLabel,
                    updatedAt = row.updatedAt,
                )
            }
    }'''),
("    suspend fun supportCaseMessages", "    suspend fun addSupportCaseMessage", '''    suspend fun supportCaseMessages(caseId: String): Result<List<SupportCaseMessage>> = runCatching {
        supabase.postgrest.rpc(
            "list_support_case_messages",
            buildJsonObject { put("p_case_id", caseId) },
        ).decodeList<SupportCaseMessageRow>().map { row ->
            SupportCaseMessage(row.id, caseId, row.authorId, row.body, row.createdAt)
        }
    }'''),
("    suspend fun publishedOfficialNotices()", "    suspend fun publishedHelpArticles()", '''    suspend fun publishedOfficialNotices(): Result<List<OfficialNotice>> = runCatching {
        supabase.from("official_notices").select {
            order(column = "published_at", order = Order.DESCENDING)
        }.decodeList<LiveOfficialNoticeRow>()
            .filter { it.status.equals("published", ignoreCase = true) }
            .map { row ->
                OfficialNotice(
                    id = row.id,
                    title = row.title,
                    summary = row.body,
                    status = NoticeStatus.PUBLISHED,
                    publishedAt = row.publishedAt,
                    requiresSafetyReview = row.safetySensitive,
                )
            }
    }'''),
("    suspend fun publishedHelpArticles()", "    suspend fun publishedCommunityPosts()", '''    suspend fun publishedHelpArticles(): Result<List<HelpArticle>> = runCatching {
        supabase.from("help_articles").select {
            order(column = "published_at", order = Order.DESCENDING)
        }.decodeList<HelpArticleDto>()
            .filter { it.publishedAt != null }
            .map { row -> HelpArticle(row.id, row.slug, row.title, row.summary, row.body, row.category, row.publishedAt) }
    }'''),
("    suspend fun communityAlertInbox()", "    suspend fun communityAlert(alertId", '''    suspend fun communityAlertInbox(): Result<List<CommunityAlert>> = runCatching {
        supabase.from("community_alert_inbox").select {
            order(column = "inbox_created_at", order = Order.DESCENDING)
        }.decodeList<CommunityAlertInboxRow>().map(::toCommunityAlert)
    }'''),
("    suspend fun communityAlert(alertId", "    suspend fun markCommunityAlertRead", '''    suspend fun communityAlert(alertId: String): Result<CommunityAlert?> = runCatching {
        supabase.from("community_alert_inbox").select {
            filter { eq("id", alertId) }
            limit(1)
        }.decodeList<CommunityAlertInboxRow>().firstOrNull()?.let(::toCommunityAlert)
    }'''),
("    suspend fun communityAlertDashboard()", "    suspend fun createCommunityAlert", '''    suspend fun communityAlertDashboard(): Result<List<CommunityAlertDashboardItem>> = runCatching {
        supabase.postgrest.rpc("get_community_alert_dashboard")
            .decodeList<CommunityAlertDashboardRow>()
            .map(::toCommunityAlertDashboardItem)
    }'''),
("    suspend fun adminAnalyticsMetrics", "    suspend fun adminAnalyticsLocalities", '''    suspend fun adminAnalyticsMetrics(period: String): Result<List<AdminAnalyticsMetric>> = runCatching {
        supabase.postgrest.rpc(
            "admin_privacy_analytics_dashboard",
            buildJsonObject { put("p_period", period) },
        ).decodeList<AdminAnalyticsMetric>()
    }'''),
("    suspend fun adminAnalyticsLocalities", "    suspend fun adminAnalyticsExactAccountLookup", '''    suspend fun adminAnalyticsLocalities(period: String): Result<List<AdminLocalitySummary>> = runCatching {
        supabase.postgrest.rpc(
            "admin_privacy_analytics_location_summary",
            buildJsonObject { put("p_period", period) },
        ).decodeList<AdminLocalitySummary>()
    }'''),
("    suspend fun adminAnalyticsAuditTrail", "    suspend fun operationsWorkQueue", '''    suspend fun adminAnalyticsAuditTrail(limit: Int = 200): Result<List<AdminAuditTrailEvent>> = runCatching {
        supabase.postgrest.rpc(
            "admin_privacy_analytics_audit_events",
            buildJsonObject { put("maximum_rows", limit.coerceIn(1, 500)) },
        ).decodeList<AdminAuditTrailEvent>()
    }'''),
("    suspend fun operationsWorkQueue()", "    suspend fun claimOperationsWorkItem", '''    suspend fun operationsWorkQueue(): Result<List<OperationsWorkItem>> = runCatching {
        supabase.postgrest.rpc("ops_list_work_queue").decodeList<OperationsWorkItem>()
    }'''),
("    suspend fun systemHealth()", "    suspend fun administrativeActivity", '''    suspend fun systemHealth(): Result<List<SystemHealthStatus>> = runCatching {
        supabase.postgrest.rpc("ops_list_system_health").decodeList<SystemHealthStatus>()
    }'''),
("    suspend fun administrativeActivity", "    suspend fun operationalIncidents", '''    suspend fun administrativeActivity(
        category: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<AdministrativeActivityEvent>> = runCatching {
        supabase.postgrest.rpc("ops_list_administrative_activity", buildJsonObject {
            put("p_limit", limit.coerceIn(1, 200))
            put("p_offset", offset.coerceAtLeast(0))
            category?.takeIf { it.isNotBlank() }?.let { put("p_category", it.trim().uppercase()) }
        }).decodeList<AdministrativeActivityEvent>()
    }'''),
("    suspend fun operationalIncidents()", "    suspend fun createOperationalIncident", '''    suspend fun operationalIncidents(): Result<List<OperationalIncident>> = runCatching {
        supabase.postgrest.rpc("ops_list_incidents").decodeList<OperationalIncident>()
    }'''),
("    suspend fun moderationQueue", "    suspend fun moderationDecideReport", '''    suspend fun moderationQueue(state: String = "OPEN"): Result<List<ModerationQueueItem>> = runCatching {
        supabase.postgrest.rpc("moderation_list_queue", buildJsonObject {
            put("p_state", state.trim().uppercase())
            put("p_limit", 100)
        }).decodeList<ModerationQueueItem>()
    }'''),
("    suspend fun moderationAppeals()", "    suspend fun moderationDecideAppeal", '''    suspend fun moderationAppeals(): Result<List<ModerationAppeal>> = runCatching {
        supabase.postgrest.rpc("moderation_list_appeals", buildJsonObject { put("p_limit", 100) })
            .decodeList<ModerationAppeal>()
    }'''),
("    suspend fun editorialNotices()", "    suspend fun editorialCreateDraft", '''    suspend fun editorialNotices(): Result<List<EditorialNoticeRecord>> = runCatching {
        supabase.from("official_notices").select {
            order(column = "updated_at", order = Order.DESCENDING)
        }.decodeList<EditorialNoticeRecord>()
    }'''),
("    suspend fun getDashboardMetrics()", "    suspend fun listProjects", '''    suspend fun getDashboardMetrics(): Result<DashboardMetrics> = runCatching {
        supabase.postgrest.rpc("get_public_directory_metrics").decodeSingle<DashboardMetrics>()
    }'''),
("    suspend fun listProjects", "    suspend fun listCentres", '''    suspend fun listProjects(offset: Int, pageSize: Int = DIRECTORY_PAGE_SIZE): Result<DirectoryPage<ProjectRecord>> = runCatching {
        val rows = supabase.from("directory_projects").select {
            order(column = "display_order", order = Order.ASCENDING)
            range(offset.toLong()..(offset + pageSize).toLong())
        }.decodeList<ProjectRecord>()
        rows.toDirectoryPage(offset, pageSize)
    }'''),
("    suspend fun listCentres", "    suspend fun listOpportunities", '''    suspend fun listCentres(offset: Int, pageSize: Int = DIRECTORY_PAGE_SIZE): Result<DirectoryPage<CentreRecord>> = runCatching {
        val rows = supabase.from("directory_centres").select {
            order(column = "display_order", order = Order.ASCENDING)
            range(offset.toLong()..(offset + pageSize).toLong())
        }.decodeList<CentreRecord>()
        rows.toDirectoryPage(offset, pageSize)
    }'''),
("    suspend fun listOpportunities", "    suspend fun searchPublicContent", '''    suspend fun listOpportunities(offset: Int, pageSize: Int = DIRECTORY_PAGE_SIZE): Result<DirectoryPage<OpportunityRecord>> = runCatching {
        val rows = supabase.from("directory_opportunities").select {
            order(column = "display_order", order = Order.ASCENDING)
            range(offset.toLong()..(offset + pageSize).toLong())
        }.decodeList<OpportunityRecord>()
        rows.toDirectoryPage(offset, pageSize)
    }'''),
("    suspend fun searchPublicContent", "    /** Returns a short-lived signed URL", '''    suspend fun searchPublicContent(
        query: String,
        offset: Int = 0,
        pageSize: Int = SEARCH_PAGE_SIZE,
    ): Result<List<PublicSearchResult>> = runCatching {
        if (query.trim().length < 2) return@runCatching emptyList()
        supabase.postgrest.rpc(
            "search_public_directory",
            buildJsonObject {
                put("p_query", query.trim())
                put("p_limit", pageSize)
                put("p_offset", offset)
            }
        ).decodeList<PublicSearchResult>()
    }'''),
]
for start, end, replacement in replacements:
    prod = replace_between(prod, start, end, replacement)

if "RtcMockData" in prod:
    raise RuntimeError("RtcMockData remains in ProductionUxRepository")
PROD.write_text(prod, encoding="utf-8")

rtc = RTC.read_text(encoding="utf-8")
for old in (
    "import android.content.Context\n",
    "import dagger.hilt.android.qualifiers.ApplicationContext\n",
    "import za.org.rtc.community.feature.community.CommunityMockData\n",
    "import java.util.UUID\n",
):
    rtc = rtc.replace(old, "")
rtc = rtc.replace("    @ApplicationContext private val context: Context,\n", "")
rtc = replace_once(
    rtc,
    "            ?: MutableStateFlow(za.org.rtc.community.feature.events.domain.SampleCommunityEvents).asStateFlow()",
    "            ?: MutableStateFlow<List<za.org.rtc.community.feature.events.domain.CommunityEvent>>(emptyList()).asStateFlow()",
)

rtc = replace_between(rtc, "    suspend fun refreshLiveContent()", "    suspend fun loadMoreProjects()", '''    suspend fun refreshLiveContent() {
        _isLiveContentLoading.value = true
        _liveContentMessage.value = null
        val refreshGeneration = ++directoryGeneration
        var firstFailure: Throwable? = null
        fun rememberFailure(result: Result<*>) {
            if (firstFailure == null) firstFailure = result.exceptionOrNull()
        }

        if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            runCatching {
                hydrateSupabaseSession()
                recordPrivacyAnalyticsAppActivity()
                refreshCommunityGuidelinesStatus()
            }.onFailure { firstFailure = it }
        } else if (_session.value.authority == SessionAuthority.DEVELOPMENT_ADAPTER) {
            refreshCommunityGuidelinesStatus()
        } else {
            _communityGuidelinesAccepted.value = null
        }

        val metrics = productionUxRepository.getDashboardMetrics().also(::rememberFailure)
        val projects = productionUxRepository.listProjects(offset = 0).also(::rememberFailure)
        val centres = productionUxRepository.listCentres(offset = 0).also(::rememberFailure)
        val opportunities = productionUxRepository.listOpportunities(offset = 0).also(::rememberFailure)
        val notices = productionUxRepository.publishedOfficialNotices().also(::rememberFailure)
        val helpArticles = productionUxRepository.publishedHelpArticles().also(::rememberFailure)

        metrics.onSuccess { _dashboardMetrics.value = it }
        if (refreshGeneration == directoryGeneration) {
            projects.onSuccess { _projectsPage.value = it }
            centres.onSuccess { _centresPage.value = it }
            opportunities.onSuccess { _opportunitiesPage.value = it }
        }
        notices.onSuccess { _notices.value = it }
        helpArticles.onSuccess { _helpArticles.value = it }

        if (_session.value.authority == SessionAuthority.SUPABASE_AUTH) {
            val communityPosts = productionUxRepository.publishedCommunityPosts().also(::rememberFailure)
            communityPosts.onSuccess { loaded ->
                database.cachedPostDao().insertPosts(loaded.map { it.toCachedEntity() })
                _posts.value = database.cachedPostDao().getAllPosts().map { it.toCommunityPost() }
            }

            val supportCases = productionUxRepository.mySupportCases().also(::rememberFailure)
            supportCases.onSuccess { _cases.value = it }

            val alerts = productionUxRepository.communityAlertInbox().also(::rememberFailure)
            alerts.onSuccess { loaded ->
                _communityAlerts.value = loaded
                val alertNotifications = loaded.map { alert ->
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
                val caseNotifications = _cases.value.map { caseItem ->
                    RtcNotification(
                        id = "case_${caseItem.id}",
                        title = "Support Ticket: ${caseItem.title}",
                        message = "Stage: ${caseItem.stage.label}",
                        createdAt = caseItem.updatedAt,
                        unread = caseItem.stage != CaseStage.RESOLVED,
                        route = MainDestination.SUPPORT,
                        alertId = null,
                    )
                }
                _notifications.value = (alertNotifications + caseNotifications).sortedByDescending { it.createdAt }
            }

            if (_session.value.role in setOf(UserRole.CONTENT_EDITOR, UserRole.SYSTEM_ADMIN)) {
                productionUxRepository.communityAlertDashboard()
                    .also(::rememberFailure)
                    .onSuccess { _communityAlertDashboard.value = it }
            } else {
                _communityAlertDashboard.value = emptyList()
            }
            if (_session.value.role == UserRole.CASE_STAFF) {
                productionUxRepository.listAssignedSupportCases()
                    .also(::rememberFailure)
                    .onSuccess { _assignedSupportCases.value = it }
            } else {
                _assignedSupportCases.value = emptyList()
            }
        } else {
            _posts.value = emptyList()
            _cases.value = emptyList()
            _communityAlerts.value = emptyList()
            _communityAlertDashboard.value = emptyList()
            _assignedSupportCases.value = emptyList()
            _notifications.value = emptyList()
        }

        if (firstFailure != null) {
            _liveContentMessage.value = "Some live information could not be refreshed. Cached verified data is shown where available."
        }
        _isLiveContentLoading.value = false
    }''')

rtc = replace_between(rtc, "    suspend fun loadCommunityPostDetail", "    suspend fun createCommunityComment", '''    suspend fun loadCommunityPostDetail(postId: String) {
        _communityPostDetail.value = null
        _communityComments.value = emptyList()
        _isLiveContentLoading.value = true
        _liveContentMessage.value = null

        val remotePost = productionUxRepository.communityPost(postId)
        val remoteComments = productionUxRepository.communityComments(postId)

        remotePost.getOrNull()?.let { database.cachedPostDao().insertPost(it.toCachedEntity()) }
        remoteComments.getOrNull()?.let { comments ->
            if (comments.isNotEmpty()) database.cachedCommentDao().insertComments(comments.map { it.toCachedEntity() })
        }

        _communityPostDetail.value = remotePost.getOrNull()
            ?: database.cachedPostDao().getPostById(postId)?.toCommunityPost()
            ?: _posts.value.firstOrNull { it.id == postId }
        _communityComments.value = remoteComments.getOrNull()
            ?: database.cachedCommentDao().getCommentsForPost(postId).map { it.toCommunityComment() }

        if (remotePost.isFailure || remoteComments.isFailure) {
            _liveContentMessage.value = "This Community item could not be fully refreshed. Cached verified data is shown where available."
        }
        _isLiveContentLoading.value = false
    }''')

rtc = replace_between(rtc, "    suspend fun createCommunityComment", "    suspend fun toggleCommunityPostLike", '''    suspend fun createCommunityComment(postId: String, body: String): Result<Unit> {
        val cleanBody = body.trim()
        if (cleanBody.isBlank()) return Result.failure(IllegalArgumentException("Write a comment before posting."))
        return productionUxRepository.createCommunityComment(postId, cleanBody).mapCatching {
            loadCommunityPostDetail(postId)
            refreshLiveContent()
        }
    }''')

rtc = replace_between(rtc, "    suspend fun toggleCommunityPostLike", "    suspend fun searchAccessManagedAccount", '''    suspend fun toggleCommunityPostLike(postId: String): Result<Unit> =
        productionUxRepository.toggleCommunityPostLike(postId).mapCatching {
            loadCommunityPostDetail(postId)
            refreshLiveContent()
        }''')

rtc = replace_between(rtc, "    suspend fun refreshAdminPrivacyAnalytics", "    suspend fun adminPrivacyExactAccountLookup", '''    suspend fun refreshAdminPrivacyAnalytics(period: AdminAnalyticsPeriod): Result<Unit> = runCatching {
        val metrics = productionUxRepository.adminAnalyticsMetrics(period.wireValue).getOrThrow()
        val localities = productionUxRepository.adminAnalyticsLocalities(period.wireValue).getOrThrow()
        val auditTrail = productionUxRepository.adminAnalyticsAuditTrail().getOrThrow()
        _adminAnalyticsDashboard.value = AdminAnalyticsDashboard(period = period, metrics = metrics)
        _adminAnalyticsLocalities.value = localities
        _adminAnalyticsAuditEvents.value = auditTrail
    }''')

rtc = replace_between(rtc, "    suspend fun refreshOperationsHub", "    suspend fun saveStaffWorkPreferences", '''    suspend fun refreshOperationsHub(): Result<Unit> = runCatching {
        val queue = productionUxRepository.operationsWorkQueue().getOrThrow()
        val preferences = productionUxRepository.workPreferences().getOrThrow()
        val moderationQueue = productionUxRepository.moderationQueue().getOrThrow()
        val moderationAppeals = productionUxRepository.moderationAppeals().getOrThrow()
        val editorialNotices = productionUxRepository.editorialNotices().getOrThrow()
        val controls = productionUxRepository.activeOperationsControls().getOrThrow()
        val incidents = productionUxRepository.operationalIncidents().getOrThrow()
        val health = productionUxRepository.systemHealth().getOrThrow()
        val activity = productionUxRepository.administrativeActivity().getOrThrow()

        _operationsWorkQueue.value = queue
        _staffWorkPreferences.value = preferences
        _moderationQueue.value = moderationQueue
        _moderationAppeals.value = moderationAppeals
        _editorialNotices.value = editorialNotices
        _operationsControls.value = controls
        _operationalIncidents.value = incidents
        _systemHealth.value = health
        _administrativeActivity.value = activity
    }''')

rtc = replace_between(rtc, "    suspend fun acceptCommunityGuidelines()", "    private suspend fun refreshCommunityGuidelinesStatus", '''    suspend fun acceptCommunityGuidelines(): Result<Unit> {
        val userId = _session.value.id
        return when (_session.value.authority) {
            SessionAuthority.SUPABASE_AUTH -> productionUxRepository.acceptCommunityGuidelines().mapCatching {
                preferencesStore.setCommunityGuidelinesAccepted(userId, true)
                _communityGuidelinesAccepted.value = true
            }
            SessionAuthority.DEVELOPMENT_ADAPTER -> runCatching {
                preferencesStore.setCommunityGuidelinesAccepted(userId, true)
                _communityGuidelinesAccepted.value = true
            }
            SessionAuthority.PUBLIC -> Result.failure(IllegalStateException("Sign in before accepting Community Guidelines."))
        }
    }''')

rtc = replace_between(rtc, "    private suspend fun refreshCommunityGuidelinesStatus", "    suspend fun checkAndTriggerOnboardingTutorial", '''    private suspend fun refreshCommunityGuidelinesStatus() {
        val userId = _session.value.id
        when (_session.value.authority) {
            SessionAuthority.SUPABASE_AUTH -> {
                val remoteStatus = productionUxRepository.communityGuidelinesAcceptedStatus().getOrThrow()
                if (remoteStatus) preferencesStore.setCommunityGuidelinesAccepted(userId, true)
                _communityGuidelinesAccepted.value = remoteStatus
            }
            SessionAuthority.DEVELOPMENT_ADAPTER -> {
                _communityGuidelinesAccepted.value = preferencesStore.isCommunityGuidelinesAccepted(userId)
            }
            SessionAuthority.PUBLIC -> _communityGuidelinesAccepted.value = null
        }
        checkAndTriggerOnboardingTutorial(userId)
    }''')

if any(token in rtc for token in ("RtcMockData", "CommunityMockData", "SampleCommunityEvents")):
    leftovers = [token for token in ("RtcMockData", "CommunityMockData", "SampleCommunityEvents") if token in rtc]
    raise RuntimeError(f"production mock tokens remain in RtcRepository: {leftovers}")
RTC.write_text(rtc, encoding="utf-8")

package za.org.rtc.community.feature.publicreports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.app.SafeUiError
import za.org.rtc.community.feature.publicreports.data.PublicReportEvidenceClient
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportCategory
import za.org.rtc.community.feature.publicreports.domain.PublicReportComment
import za.org.rtc.community.feature.publicreports.domain.PublicReportEvidenceItem
import za.org.rtc.community.feature.publicreports.domain.PublicReportFilters
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.domain.PublicReportSort
import za.org.rtc.community.feature.publicreports.domain.PublicReportTimelineEntry
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportValidation

import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard

data class PublicReportsFeedState(
    val reports: List<PublicReport> = emptyList(),
    val categories: List<PublicReportCategory> = emptyList(),
    val filters: PublicReportFilters = PublicReportFilters(),
    val dashboard: PublicReportDashboard? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val message: String? = null,
    val requestGeneration: Long = 0,
)

data class PublicReportDetailState(
    val report: PublicReport? = null,
    val evidence: List<PublicReportEvidenceItem> = emptyList(),
    val selectedEvidenceId: String? = null,
    val evidenceOpening: Boolean = false,
    val timeline: List<PublicReportTimelineEntry> = emptyList(),
    val comments: List<PublicReportComment> = emptyList(),
    val commentDraft: String = "",
    val loading: Boolean = false,
    val commentsEndReached: Boolean = false,
    val voting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PublicReportViewModel @Inject constructor(
    private val repository: PublicReportRepository,
    private val evidenceClient: PublicReportEvidenceClient,
    private val syncEngine: za.org.rtc.community.core.sync.SystemUpdateSyncEngine = za.org.rtc.community.core.sync.SystemUpdateSyncEngine(),
) : ViewModel() {
    private val _feed = MutableStateFlow(PublicReportsFeedState())
    val feed = _feed.asStateFlow()

    private val _detail = MutableStateFlow(PublicReportDetailState())
    val detail = _detail.asStateFlow()

    private var cursorCreatedAt: Instant? = null
    private var cursorId: String? = null
    private var commentsCursorCreatedAt: Instant? = null
    private var commentsCursorId: String? = null
    private var feedInitialized = false

    fun loadInitial(initialScope: PublicReportScope = PublicReportScope.VERIFIED) {
        if (feedInitialized) {
            if (_feed.value.filters.effectiveScope != initialScope) setScope(initialScope)
            return
        }
        feedInitialized = true
        _feed.update {
            it.copy(
                filters = it.filters.copy(
                    scope = initialScope,
                    statusBucket = null,
                    verified = null,
                ),
            )
        }
        viewModelScope.launch {
            repository.categories().onSuccess { categories ->
                _feed.update { it.copy(categories = categories.filter { category -> category.isActive }) }
            }
        }
        refresh()
    }

    fun refresh() {
        val generation = _feed.value.requestGeneration + 1
        _feed.update { it.copy(refreshing = true, loading = it.reports.isEmpty(), message = null, requestGeneration = generation) }
        cursorCreatedAt = null
        cursorId = null
        viewModelScope.launch {
            repository.dashboard().onSuccess { dash ->
                _feed.update { it.copy(dashboard = dash) }
            }
            loadPage(reset = true, generation = generation)
        }
    }

    fun loadNext() {
        val state = _feed.value
        if (state.loadingMore || state.endReached || state.loading) return
        val generation = state.requestGeneration
        _feed.update { it.copy(loadingMore = true) }
        viewModelScope.launch { loadPage(reset = false, generation = generation) }
    }

    fun applyFilters(filters: PublicReportFilters) {
        _feed.update { PublicReportFeedReducer.beginFilterChange(it, filters) }
        refresh()
    }

    fun setScope(scope: PublicReportScope) = applyFilters(
        _feed.value.filters.copy(scope = scope, statusBucket = null, verified = null),
    )
    fun setUrgency(urgency: PublicReportUrgency?) = applyFilters(_feed.value.filters.copy(urgency = urgency, quickFilterTag = null))
    fun setCategory(slug: String?) = applyFilters(_feed.value.filters.copy(categorySlug = slug, quickFilterTag = null))
    fun setSort(sort: PublicReportSort) = applyFilters(_feed.value.filters.copy(sort = sort))
    fun setSearchQuery(query: String) = applyFilters(_feed.value.filters.copy(searchQuery = query, quickFilterTag = null))
    fun selectQuickFilter(tag: String) {
        val currentTag = _feed.value.filters.quickFilterTag
        if (currentTag == tag) {
            clearFilters()
            return
        }
        val nextFilters = when (tag) {
            "All" -> _feed.value.filters.copy(quickFilterTag = null, searchQuery = "", urgency = null, categorySlug = null)
            "Pothole" -> _feed.value.filters.copy(quickFilterTag = "Pothole", searchQuery = "pothole", urgency = null, categorySlug = null)
            "Streetlight" -> _feed.value.filters.copy(quickFilterTag = "Streetlight", searchQuery = "streetlight", urgency = null, categorySlug = null)
            "Water Main" -> _feed.value.filters.copy(quickFilterTag = "Water Main", searchQuery = "water", urgency = null, categorySlug = null)
            "Sanitation" -> _feed.value.filters.copy(quickFilterTag = "Sanitation", searchQuery = "sanitation", urgency = null, categorySlug = null)
            "Graffiti" -> _feed.value.filters.copy(quickFilterTag = "Graffiti", searchQuery = "graffiti", urgency = null, categorySlug = null)
            "Sidewalk" -> _feed.value.filters.copy(quickFilterTag = "Sidewalk", searchQuery = "sidewalk", urgency = null, categorySlug = null)
            "Urgent" -> _feed.value.filters.copy(quickFilterTag = "Urgent", searchQuery = "", urgency = PublicReportUrgency.HIGH, categorySlug = null)
            "Traffic" -> _feed.value.filters.copy(quickFilterTag = "Traffic", searchQuery = "traffic", urgency = null, categorySlug = null)
            else -> _feed.value.filters.copy(quickFilterTag = tag, searchQuery = tag.lowercase(), urgency = null, categorySlug = null)
        }
        applyFilters(nextFilters)
    }
    fun clearFilters() = applyFilters(PublicReportFilters())

    fun openReport(reportId: String) {
        _detail.value = PublicReportDetailState(loading = true)
        commentsCursorCreatedAt = null
        commentsCursorId = null
        viewModelScope.launch {
            val report = repository.get(reportId).getOrElse { error ->
                _detail.update {
                    it.copy(loading = false, message = SafeUiError.generic(error, error.message ?: "Public Report could not be loaded."))
                }
                return@launch
            }
            val evidence = evidenceClient.list(reportId).getOrDefault(emptyList())
            val timeline = repository.timeline(reportId).getOrDefault(emptyList())
            val comments = repository.comments(reportId).getOrDefault(emptyList())
            commentsCursorCreatedAt = comments.lastOrNull()?.createdAt
            commentsCursorId = comments.lastOrNull()?.id
            _detail.value = PublicReportDetailState(
                report = report,
                evidence = evidence,
                timeline = timeline,
                comments = comments,
                commentsEndReached = comments.size < 20,
                loading = false,
            )
        }
    }

    fun openEvidence(evidenceId: String) {
        val evidence = _detail.value.evidence.firstOrNull { it.id == evidenceId } ?: return
        if (!evidence.signedUrl.isNullOrBlank()) {
            _detail.update { it.copy(selectedEvidenceId = evidenceId, evidenceOpening = false) }
            return
        }
        _detail.update { it.copy(evidenceOpening = true, message = null) }
        viewModelScope.launch {
            evidenceClient.signedUrl(evidenceId)
                .onSuccess { url ->
                    _detail.update { state ->
                        state.copy(
                            evidence = state.evidence.map { item ->
                                if (item.id == evidenceId) item.copy(signedUrl = url) else item
                            },
                            selectedEvidenceId = evidenceId,
                            evidenceOpening = false,
                        )
                    }
                }
                .onFailure { error ->
                    _detail.update {
                        it.copy(
                            evidenceOpening = false,
                            message = SafeUiError.generic(error, "Evidence could not be opened."),
                        )
                    }
                }
        }
    }

    suspend fun refreshEvidenceUrl(evidenceId: String): String? =
        evidenceClient.signedUrl(evidenceId).getOrNull()?.also { url ->
            _detail.update { state ->
                state.copy(
                    evidence = state.evidence.map { item ->
                        if (item.id == evidenceId) item.copy(signedUrl = url) else item
                    },
                )
            }
        }

    fun dismissEvidence() {
        _detail.update { it.copy(selectedEvidenceId = null, evidenceOpening = false) }
    }

    fun loadMoreComments() {
        val report = _detail.value.report ?: return
        if (_detail.value.commentsEndReached) return
        viewModelScope.launch {
            val page = repository.comments(report.id, commentsCursorCreatedAt, commentsCursorId).getOrElse { return@launch }
            commentsCursorCreatedAt = page.lastOrNull()?.createdAt
            commentsCursorId = page.lastOrNull()?.id
            _detail.update {
                it.copy(comments = it.comments + page, commentsEndReached = page.size < 20)
            }
        }
    }

    fun updateCommentDraft(value: String) {
        _detail.update { it.copy(commentDraft = value.take(PublicReportValidation.COMMENT_MAX)) }
    }

    fun submitComment() {
        val report = _detail.value.report ?: return
        val body = _detail.value.commentDraft
        PublicReportValidation.comment(body)?.let { message ->
            _detail.update { it.copy(message = message) }
            return
        }
        viewModelScope.launch {
            repository.addComment(report.id, body, UUID.randomUUID().toString())
                .onSuccess {
                    _detail.update { it.copy(commentDraft = "") }
                    openReport(report.id)
                }
                .onFailure { error ->
                    _detail.update { it.copy(message = SafeUiError.generic(error, error.message ?: "Comment could not be added.")) }
                }
        }
    }

    fun vote(direction: Int) {
        val report = _detail.value.report ?: return
        val previous = report.currentUserVote
        val next = if (previous == direction) 0 else direction
        val optimistic = report.copy(
            currentUserVote = next,
            thumbsUpCount = report.thumbsUpCount + (if (next == 1) 1 else 0) - (if (previous == 1) 1 else 0),
            thumbsDownCount = report.thumbsDownCount + (if (next == -1) 1 else 0) - (if (previous == -1) 1 else 0),
        )
        _detail.update { it.copy(report = optimistic, voting = true) }
        viewModelScope.launch {
            repository.setVote(report.id, next)
                .onSuccess { result ->
                    _detail.update { state ->
                        state.copy(
                            voting = false,
                            report = state.report?.copy(
                                thumbsUpCount = result.thumbsUpCount,
                                thumbsDownCount = result.thumbsDownCount,
                                currentUserVote = result.currentUserVote,
                            ),
                        )
                    }
                    _feed.update { PublicReportFeedReducer.applyVote(it, report.id, result) }
                }
                .onFailure { error ->
                    _detail.update {
                        it.copy(
                            voting = false,
                            report = report,
                            message = SafeUiError.generic(error, error.message ?: "Vote could not be saved."),
                        )
                    }
                }
        }
    }

    fun voteOnCard(reportId: String, direction: Int) {
        val current = _feed.value.reports.firstOrNull { it.id == reportId } ?: return
        val next = if (current.currentUserVote == direction) 0 else direction
        val snapshot = _feed.value.reports
        _feed.update { state ->
            state.copy(
                reports = state.reports.map { item ->
                    if (item.id != reportId) item else item.copy(currentUserVote = next)
                },
            )
        }
        viewModelScope.launch {
            repository.setVote(reportId, next)
                .onSuccess { result ->
                    _feed.update { PublicReportFeedReducer.applyVote(it, reportId, result) }
                }
                .onFailure { error ->
                    _feed.update {
                        PublicReportFeedReducer.rollbackReports(it, snapshot).copy(
                            message = SafeUiError.generic(error, error.message ?: "Vote could not be saved."),
                        )
                    }
                }
        }
    }

    fun dismissMessage() {
        _feed.update { it.copy(message = null) }
        _detail.update { it.copy(message = null) }
    }

    fun adminVerifyReport(
        reportId: String,
        verified: Boolean,
        reason: String = if (verified) "Report verified by authorized administrator." else "Marked unverified by administrator."
    ) {
        viewModelScope.launch {
            _feed.update { state ->
                state.copy(
                    reports = state.reports.map { if (it.id == reportId) it.copy(verified = verified) else it },
                    message = if (verified) "Report approved & verified for public feed." else "Report verification removed."
                )
            }
            repository.adminSetVerification(reportId, verified, reason, UUID.randomUUID().toString())
                .onSuccess {
                    syncEngine.triggerSystemWideUpdate(za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.PublicReportUpdated(reportId, if (verified) "VERIFIED" else "UNVERIFIED"))
                    refresh()
                }
                .onFailure { error ->
                    _feed.update {
                        it.copy(message = SafeUiError.generic(error, error.message ?: "Verification could not be updated."))
                    }
                    refresh()
                }
        }
    }

    fun adminRejectReport(
        reportId: String,
        reason: String = "Rejected by administrator during verification review."
    ) {
        viewModelScope.launch {
            _feed.update { state ->
                state.copy(
                    reports = state.reports.filterNot { it.id == reportId },
                    message = "Report rejected and excluded from public feed."
                )
            }
            repository.adminTransition(
                reportId = reportId,
                toStatus = za.org.rtc.community.feature.publicreports.domain.PublicReportStatus.REJECTED,
                publicNote = reason,
                privateNote = "Rejected during verification: $reason",
                duplicateOf = null,
                requestId = UUID.randomUUID().toString()
            ).onSuccess {
                syncEngine.triggerSystemWideUpdate(za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.PublicReportUpdated(reportId, "REJECTED"))
                refresh()
            }.onFailure { error ->
                _feed.update {
                    it.copy(message = SafeUiError.generic(error, error.message ?: "Failed to reject report."))
                }
                refresh()
            }
        }
    }

    private suspend fun loadPage(reset: Boolean, generation: Long) {
        repository.page(_feed.value.filters, if (reset) null else cursorCreatedAt, if (reset) null else cursorId)
            .onSuccess { page ->
                if (_feed.value.requestGeneration != generation) return
                cursorCreatedAt = page.nextCreatedAt
                cursorId = page.nextId
                _feed.update { PublicReportFeedReducer.applyPage(it, page, reset, generation) }
            }
            .onFailure { error ->
                if (_feed.value.requestGeneration != generation) return
                _feed.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        message = SafeUiError.generic(error, error.message ?: "Public Reports could not be loaded."),
                    )
                }
            }
    }
}

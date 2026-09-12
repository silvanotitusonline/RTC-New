package za.org.rtc.community.feature.publicreports.presentation

import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportFilters
import za.org.rtc.community.feature.publicreports.domain.PublicReportPage
import za.org.rtc.community.feature.publicreports.domain.PublicReportVoteResult

object PublicReportFeedReducer {
    fun applyPage(state: PublicReportsFeedState, page: PublicReportPage, reset: Boolean, generation: Long): PublicReportsFeedState {
        if (state.requestGeneration != generation) return state
        return state.copy(
            reports = if (reset) page.items else state.reports + page.items,
            loading = false,
            refreshing = false,
            loadingMore = false,
            endReached = page.endReached,
        )
    }

    fun applyVote(state: PublicReportsFeedState, reportId: String, result: PublicReportVoteResult): PublicReportsFeedState =
        state.copy(
            reports = state.reports.map { item ->
                if (item.id != reportId) item else item.copy(
                    thumbsUpCount = result.thumbsUpCount,
                    thumbsDownCount = result.thumbsDownCount,
                    currentUserVote = result.currentUserVote,
                )
            },
        )

    fun rollbackReports(state: PublicReportsFeedState, snapshot: List<PublicReport>): PublicReportsFeedState =
        state.copy(reports = snapshot)

    fun beginFilterChange(state: PublicReportsFeedState, filters: PublicReportFilters): PublicReportsFeedState =
        state.copy(filters = filters, requestGeneration = state.requestGeneration + 1, endReached = false)
}

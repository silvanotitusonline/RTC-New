package za.org.rtc.community.feature.publicreports.presentation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportFilters
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportPage
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatusBucket
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportVoteResult

class PublicReportFeedReducerTest {
    @Test
    fun changingFiltersIncrementsGenerationAndResetReplacesItems() {
        val first = report("1")
        val start = PublicReportsFeedState(reports = listOf(first), requestGeneration = 3)
        val filtered = PublicReportFeedReducer.beginFilterChange(start, PublicReportFilters(statusBucket = PublicReportStatusBucket.ACTIVE))
        assertEquals(4, filtered.requestGeneration)
        val page = PublicReportPage(listOf(report("2")), Instant.parse("2026-08-29T10:00:00Z"), "2", true)
        val applied = PublicReportFeedReducer.applyPage(filtered, page, reset = true, generation = 4)
        assertEquals(listOf("2"), applied.reports.map { it.id })
        assertTrue(applied.endReached)
    }

    @Test
    fun staleGenerationIsIgnored() {
        val start = PublicReportsFeedState(reports = listOf(report("1")), requestGeneration = 2)
        val page = PublicReportPage(listOf(report("9")), Instant.EPOCH, "9", true)
        val applied = PublicReportFeedReducer.applyPage(start, page, reset = true, generation = 1)
        assertEquals(listOf("1"), applied.reports.map { it.id })
    }

    @Test
    fun voteUpdateAndRollbackAreDeterministic() {
        val original = listOf(report("1", vote = 0, up = 2, down = 1))
        val start = PublicReportsFeedState(reports = original)
        val updated = PublicReportFeedReducer.applyVote(start, "1", PublicReportVoteResult(3, 1, 1))
        assertEquals(1, updated.reports.single().currentUserVote)
        assertEquals(3, updated.reports.single().thumbsUpCount)
        val rolled = PublicReportFeedReducer.rollbackReports(updated, original)
        assertEquals(0, rolled.reports.single().currentUserVote)
        assertEquals(2, rolled.reports.single().thumbsUpCount)
    }

    private fun report(id: String, vote: Int = 0, up: Int = 0, down: Int = 0) = PublicReport(
        id = id,
        title = "Water leaking near clinic",
        description = "A pipe has been leaking beside the clinic gate since Monday.",
        startedAt = null,
        categoryId = "cat",
        categorySlug = "water-sanitation",
        categoryLabel = "Water & Sanitation",
        urgency = PublicReportUrgency.HIGH,
        status = PublicReportStatus.SUBMITTED,
        identityMode = PublicReportIdentityMode.NAMED,
        publicLocationLabel = "Clinic gate",
        authorDisplayName = "Thabo Molefe",
        verified = false,
        verificationReason = null,
        duplicateOf = null,
        thumbsUpCount = up,
        thumbsDownCount = down,
        commentCount = 0,
        evidenceCount = 1,
        currentUserVote = vote,
        createdAt = Instant.parse("2026-08-29T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-29T08:00:00Z"),
    )
}

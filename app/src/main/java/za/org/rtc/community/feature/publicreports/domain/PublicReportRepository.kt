package za.org.rtc.community.feature.publicreports.domain

import java.time.Instant

interface PublicReportRepository {
    suspend fun categories(): Result<List<PublicReportCategory>>

    suspend fun page(
        filters: PublicReportFilters,
        cursorCreatedAt: Instant? = null,
        cursorId: String? = null,
        limit: Int = 20,
    ): Result<PublicReportPage>

    suspend fun get(reportId: String): Result<PublicReport?>

    suspend fun timeline(reportId: String): Result<List<PublicReportTimelineEntry>>

    suspend fun comments(
        reportId: String,
        cursorCreatedAt: Instant? = null,
        cursorId: String? = null,
        limit: Int = 20,
    ): Result<List<PublicReportComment>>

    suspend fun addComment(reportId: String, body: String, clientRequestId: String): Result<String>

    suspend fun setVote(reportId: String, direction: Int): Result<PublicReportVoteResult>

    suspend fun create(draft: PublicReportDraft): Result<String>

    suspend fun currentUserId(): Result<String>

    suspend fun uploadEvidenceBytes(storagePath: String, bytes: ByteArray, mimeType: String): Result<Unit>

    suspend fun finalizeEvidence(upload: PublicReportEvidenceUpload): Result<String>

    suspend fun dashboard(): Result<PublicReportDashboard>

    suspend fun myPage(cursorCreatedAt: Instant? = null, cursorId: String? = null, limit: Int = 20): Result<PublicReportPage>

    suspend fun withdraw(reportId: String, reason: String, requestId: String): Result<Unit>

    suspend fun ownerPrivateDetails(reportId: String): Result<PublicReportPrivateDetails>

    suspend fun adminPage(
        status: PublicReportStatus?,
        urgency: PublicReportUrgency?,
        categorySlug: String?,
        verified: Boolean?,
        limit: Int = 50,
    ): Result<List<PublicReportAdminRow>>

    suspend fun adminSetVerification(reportId: String, verified: Boolean, reason: String, requestId: String): Result<Unit>

    suspend fun adminTransition(
        reportId: String,
        toStatus: PublicReportStatus,
        publicNote: String?,
        privateNote: String?,
        duplicateOf: String?,
        requestId: String,
    ): Result<Unit>
}

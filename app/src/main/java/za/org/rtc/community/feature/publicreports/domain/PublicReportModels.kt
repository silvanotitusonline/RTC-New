package za.org.rtc.community.feature.publicreports.domain

import java.time.Instant

enum class PublicReportStatus {
    SUBMITTED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    COMPLETED,
    CLOSED,
    REJECTED,
    DUPLICATE,
    WITHDRAWN,
    UNKNOWN,
}

enum class PublicReportUrgency {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
    UNKNOWN,
}

enum class PublicReportIdentityMode {
    NAMED,
    ANONYMOUS,
    UNKNOWN,
}

enum class PublicReportLocationMode {
    MANUAL,
    MAP,
}

enum class PublicReportMediaKind {
    IMAGE,
    VIDEO,
}

enum class PublicReportStatusBucket {
    ALL,
    ACTIVE,
    COMPLETED,
    INACTIVE,
}

enum class PublicReportScope {
    VERIFIED,
    ACTIVE,
    RESOLVED,
    UNRESOLVED,
}

enum class PublicReportSort {
    LATEST,
    OLDEST,
    URGENCY,
    MOST_DISCUSSED,
    MOST_SUPPORTED,
    HOT,
}

data class PublicReportCategory(
    val id: String,
    val slug: String,
    val label: String,
    val description: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

data class PublicReportEvidenceSummary(
    val count: Int,
)

data class PublicReport(
    val id: String,
    val title: String,
    val description: String,
    val startedAt: Instant?,
    val categoryId: String,
    val categorySlug: String,
    val categoryLabel: String,
    val urgency: PublicReportUrgency,
    val status: PublicReportStatus,
    val identityMode: PublicReportIdentityMode,
    val publicLocationLabel: String,
    val authorDisplayName: String,
    val verified: Boolean,
    val verificationReason: String?,
    val duplicateOf: String?,
    val thumbsUpCount: Int,
    val thumbsDownCount: Int,
    val commentCount: Int,
    val evidenceCount: Int,
    val currentUserVote: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Rounded public projection supplied by civic RPCs; never exact reporter coordinates. */
    val publicLatitude: Double? = null,
    val publicLongitude: Double? = null,
)

data class PublicReportComment(
    val id: String,
    val reportId: String,
    val authorDisplayName: String,
    val body: String,
    val createdAt: Instant,
)

data class PublicReportTimelineEntry(
    val id: String,
    val reportId: String,
    val fromStatus: PublicReportStatus?,
    val toStatus: PublicReportStatus,
    val publicNote: String?,
    val createdAt: Instant,
)

data class PublicReportVoteResult(
    val thumbsUpCount: Int,
    val thumbsDownCount: Int,
    val currentUserVote: Int,
)

val PublicReport.assignedDepartment: String
    get() = when (categorySlug) {
        "infrastructure" -> "Municipal Roads & Infrastructure Dept"
        "water-sanitation" -> "Civic Water & Wastewater Management Unit"
        "electricity" -> "Public Energy & Power Grid Division"
        "public-safety" -> "Public Safety & Traffic Operations Directorate"
        "facilities" -> "Parks, Recreation & Municipal Facilities Unit"
        "waste-mgmt" -> "Environmental Services & Solid Waste Dept"
        else -> "Civic Works & Maintenance Directorate"
    }

data class PublicReportDashboard(
    val openReports: Long = 0L,
    val inProgressReports: Long = 0L,
    val resolvedReports: Long = 0L,
    val verifiedReports: Long = 0L,
    val activeReports: Long = 0L,
    val unresolvedReports: Long = 0L,
) {
    val totalReports: Long
        get() = (openReports + inProgressReports + resolvedReports).let { count ->
            if (count > 0) count else verifiedReports.coerceAtLeast(0L)
        }

    val openPercentage: Int
        get() = if (totalReports > 0) ((openReports * 100) / totalReports).toInt() else 0

    val inProgressPercentage: Int
        get() = if (totalReports > 0) ((inProgressReports * 100) / totalReports).toInt() else 0

    val resolvedPercentage: Int
        get() = if (totalReports > 0) (100 - openPercentage - inProgressPercentage).coerceAtLeast(0) else 0
}

data class PublicReportPrivateDetails(
    val reportId: String,
    val exactAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationMode: PublicReportLocationMode,
    val noEvidenceReason: String?,
    val contactPermission: Boolean,
)

data class PublicReportAdminRow(
    val id: String,
    val title: String,
    val status: PublicReportStatus,
    val urgency: PublicReportUrgency,
    val categorySlug: String,
    val verifiedAt: Instant?,
    val reporterId: String?,
    val exactAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Instant,
)

data class PublicReportDraft(
    val clientRequestId: String,
    val title: String,
    val description: String,
    val startedAt: Instant?,
    val categoryId: String,
    val urgency: PublicReportUrgency,
    val identityMode: PublicReportIdentityMode,
    val locationMode: PublicReportLocationMode,
    val publicLocationLabel: String,
    val latitude: Double?,
    val longitude: Double?,
    val exactAddress: String?,
    val noEvidenceReason: String?,
    val contactPermission: Boolean,
    val guidelinesVersion: String,
)

data class PublicReportEvidenceUpload(
    val reportId: String,
    val storagePath: String,
    val mediaKind: PublicReportMediaKind,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
    val position: Int,
    val finalizeRequestId: String,
)

data class PublicReportPage(
    val items: List<PublicReport>,
    val nextCreatedAt: Instant?,
    val nextId: String?,
    val endReached: Boolean,
)

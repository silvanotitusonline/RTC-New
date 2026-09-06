package za.org.rtc.community.feature.publicreports.domain

data class PublicReportFilters(
    val scope: PublicReportScope = PublicReportScope.VERIFIED,
    @Deprecated("Use verified-only PublicReportScope")
    val statusBucket: PublicReportStatusBucket? = null,
    val urgency: PublicReportUrgency? = null,
    val categorySlug: String? = null,
    @Deprecated("Resident Public Reports are always verified")
    val verified: Boolean? = null,
    val sort: PublicReportSort = PublicReportSort.LATEST,
    val searchQuery: String = "",
    val quickFilterTag: String? = null,
) {
    val effectiveScope: PublicReportScope
        get() = when (statusBucket) {
            PublicReportStatusBucket.ACTIVE -> PublicReportScope.ACTIVE
            PublicReportStatusBucket.COMPLETED -> PublicReportScope.RESOLVED
            PublicReportStatusBucket.INACTIVE -> PublicReportScope.UNRESOLVED
            PublicReportStatusBucket.ALL, null -> scope
        }

    val appliedCount: Int
        get() = listOfNotNull(
            effectiveScope.takeIf { it != PublicReportScope.VERIFIED },
            urgency,
            categorySlug?.takeIf { it.isNotBlank() },
            sort.takeIf { it != PublicReportSort.LATEST },
            searchQuery.takeIf { it.isNotBlank() },
            quickFilterTag?.takeIf { it.isNotBlank() },
        ).size

    val summary: String
        get() {
            if (appliedCount == 0) return "Verified Public Reports"
            val parts = buildList {
                add(effectiveScope.label)
                quickFilterTag?.takeIf { it.isNotBlank() }?.let { add("Tag: $it") }
                searchQuery.takeIf { it.isNotBlank() }?.let { add("\"$it\"") }
                urgency?.takeIf { it != PublicReportUrgency.UNKNOWN }?.let { add(it.label) }
                categorySlug?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (sort != PublicReportSort.LATEST) add(sort.label)
            }
            return parts.joinToString(" · ")
        }

    fun cleared(): PublicReportFilters = PublicReportFilters()
}

val PublicReportScope.label: String
    get() = when (this) {
        PublicReportScope.VERIFIED -> "Verified"
        PublicReportScope.ACTIVE -> "Active"
        PublicReportScope.RESOLVED -> "Resolved"
        PublicReportScope.UNRESOLVED -> "Unresolved"
    }

val PublicReportStatusBucket.label: String
    get() = when (this) {
        PublicReportStatusBucket.ALL -> "All"
        PublicReportStatusBucket.ACTIVE -> "Active"
        PublicReportStatusBucket.COMPLETED -> "Completed"
        PublicReportStatusBucket.INACTIVE -> "Inactive"
    }

val PublicReportUrgency.label: String
    get() = when (this) {
        PublicReportUrgency.LOW -> "Low"
        PublicReportUrgency.NORMAL -> "Normal"
        PublicReportUrgency.HIGH -> "High"
        PublicReportUrgency.CRITICAL -> "Critical"
        PublicReportUrgency.UNKNOWN -> "Urgency unavailable"
    }

val PublicReportStatus.label: String
    get() = when (this) {
        PublicReportStatus.SUBMITTED -> "Submitted"
        PublicReportStatus.ACKNOWLEDGED -> "Acknowledged"
        PublicReportStatus.IN_PROGRESS -> "In progress"
        PublicReportStatus.COMPLETED -> "Completed"
        PublicReportStatus.CLOSED -> "Closed"
        PublicReportStatus.REJECTED -> "Rejected"
        PublicReportStatus.DUPLICATE -> "Duplicate"
        PublicReportStatus.WITHDRAWN -> "Withdrawn"
        PublicReportStatus.UNKNOWN -> "Status unavailable"
    }

val PublicReportSort.label: String
    get() = when (this) {
        PublicReportSort.LATEST -> "Latest"
        PublicReportSort.OLDEST -> "Oldest"
        PublicReportSort.URGENCY -> "Highest urgency"
        PublicReportSort.MOST_DISCUSSED -> "Most discussed"
        PublicReportSort.MOST_SUPPORTED -> "Most supported"
        PublicReportSort.HOT -> "Most active in the last five hours"
    }

fun PublicReportStatus.bucket(): PublicReportStatusBucket = when (this) {
    PublicReportStatus.SUBMITTED, PublicReportStatus.ACKNOWLEDGED, PublicReportStatus.IN_PROGRESS ->
        PublicReportStatusBucket.ACTIVE
    PublicReportStatus.COMPLETED -> PublicReportStatusBucket.COMPLETED
    PublicReportStatus.CLOSED, PublicReportStatus.REJECTED, PublicReportStatus.DUPLICATE, PublicReportStatus.WITHDRAWN ->
        PublicReportStatusBucket.INACTIVE
    PublicReportStatus.UNKNOWN -> PublicReportStatusBucket.ALL
}

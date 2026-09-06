package za.org.rtc.community.feature.publicreports.data

import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportAdminRow
import za.org.rtc.community.feature.publicreports.domain.PublicReportCategory
import za.org.rtc.community.feature.publicreports.domain.PublicReportComment
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportLocationMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportPrivateDetails
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.feature.publicreports.domain.PublicReportTimelineEntry
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportVoteResult

object PublicReportJsonMappers {
    private val blockedPublicKeys = setOf(
        "reporter_id",
        "exact_address",
        "latitude",
        "longitude",
        "storage_path",
        "private_note",
        "no_evidence_reason",
        "contact_permission",
        "client_request_id",
    )

    fun status(raw: String?): PublicReportStatus =
        raw?.trim()?.uppercase()?.let { value ->
            PublicReportStatus.entries.firstOrNull { it.name == value && it != PublicReportStatus.UNKNOWN }
        } ?: PublicReportStatus.UNKNOWN

    fun urgency(raw: String?): PublicReportUrgency =
        raw?.trim()?.uppercase()?.let { value ->
            PublicReportUrgency.entries.firstOrNull { it.name == value && it != PublicReportUrgency.UNKNOWN }
        } ?: PublicReportUrgency.UNKNOWN

    fun identity(raw: String?): PublicReportIdentityMode =
        raw?.trim()?.uppercase()?.let { value ->
            PublicReportIdentityMode.entries.firstOrNull { it.name == value && it != PublicReportIdentityMode.UNKNOWN }
        } ?: PublicReportIdentityMode.UNKNOWN

    fun report(row: JsonObject): PublicReport {
        requireNoPrivateLeak(row)
        val identityMode = identity(row.string("identity_mode"))
        val author = when (identityMode) {
            PublicReportIdentityMode.ANONYMOUS -> "Anonymous community member"
            else -> row.string("author_display_name")?.takeIf { it.isNotBlank() } ?: "Community member"
        }
        return PublicReport(
            id = row.requiredString("id"),
            title = row.requiredString("title"),
            description = row.string("description").orEmpty(),
            startedAt = row.instant("started_at"),
            categoryId = row.string("category_id").orEmpty(),
            categorySlug = row.string("category_slug").orEmpty(),
            categoryLabel = row.string("category_label").orEmpty(),
            urgency = urgency(row.string("urgency")),
            status = status(row.string("status")),
            identityMode = identityMode,
            publicLocationLabel = row.string("public_location_label").orEmpty(),
            authorDisplayName = author,
            verified = row.string("verified_at") != null,
            verificationReason = row.string("verification_reason"),
            duplicateOf = row.string("duplicate_of"),
            thumbsUpCount = row.int("thumbs_up_count"),
            thumbsDownCount = row.int("thumbs_down_count"),
            commentCount = row.int("comment_count"),
            evidenceCount = row.int("evidence_count"),
            currentUserVote = row.int("current_user_vote"),
            createdAt = row.instant("created_at") ?: Instant.EPOCH,
            updatedAt = row.instant("updated_at") ?: Instant.EPOCH,
        )
    }

    fun category(row: JsonObject): PublicReportCategory = PublicReportCategory(
        id = row.requiredString("id"),
        slug = row.requiredString("slug"),
        label = row.requiredString("label"),
        description = row.string("description").orEmpty(),
        sortOrder = row.int("sort_order"),
        isActive = row.boolean("is_active") ?: true,
    )

    fun comment(row: JsonObject): PublicReportComment = PublicReportComment(
        id = row.requiredString("id"),
        reportId = row.requiredString("report_id"),
        authorDisplayName = row.string("author_display_name") ?: "Community member",
        body = row.requiredString("body"),
        createdAt = row.instant("created_at") ?: Instant.EPOCH,
    )

    fun timeline(row: JsonObject): PublicReportTimelineEntry = PublicReportTimelineEntry(
        id = row.requiredString("id"),
        reportId = row.requiredString("report_id"),
        fromStatus = row.string("from_status")?.let(::status),
        toStatus = status(row.string("to_status")),
        publicNote = row.string("public_note"),
        createdAt = row.instant("created_at") ?: Instant.EPOCH,
    )

    fun vote(row: JsonObject): PublicReportVoteResult = PublicReportVoteResult(
        thumbsUpCount = row.int("thumbs_up_count"),
        thumbsDownCount = row.int("thumbs_down_count"),
        currentUserVote = row.int("current_user_vote"),
    )

    fun dashboard(row: JsonObject): PublicReportDashboard {
        val open = runCatching { row.requiredLong("open_reports") }.getOrNull()
            ?: runCatching { row.requiredLong("active_reports") / 2 }.getOrNull() ?: 0L
        val inProgress = runCatching { row.requiredLong("in_progress_reports") }.getOrNull()
            ?: runCatching { row.requiredLong("active_reports") / 2 }.getOrNull() ?: 0L
        val resolved = runCatching { row.requiredLong("resolved_reports") }.getOrNull() ?: 0L
        val verified = runCatching { row.requiredLong("verified_reports") }.getOrNull()
            ?: (open + inProgress + resolved)
        return PublicReportDashboard(
            openReports = open,
            inProgressReports = inProgress,
            resolvedReports = resolved,
            verifiedReports = verified,
            activeReports = open + inProgress,
            unresolvedReports = open + inProgress,
        )
    }

    fun privateDetails(row: JsonObject): PublicReportPrivateDetails = PublicReportPrivateDetails(
        reportId = row.requiredString("report_id"),
        exactAddress = row.string("exact_address"),
        latitude = row.double("latitude"),
        longitude = row.double("longitude"),
        locationMode = if (row.string("location_mode")?.uppercase() == "MAP") {
            PublicReportLocationMode.MAP
        } else {
            PublicReportLocationMode.MANUAL
        },
        noEvidenceReason = row.string("no_evidence_reason"),
        contactPermission = row.boolean("contact_permission") ?: false,
    )

    fun adminRow(row: JsonObject): PublicReportAdminRow = PublicReportAdminRow(
        id = row.requiredString("id"),
        title = row.requiredString("title"),
        status = status(row.string("status")),
        urgency = urgency(row.string("urgency")),
        categorySlug = row.string("category_slug").orEmpty(),
        verifiedAt = row.instant("verified_at"),
        reporterId = row.string("reporter_id"),
        exactAddress = row.string("exact_address"),
        latitude = row.double("latitude"),
        longitude = row.double("longitude"),
        createdAt = row.instant("created_at") ?: Instant.EPOCH,
    )

    fun requireNoPrivateLeak(row: JsonObject) {
        blockedPublicKeys.forEach { key ->
            check(row[key] == null || row[key] is JsonNull) {
                "Private field $key cannot enter the public Public Report model."
            }
        }
    }

    private fun JsonObject.requiredString(key: String): String =
        string(key) ?: error("Missing Public Report field.")

    private fun JsonObject.string(key: String): String? =
        get(key)?.asPrimitive()?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int =
        get(key)?.asPrimitive()?.intOrNull ?: get(key)?.asPrimitive()?.contentOrNull?.toIntOrNull() ?: 0

    private fun JsonObject.requiredLong(key: String): Long =
        get(key)?.asPrimitive()?.longOrNull
            ?: get(key)?.asPrimitive()?.contentOrNull?.toLongOrNull()
            ?: error("Missing Public Report count.")

    private fun JsonObject.double(key: String): Double? =
        get(key)?.asPrimitive()?.doubleOrNull ?: get(key)?.asPrimitive()?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.boolean(key: String): Boolean? =
        get(key)?.asPrimitive()?.booleanOrNull ?: get(key)?.asPrimitive()?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.instant(key: String): Instant? =
        string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JsonElement.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive ?: jsonPrimitive
}

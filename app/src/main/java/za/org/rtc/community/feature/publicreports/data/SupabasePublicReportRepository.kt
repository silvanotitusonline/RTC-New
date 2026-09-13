package za.org.rtc.community.feature.publicreports.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.data.local.VerifiedPublicReportDao
import za.org.rtc.community.data.local.VerifiedPublicReportEntity
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportAdminRow
import za.org.rtc.community.feature.publicreports.domain.PublicReportCategory
import za.org.rtc.community.feature.publicreports.domain.PublicReportComment
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportDraft
import za.org.rtc.community.feature.publicreports.domain.PublicReportEvidenceUpload
import za.org.rtc.community.feature.publicreports.domain.PublicReportFilters
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.PublicReportPage
import za.org.rtc.community.feature.publicreports.domain.PublicReportPrivateDetails
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.domain.PublicReportSort
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.feature.publicreports.domain.PublicReportTimelineEntry
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportValidation
import za.org.rtc.community.feature.publicreports.domain.PublicReportVoteResult

@Singleton
class SupabasePublicReportRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val verifiedCache: VerifiedPublicReportDao,
) : PublicReportRepository {

    override suspend fun categories(): Result<List<PublicReportCategory>> = authoritativeResult {
        decodeObjects(supabase.postgrest.rpc(PublicReportRpcContract.CATEGORIES, buildJsonObject {}))
            .map(PublicReportJsonMappers::category)
    }

    override suspend fun page(
        filters: PublicReportFilters,
        cursorCreatedAt: Instant?,
        cursorId: String?,
        limit: Int,
    ): Result<PublicReportPage> {
        val bounded = limit.coerceIn(1, PublicReportValidation.PAGE_MAX)
        return try {
            val items = decodeObjects(
                supabase.postgrest.rpc(
                    PublicReportRpcContract.VERIFIED_PAGE,
                    pageParameters(filters, cursorCreatedAt, cursorId, bounded),
                ),
            ).map(PublicReportJsonMappers::report)
            cacheVerifiedReports(items)
            Result.success(
                PublicReportPage(
                    items = items,
                    nextCreatedAt = items.lastOrNull()?.createdAt,
                    nextId = items.lastOrNull()?.id,
                    endReached = items.size < bounded,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val cached = if (cursorCreatedAt == null && cursorId == null && filters.cacheCompatible) {
                verifiedCache.latest((bounded * 3).coerceAtMost(100))
                    .map(VerifiedPublicReportEntity::toDomain)
                    .filter { it.matches(filters) }
                    .take(bounded)
            } else {
                emptyList()
            }
            if (cached.isNotEmpty()) {
                Result.success(PublicReportPage(cached, null, null, endReached = true))
            } else {
                Result.failure(PublicReportFailure.from(failure))
            }
        }
    }

    override suspend fun get(reportId: String): Result<PublicReport?> {
        return try {
            val report = decodeObjects(
                supabase.postgrest.rpc(
                    PublicReportRpcContract.GET,
                    buildJsonObject { put("p_report_id", reportId) },
                ),
            ).firstOrNull()?.let(PublicReportJsonMappers::report)
            report?.takeIf { it.verified }?.let { verifiedCache.upsert(it.toCacheEntity()) }
            Result.success(report)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            verifiedCache.byId(reportId)?.toDomain()?.let { Result.success(it) }
                ?: Result.failure(PublicReportFailure.from(failure))
        }
    }

    override suspend fun timeline(reportId: String): Result<List<PublicReportTimelineEntry>> = authoritativeResult {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.TIMELINE,
                buildJsonObject { put("p_report_id", reportId) },
            ),
        ).map(PublicReportJsonMappers::timeline)
    }

    override suspend fun comments(
        reportId: String,
        cursorCreatedAt: Instant?,
        cursorId: String?,
        limit: Int,
    ): Result<List<PublicReportComment>> = authoritativeResult {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.COMMENT_PAGE,
                buildJsonObject {
                    put("p_report_id", reportId)
                    cursorCreatedAt?.let { put("p_cursor_created_at", it.toString()) }
                    cursorId?.let { put("p_cursor_id", it) }
                    put("p_limit", limit.coerceIn(1, PublicReportValidation.PAGE_MAX))
                },
            ),
        ).map(PublicReportJsonMappers::comment)
    }

    override suspend fun addComment(reportId: String, body: String, clientRequestId: String): Result<String> = authoritativeResult {
        PublicReportValidation.comment(body)?.let { error(it) }
        supabase.postgrest.rpc(
            PublicReportRpcContract.ADD_COMMENT,
            buildJsonObject {
                put("p_report_id", reportId)
                put("p_body", body.trim())
                put("p_client_request_id", clientRequestId)
            },
        ).decodeSingle<String>()
    }

    override suspend fun setVote(reportId: String, direction: Int): Result<PublicReportVoteResult> = authoritativeResult {
        require(direction in -1..1) { "Vote must be thumbs up, thumbs down, or cleared." }
        val payload = supabase.postgrest.rpc(
            PublicReportRpcContract.SET_VOTE,
            buildJsonObject {
                put("p_report_id", reportId)
                put("p_direction", direction)
            },
        ).decodeSingle<JsonObject>()
        PublicReportJsonMappers.vote(payload)
    }

    override suspend fun create(draft: PublicReportDraft): Result<String> = authoritativeResult {
        val serverId = supabase.postgrest.rpc(
            PublicReportRpcContract.CREATE,
            publicReportCreateParameters(draft),
        ).decodeSingle<String>()
        java.util.UUID.fromString(serverId)
        serverId
    }

    override suspend fun currentUserId(): Result<String> = runCatching {
        supabase.auth.currentUserOrNull()?.id ?: error("CIVIC_REPORT_AUTH_REQUIRED")
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun uploadEvidenceBytes(storagePath: String, bytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        supabase.storage.from(EVIDENCE_BUCKET).upload(storagePath, bytes) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
        Unit
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun finalizeEvidence(upload: PublicReportEvidenceUpload): Result<String> = runCatching {
        supabase.postgrest.rpc(
            PublicReportRpcContract.FINALIZE_EVIDENCE,
            buildJsonObject {
                put("p_report_id", upload.reportId)
                put("p_storage_path", upload.storagePath)
                put("p_media_kind", upload.mediaKind.name)
                put("p_mime_type", upload.mimeType)
                put("p_byte_size", upload.byteSize)
                upload.width?.let { put("p_width", it) }
                upload.height?.let { put("p_height", it) }
                upload.durationSeconds?.let { put("p_duration_seconds", it) }
                put("p_position", upload.position)
                put("p_client_request_id", upload.finalizeRequestId)
            },
        ).decodeSingle<String>()
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun dashboard(): Result<PublicReportDashboard> = authoritativeResult {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.VERIFIED_DASHBOARD,
                buildJsonObject {},
            ),
        ).first().let(PublicReportJsonMappers::dashboard)
    }

    override suspend fun myPage(cursorCreatedAt: Instant?, cursorId: String?, limit: Int): Result<PublicReportPage> = runCatching {
        val bounded = limit.coerceIn(1, PublicReportValidation.PAGE_MAX)
        val items = decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.MY_PAGE,
                buildJsonObject {
                    cursorCreatedAt?.let { put("p_cursor_created_at", it.toString()) }
                    cursorId?.let { put("p_cursor_id", it) }
                    put("p_limit", bounded)
                },
            ),
        ).map(PublicReportJsonMappers::report)
        PublicReportPage(items, items.lastOrNull()?.createdAt, items.lastOrNull()?.id, items.size < bounded)
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun withdraw(reportId: String, reason: String, requestId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            PublicReportRpcContract.WITHDRAW,
            buildJsonObject {
                put("p_report_id", reportId)
                put("p_reason", reason.trim())
                put("p_request_id", requestId)
            },
        )
        Unit
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun ownerPrivateDetails(reportId: String): Result<PublicReportPrivateDetails> = runCatching {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.OWNER_PRIVATE,
                buildJsonObject { put("p_report_id", reportId) },
            ),
        ).first().let(PublicReportJsonMappers::privateDetails)
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun adminPage(
        status: PublicReportStatus?,
        urgency: PublicReportUrgency?,
        categorySlug: String?,
        verified: Boolean?,
        limit: Int,
    ): Result<List<PublicReportAdminRow>> = runCatching {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.ADMIN_PAGE,
                buildJsonObject {
                    status?.takeIf { it != PublicReportStatus.UNKNOWN }?.let { put("p_status", it.name) }
                    urgency?.takeIf { it != PublicReportUrgency.UNKNOWN }?.let { put("p_urgency", it.name) }
                    categorySlug?.takeIf { it.isNotBlank() }?.let { put("p_category_slug", it) }
                    verified?.let { put("p_verified", it) }
                    put("p_limit", limit.coerceIn(1, 100))
                },
            ),
        ).map(PublicReportJsonMappers::adminRow)
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun adminSetVerification(
        reportId: String,
        verified: Boolean,
        reason: String,
        requestId: String,
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            PublicReportRpcContract.ADMIN_VERIFY,
            buildJsonObject {
                put("p_report_id", reportId)
                put("p_verified", verified)
                put("p_reason", reason.trim())
                put("p_request_id", requestId)
            },
        )
        Unit
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    override suspend fun adminTransition(
        reportId: String,
        toStatus: PublicReportStatus,
        publicNote: String?,
        privateNote: String?,
        duplicateOf: String?,
        requestId: String,
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            PublicReportRpcContract.ADMIN_TRANSITION,
            buildJsonObject {
                put("p_report_id", reportId)
                put("p_to_status", toStatus.name)
                publicNote?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_public_note", it) }
                privateNote?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_private_note", it) }
                duplicateOf?.let { put("p_duplicate_of", it) }
                put("p_request_id", requestId)
            },
        )
        Unit
    }.recoverCatching { error -> throw PublicReportFailure.from(error) }

    private suspend fun cacheVerifiedReports(reports: List<PublicReport>) {
        val verified = reports.filter(PublicReport::verified)
        if (verified.isEmpty()) return
        verifiedCache.upsertAll(verified.map(PublicReport::toCacheEntity))
        verifiedCache.deleteOlderThan(System.currentTimeMillis() - CACHE_RETENTION_MILLIS)
    }

    private fun PublicReport.toCacheEntity() = VerifiedPublicReportEntity(
        id = id,
        title = title,
        description = description,
        startedAt = startedAt?.toString(),
        categoryId = categoryId,
        categorySlug = categorySlug,
        categoryLabel = categoryLabel,
        urgency = urgency.name,
        status = status.name,
        identityMode = identityMode.name,
        publicLocationLabel = publicLocationLabel,
        authorDisplayName = authorDisplayName,
        verificationReason = verificationReason,
        duplicateOf = duplicateOf,
        thumbsUpCount = thumbsUpCount,
        thumbsDownCount = thumbsDownCount,
        commentCount = commentCount,
        evidenceCount = evidenceCount,
        currentUserVote = currentUserVote,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        publicLatitude = publicLatitude,
        publicLongitude = publicLongitude,
        cachedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun VerifiedPublicReportEntity.toDomain() = PublicReport(
        id = id,
        title = title,
        description = description,
        startedAt = startedAt?.let(Instant::parse),
        categoryId = categoryId,
        categorySlug = categorySlug,
        categoryLabel = categoryLabel,
        urgency = enumValueOrDefault(urgency, PublicReportUrgency.UNKNOWN),
        status = enumValueOrDefault(status, PublicReportStatus.UNKNOWN),
        identityMode = enumValueOrDefault(identityMode, PublicReportIdentityMode.UNKNOWN),
        publicLocationLabel = publicLocationLabel,
        authorDisplayName = authorDisplayName,
        verified = true,
        verificationReason = verificationReason,
        duplicateOf = duplicateOf,
        thumbsUpCount = thumbsUpCount,
        thumbsDownCount = thumbsDownCount,
        commentCount = commentCount,
        evidenceCount = evidenceCount,
        currentUserVote = currentUserVote,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        publicLatitude = publicLatitude,
        publicLongitude = publicLongitude,
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private val PublicReportFilters.cacheCompatible: Boolean
        get() = searchQuery.isBlank() && quickFilterTag.isNullOrBlank() && sort == PublicReportSort.LATEST

    private fun PublicReport.matches(filters: PublicReportFilters): Boolean {
        if (filters.urgency?.takeIf { it != PublicReportUrgency.UNKNOWN }?.let { urgency != it } == true) return false
        if (filters.categorySlug?.takeIf { it.isNotBlank() }?.let { categorySlug != it } == true) return false
        return when (filters.effectiveScope) {
            PublicReportScope.VERIFIED -> verified
            PublicReportScope.ACTIVE -> status in ACTIVE_STATUSES
            PublicReportScope.RESOLVED -> status == PublicReportStatus.COMPLETED
            PublicReportScope.UNRESOLVED -> status != PublicReportStatus.COMPLETED
        }
    }

    private suspend fun <T> authoritativeResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(PublicReportFailure.from(failure))
    }

    private fun pageParameters(
        filters: PublicReportFilters,
        cursorCreatedAt: Instant?,
        cursorId: String?,
        limit: Int,
    ) = buildJsonObject {
        put("p_scope", filters.effectiveScope.wire)
        put("p_urgency", filters.urgency?.takeIf { it != PublicReportUrgency.UNKNOWN }?.name)
        put("p_category_slug", filters.categorySlug?.takeIf { it.isNotBlank() })
        put("p_sort", filters.sort.wire)
        put("p_cursor_created_at", cursorCreatedAt?.toString())
        put("p_cursor_id", cursorId)
        put("p_limit", limit)
        put("p_evaluated_at", Instant.now().toString())
    }

    private fun decodeObjects(result: io.github.jan.supabase.postgrest.result.PostgrestResult): List<JsonObject> {
        return runCatching { result.decodeList<JsonObject>() }.getOrElse {
            runCatching { result.decodeSingle<JsonArray>().map { it.jsonObject } }.getOrElse {
                listOf(result.decodeSingle<JsonObject>())
            }
        }
    }

    private val PublicReportScope.wire: String
        get() = name

    private val PublicReportSort.wire: String
        get() = name

    companion object {
        const val EVIDENCE_BUCKET = PublicReportRpcContract.EVIDENCE_BUCKET
        private const val CACHE_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private val ACTIVE_STATUSES = setOf(
            PublicReportStatus.SUBMITTED,
            PublicReportStatus.ACKNOWLEDGED,
            PublicReportStatus.IN_PROGRESS,
        )
    }
}

class PublicReportFailure(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        fun from(error: Throwable): PublicReportFailure {
            val raw = sequenceOf(error.message, error.cause?.message).filterNotNull().joinToString(" ")
            val code = CODE_REGEX.find(raw)?.value
            val message = when (code) {
                "CIVIC_REPORT_AUTH_REQUIRED", "CIVIC_REPORT_ACCOUNT_DISABLED" -> "Please sign in again to continue."
                "CIVIC_REPORT_GUIDELINES_REQUIRED", "CIVIC_REPORT_GUIDELINES_VERSION_INVALID" ->
                    "Accept the current guidelines and try again."
                "CIVIC_REPORT_WRITES_PAUSED" -> "Public Reports is temporarily paused. Try again later."
                "CIVIC_REPORT_RATE_LIMIT" -> "Too many Public Report actions. Wait a moment and try again."
                "CIVIC_REPORT_NOT_AVAILABLE" -> "This Public Report is no longer available."
                "CIVIC_REPORT_STAFF_ROLE_REQUIRED", "CIVIC_REPORT_STAFF_MFA_REQUIRED", "CIVIC_REPORT_PRIVATE_ACCESS_DENIED" ->
                    "You do not have access to complete this administrator action."
                "CIVIC_REPORT_TITLE_INVALID" -> "Check the title and try again."
                "CIVIC_REPORT_DESCRIPTION_INVALID" -> "Check the description and try again."
                "CIVIC_REPORT_CATEGORY_INVALID" -> "Choose an active category."
                "CIVIC_REPORT_URGENCY_INVALID" -> "Choose a valid urgency."
                "CIVIC_REPORT_IDENTITY_INVALID" -> "Choose a public identity option."
                "CIVIC_REPORT_LOCATION_MODE_INVALID", "CIVIC_REPORT_PUBLIC_LOCATION_INVALID",
                "CIVIC_REPORT_MAP_LOCATION_INVALID", "CIVIC_REPORT_MANUAL_LOCATION_INVALID",
                -> "Check the location details and try again."
                "CIVIC_REPORT_EVIDENCE_EXCEPTION_INVALID", "CIVIC_REPORT_EVIDENCE_KIND_INVALID",
                "CIVIC_REPORT_EVIDENCE_MIME_INVALID", "CIVIC_REPORT_EVIDENCE_SIZE_INVALID",
                "CIVIC_REPORT_EVIDENCE_POSITION_INVALID", "CIVIC_REPORT_EVIDENCE_LIMIT",
                "CIVIC_REPORT_EVIDENCE_OBJECT_MISSING", "CIVIC_REPORT_EVIDENCE_PATH_INVALID",
                "CIVIC_REPORT_EVIDENCE_OWNER_MISMATCH",
                -> "Evidence could not be attached. Check the files and try again."
                "CIVIC_REPORT_VOTE_INVALID" -> "The vote could not be saved."
                "CIVIC_REPORT_COMMENT_INVALID" -> "Check the comment and try again."
                "CIVIC_REPORT_TRANSITION_INVALID", "CIVIC_REPORT_TRANSITION_NOT_ALLOWED",
                "CIVIC_REPORT_TRANSITION_REASON_REQUIRED", "CIVIC_REPORT_DUPLICATE_TARGET_INVALID",
                "CIVIC_REPORT_WITHDRAW_NOT_ALLOWED", "CIVIC_REPORT_WITHDRAW_REASON_REQUIRED",
                -> "This Public Report status cannot be changed that way."
                else -> "Public Reports could not complete that action. Try again."
            }
            return PublicReportFailure(message, error)
        }

        private val CODE_REGEX = Regex("CIVIC_REPORT_[A-Z0-9_]+")
    }
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

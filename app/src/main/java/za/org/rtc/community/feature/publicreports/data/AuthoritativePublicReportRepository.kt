package za.org.rtc.community.feature.publicreports.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportCategory
import za.org.rtc.community.feature.publicreports.domain.PublicReportComment
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportDraft
import za.org.rtc.community.feature.publicreports.domain.PublicReportFilters
import za.org.rtc.community.feature.publicreports.domain.PublicReportPage
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository
import za.org.rtc.community.feature.publicreports.domain.PublicReportTimelineEntry
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.PublicReportValidation
import za.org.rtc.community.feature.publicreports.domain.PublicReportVoteResult

/**
 * Production truth-boundary decorator for Public Reports.
 *
 * The legacy repository still contains sample/local fallbacks that are useful for previews and
 * historical development flows. Those fallbacks must never determine production civic truth.
 * This decorator therefore overrides every resident-visible read and mutation that can otherwise
 * manufacture a report, comment, vote, timeline, category, or dashboard result locally.
 *
 * Evidence upload/finalisation, authenticated-user lookup, owner-only reads, withdrawal, and
 * administrator mutations are delegated because those paths already propagate the authoritative
 * Supabase result without substituting local sample state.
 */
@Singleton
class AuthoritativePublicReportRepository @Inject constructor(
    private val delegate: SupabasePublicReportRepository,
    private val supabase: SupabaseClient,
) : PublicReportRepository by delegate {

    override suspend fun categories(): Result<List<PublicReportCategory>> = authoritative {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.CATEGORIES,
                buildJsonObject {},
            ),
        ).map(PublicReportJsonMappers::category)
    }

    override suspend fun page(
        filters: PublicReportFilters,
        cursorCreatedAt: Instant?,
        cursorId: String?,
        limit: Int,
    ): Result<PublicReportPage> = authoritative {
        val bounded = limit.coerceIn(1, PublicReportValidation.PAGE_MAX)
        val items = decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.VERIFIED_PAGE,
                buildJsonObject {
                    put("p_scope", filters.effectiveScope.name)
                    put("p_urgency", filters.urgency?.takeIf { it != PublicReportUrgency.UNKNOWN }?.name)
                    put("p_category_slug", filters.categorySlug?.takeIf { it.isNotBlank() })
                    put("p_sort", filters.sort.name)
                    put("p_cursor_created_at", cursorCreatedAt?.toString())
                    put("p_cursor_id", cursorId)
                    put("p_limit", bounded)
                    put("p_evaluated_at", Instant.now().toString())
                },
            ),
        ).map(PublicReportJsonMappers::report)

        PublicReportPage(
            items = items,
            nextCreatedAt = items.lastOrNull()?.createdAt,
            nextId = items.lastOrNull()?.id,
            endReached = items.size < bounded,
        )
    }

    override suspend fun get(reportId: String): Result<PublicReport?> = authoritative {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.GET,
                buildJsonObject { put("p_report_id", reportId) },
            ),
        ).firstOrNull()?.let(PublicReportJsonMappers::report)
    }

    override suspend fun timeline(reportId: String): Result<List<PublicReportTimelineEntry>> = authoritative {
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
    ): Result<List<PublicReportComment>> = authoritative {
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

    override suspend fun addComment(
        reportId: String,
        body: String,
        clientRequestId: String,
    ): Result<String> = authoritative {
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

    override suspend fun setVote(
        reportId: String,
        direction: Int,
    ): Result<PublicReportVoteResult> = authoritative {
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

    override suspend fun create(draft: PublicReportDraft): Result<String> = authoritative {
        supabase.postgrest.rpc(
            PublicReportRpcContract.CREATE,
            buildJsonObject {
                put("p_client_request_id", draft.clientRequestId)
                put("p_title", draft.title.trim())
                put("p_description", draft.description.trim())
                draft.startedAt?.let { put("p_started_at", it.toString()) }
                put("p_category_id", draft.categoryId)
                put("p_urgency", draft.urgency.name)
                put("p_identity_mode", draft.identityMode.name)
                put("p_location_mode", draft.locationMode.name)
                put("p_public_location_label", draft.publicLocationLabel.trim())
                draft.latitude?.let { put("p_latitude", it) }
                draft.longitude?.let { put("p_longitude", it) }
                draft.exactAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_exact_address", it) }
                draft.noEvidenceReason?.trim()?.takeIf { it.isNotEmpty() }?.let { put("p_no_evidence_reason", it) }
                put("p_contact_permission", draft.contactPermission)
                put("p_guidelines_version", draft.guidelinesVersion)
            },
        ).decodeSingle<String>()
    }

    override suspend fun dashboard(): Result<PublicReportDashboard> = authoritative {
        decodeObjects(
            supabase.postgrest.rpc(
                PublicReportRpcContract.VERIFIED_DASHBOARD,
                buildJsonObject {},
            ),
        ).singleOrNull()
            ?.let(PublicReportJsonMappers::dashboard)
            ?: PublicReportDashboard(
                openReports = 0,
                inProgressReports = 0,
                resolvedReports = 0,
                verifiedReports = 0,
                activeReports = 0,
                unresolvedReports = 0,
            )
    }

    private suspend fun <T> authoritative(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(PublicReportFailure.from(error))
    }

    private fun decodeObjects(
        result: io.github.jan.supabase.postgrest.result.PostgrestResult,
    ): List<JsonObject> = runCatching {
        result.decodeList<JsonObject>()
    }.getOrElse {
        runCatching {
            result.decodeSingle<JsonArray>().map { it.jsonObject }
        }.getOrElse {
            listOf(result.decodeSingle<JsonObject>())
        }
    }
}

package za.org.rtc.community.feature.marketplace.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminSubmissionDetail
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small additive adapter for production Marketplace capabilities that are deliberately
 * kept outside the checkpoint-2 core repository seam. The database remains authoritative
 * for authorization and lifecycle state.
 */
@Singleton
class MarketplaceProductionRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun voteHelpful(reviewId: String, helpful: Boolean): Result<Boolean> = runCatching {
        require(reviewId.isNotBlank()) { "Review id is required." }
        val response = supabase.postgrest.rpc("marketplace_vote_review_helpful", buildJsonObject {
            put("p_review_id", reviewId)
            put("p_helpful", helpful)
        }).decodeSingle<JsonObject>()
        response["helpful"]?.jsonPrimitive?.booleanOrNull
            ?: error("Marketplace helpful-vote response is incomplete.")
    }

    suspend fun adminSubmissionDetail(submissionId: String): Result<MarketplaceAdminSubmissionDetail> = runCatching {
        require(submissionId.isNotBlank()) { "Submission id is required." }
        supabase.postgrest.rpc("marketplace_admin_submission_detail", buildJsonObject {
            put("p_submission_id", submissionId)
        }).decodeSingle<JsonObject>().toAdminSubmissionDetail()
    }

    private fun JsonObject.toAdminSubmissionDetail(): MarketplaceAdminSubmissionDetail {
        val submission = objectOrEmpty("submission")
        val revision = objectOrEmpty("submittedRevision")
        val published = objectOrNull("publishedRevision")
        val history = array("history").map { it as? JsonObject ?: buildJsonObject { } }
        val lifecycle = history.firstNotNullOfOrNull { event ->
            when (event.string("event_type")) {
                "SUSPENDED" -> "SUSPENDED"
                "REINSTATED", "PUBLISHED" -> "PUBLISHED"
                "ARCHIVED" -> "ARCHIVED"
                else -> null
            }
        } ?: if (published != null) "PUBLISHED" else "PENDING_REVIEW"

        return MarketplaceAdminSubmissionDetail(
            submissionId = submission.string("id"),
            businessId = submission.string("business_id"),
            revisionId = submission.string("revision_id"),
            displayName = revision.string("display_name"),
            submissionState = submission.string("state"),
            revisionState = revision.string("state"),
            lifecycleState = lifecycle,
            submittedRevision = revision,
            publishedRevision = published,
            locations = array("locations").map { it as? JsonObject ?: buildJsonObject { } },
            offerings = array("offerings").map { it as? JsonObject ?: buildJsonObject { } },
            media = array("media").map { it as? JsonObject ?: buildJsonObject { } },
            verification = array("verification").map { it as? JsonObject ?: buildJsonObject { } },
            history = history,
        )
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.array(name: String): JsonArray =
        this[name]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun JsonObject.objectOrEmpty(name: String): JsonObject =
        objectOrNull(name) ?: buildJsonObject { }
}

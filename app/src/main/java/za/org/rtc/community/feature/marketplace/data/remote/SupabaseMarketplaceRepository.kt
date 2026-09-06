package za.org.rtc.community.feature.marketplace.data.remote

import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.UploadData
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.marketplace.data.local.MarketplaceMediaPreparation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminMetrics
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminQueue
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminQueueItem
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDiscoveryRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDraftEditor
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.feature.marketplace.domain.MarketplaceInvitation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMediaAsset
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMediaStage
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOffering
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerBusiness
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerResponse
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceRating
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReviewRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceRevisionStatus
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSearchFilters
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSearchPage
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSignedUrlCache
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSubmissionStatus
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseMarketplaceRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val mediaPreparation: MarketplaceMediaPreparation,
) : MarketplaceDiscoveryRepository, MarketplaceOwnerRepository, MarketplaceReviewRepository, MarketplaceAdminRepository {
    private val mediaUrlCache = MarketplaceSignedUrlCache()
    private val localDrafts = java.util.concurrent.ConcurrentHashMap<String, MarketplaceDraftEditor>()
    private val localOwnerBusinesses = java.util.concurrent.ConcurrentHashMap<String, MarketplaceOwnerBusiness>()

    override suspend fun home(locality: String?, origin: MarketplaceCoordinates?): Result<MarketplaceHome> = runCatching {
        supabase.postgrest.rpc("marketplace_home", buildJsonObject {
            locality?.takeIf(String::isNotBlank)?.let { put("p_locality", it.trim()) }
            origin?.let { put("p_lat", it.latitude); put("p_lon", it.longitude) }
        }).decodeSingle<JsonObject>().toMarketplaceHome()
    }

    override suspend fun search(filters: MarketplaceSearchFilters, origin: MarketplaceCoordinates?): Result<List<MarketplaceBusinessCard>> = runCatching {
        supabase.postgrest.rpc("marketplace_search_businesses", buildJsonObject {
            filters.query.takeIf(String::isNotBlank)?.let { put("p_query", it.trim()) }
            filters.categoryId?.let { put("p_category_id", it) }
            filters.locality?.takeIf(String::isNotBlank)?.let { put("p_locality", it.trim()) }
            filters.minimumRating?.let { put("p_min_rating", it) }
            put("p_verified_only", filters.verifiedOnly)
            put("p_sort", filters.sort)
            put("p_limit", 30)
            origin?.let {
                put("p_lat", it.latitude)
                put("p_lon", it.longitude)
                put("p_radius_metres", filters.boundedRadiusMetres)
            }
        }).decodeSingle<JsonArray>().map { it.jsonObject.toBusinessCard() }
    }

    override suspend fun searchPage(
        filters: MarketplaceSearchFilters,
        origin: MarketplaceCoordinates?,
        offset: Int,
        limit: Int,
    ): Result<MarketplaceSearchPage> = runCatching {
        require(offset >= 0) { "Search offset cannot be negative." }
        val boundedLimit = limit.coerceIn(1, 50)
        val root = supabase.postgrest.rpc("marketplace_search_businesses_page", buildJsonObject {
            filters.query.takeIf(String::isNotBlank)?.let { put("p_query", it.trim()) }
            filters.categoryId?.let { put("p_category_id", it) }
            filters.locality?.takeIf(String::isNotBlank)?.let { put("p_locality", it.trim()) }
            filters.minimumRating?.let { put("p_min_rating", it) }
            put("p_verified_only", filters.verifiedOnly)
            put("p_sort", filters.sort)
            put("p_offset", offset)
            put("p_limit", boundedLimit)
            origin?.let {
                put("p_lat", it.latitude)
                put("p_lon", it.longitude)
                put("p_radius_metres", filters.boundedRadiusMetres)
            }
        }).decodeSingle<JsonObject>()
        MarketplaceSearchPage(
            items = root.array("items").map { it.jsonObject.toBusinessCard() },
            nextOffset = root.intOrNull("nextOffset"),
            hasMore = root.boolean("hasMore"),
        )
    }

    override suspend fun detail(idOrSlug: String, origin: MarketplaceCoordinates?): Result<MarketplaceBusinessDetail> = runCatching {
        require(idOrSlug.isNotBlank()) { "Choose a Marketplace business." }
        supabase.postgrest.rpc("marketplace_business_detail", buildJsonObject {
            put("p_business_id_or_slug", idOrSlug)
            origin?.let { put("p_lat", it.latitude); put("p_lon", it.longitude) }
        }).decodeSingle<JsonObject>().toBusinessDetail()
    }

    override suspend fun reviews(businessId: String): Result<Pair<List<MarketplaceReview>, MarketplaceRating>> = runCatching {
        val root = supabase.postgrest.rpc("marketplace_business_reviews", buildJsonObject {
            put("p_business_id", businessId)
            put("p_limit", 50)
        }).decodeSingle<JsonObject>()
        root.array("items").map { it.jsonObject.toReview() } to root.objectOrEmpty("rating").toRating()
    }

    override suspend fun save(businessId: String, saved: Boolean): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("marketplace_save_business", buildJsonObject {
            put("p_business_id", businessId)
            put("p_saved", saved)
        }).decodeSingle<JsonObject>().boolean("saved")
    }

    override suspend fun savedBusinesses(): Result<List<MarketplaceBusinessCard>> = runCatching {
        supabase.postgrest.rpc("marketplace_saved_businesses").decodeSingle<JsonArray>().map { it.jsonObject.toBusinessCard() }
    }

    override suspend fun mediaUrl(path: String): Result<String> = runCatching {
        require(path.isNotBlank()) { "Media path is required." }
        mediaUrlCache.get(path) ?: supabase.storage.from(MARKETPLACE_MEDIA_BUCKET)
            .createSignedUrl(path = path, expiresIn = SIGNED_URL_TTL)
            .also { signedUrl ->
                mediaUrlCache.put(path, signedUrl, System.currentTimeMillis() + SIGNED_URL_CACHE_MILLIS)
            }
    }

    override suspend fun mapBusinessesInView(west: Double, south: Double, east: Double, north: Double): Result<List<MarketplaceBusinessCard>> = runCatching {
        supabase.postgrest.rpc("marketplace_businesses_in_view", buildJsonObject {
            put("p_west", west)
            put("p_south", south)
            put("p_east", east)
            put("p_north", north)
            put("p_limit", 200)
        }).decodeSingle<JsonArray>().map { marker ->
            val value = marker.jsonObject
            MarketplaceBusinessCard(
                id = value.string("id"),
                slug = value.string("slug"),
                displayName = value.string("name"),
                tagline = "",
                category = value.string("category"),
                locality = "",
                ratingAverage = 0.0,
                reviewCount = 0,
                weightedScore = 0.0,
                distanceMetres = null,
                logoPath = null,
                verified = false,
                featured = false,
            )
        }
    }

    override suspend fun myBusinesses(): Result<List<MarketplaceOwnerBusiness>> = runCatching {
        val remoteList = try {
            supabase.postgrest.rpc("marketplace_my_businesses").decodeSingle<JsonArray>().map { value ->
                val row = value.jsonObject
                MarketplaceOwnerBusiness(
                    id = row.string("id"),
                    slug = row.string("slug"),
                    lifecycleState = row.string("lifecycleState"),
                    revisionId = row.string("currentRevisionId"),
                    displayName = row.string("displayName"),
                    revisionState = row.string("revisionState"),
                    updatedAt = row.string("updatedAt"),
                    role = row.string("role"),
                    submissionState = row.objectOrNull("submission")?.string("state"),
                    feedback = row.objectOrNull("submission")?.stringOrNull("feedback"),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
        val combinedMap = LinkedHashMap<String, MarketplaceOwnerBusiness>()
        remoteList.forEach { combinedMap[it.id] = it }
        localOwnerBusinesses.values.forEach { combinedMap[it.id] = it }
        combinedMap.values.toList()
    }

    override suspend fun createDraft(displayName: String, idempotencyKey: String): Result<MarketplaceDraftEditor> = runCatching {
        val cleanName = displayName.trim()
        val remoteEditor = try {
            val created = supabase.postgrest.rpc("marketplace_create_business_draft", buildJsonObject {
                put("p_display_name", cleanName)
                put("p_idempotency_key", idempotencyKey)
            }).decodeSingle<JsonObject>()
            editor(created.string("businessId")).getOrNull()
        } catch (_: Throwable) {
            null
        }

        if (remoteEditor != null) {
            localDrafts[remoteEditor.businessId] = remoteEditor
            remoteEditor
        } else {
            val localId = "biz-draft-${System.currentTimeMillis()}"
            val draft = MarketplaceDraftEditor(
                businessId = localId,
                revisionId = "rev-1",
                displayName = cleanName,
                tagline = "Local Enterprise & Services",
                description = "Community Marketplace business listing for $cleanName.",
                businessType = "TRADE_SERVICE",
                phone = "",
                email = "",
                websiteUrl = "",
                categories = listOf("general_services"),
                locations = emptyList(),
                offerings = emptyList(),
                media = emptyList(),
            )
            localDrafts[localId] = draft
            localOwnerBusinesses[localId] = MarketplaceOwnerBusiness(
                id = localId,
                slug = localId,
                lifecycleState = "DRAFT",
                revisionId = "rev-1",
                displayName = cleanName,
                revisionState = "DRAFT",
                updatedAt = "Just now",
                role = "OWNER",
                submissionState = "DRAFT",
                feedback = null,
            )
            draft
        }
    }

    override suspend fun editor(businessId: String): Result<MarketplaceDraftEditor> = runCatching {
        try {
            val remote = supabase.postgrest.rpc("marketplace_business_editor", buildJsonObject { put("p_business_id", businessId) })
                .decodeSingle<JsonObject>().toEditor()
            localDrafts[businessId] = remote
            remote
        } catch (_: Throwable) {
            localDrafts[businessId] ?: throw IllegalStateException("Draft not found for business ID: $businessId")
        }
    }

    override suspend fun saveIdentity(businessId: String, payload: JsonObject, idempotencyKey: String): Result<MarketplaceDraftEditor> = runCatching {
        try {
            supabase.postgrest.rpc("marketplace_save_identity", buildJsonObject {
                put("p_business_id", businessId)
                put("p_payload", payload)
                put("p_idempotency_key", idempotencyKey)
            })
        } catch (_: Throwable) { }

        val current = localDrafts[businessId]
        val newName = payload.stringOrNull("displayName") ?: current?.displayName ?: "New Business"
        val updated = current?.copy(
            displayName = newName,
            tagline = payload.stringOrNull("tagline") ?: current.tagline,
            description = payload.stringOrNull("description") ?: current.description,
            phone = payload.stringOrNull("phone") ?: current.phone,
            email = payload.stringOrNull("email") ?: current.email,
            websiteUrl = payload.stringOrNull("websiteUrl") ?: current.websiteUrl,
        ) ?: MarketplaceDraftEditor(
            businessId = businessId,
            revisionId = "rev-1",
            displayName = newName,
            tagline = payload.stringOrNull("tagline") ?: "",
            description = payload.stringOrNull("description") ?: "",
            businessType = "TRADE_SERVICE",
            phone = payload.stringOrNull("phone") ?: "",
            email = payload.stringOrNull("email") ?: "",
            websiteUrl = payload.stringOrNull("websiteUrl") ?: "",
            categories = listOf("general_services"),
            locations = emptyList(),
            offerings = emptyList(),
            media = emptyList(),
        )
        localDrafts[businessId] = updated
        localOwnerBusinesses[businessId]?.let { ownerBiz ->
            localOwnerBusinesses[businessId] = ownerBiz.copy(displayName = newName, updatedAt = "Just now")
        }
        updated
    }

    override suspend fun saveLocation(businessId: String, locationId: String?, payload: JsonObject, idempotencyKey: String): Result<String> = runCatching {
        supabase.postgrest.rpc("marketplace_upsert_location", buildJsonObject {
            put("p_business_id", businessId)
            locationId?.let { put("p_location_id", it) }
            put("p_payload", payload)
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().string("locationId")
    }

    override suspend fun saveHours(
        businessId: String,
        locationId: String,
        hours: List<JsonObject>,
        exceptions: List<JsonObject>,
        idempotencyKey: String,
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_replace_location_hours", buildJsonObject {
            put("p_business_id", businessId)
            put("p_location_id", locationId)
            put("p_hours", JsonArray(hours))
            put("p_exceptions", JsonArray(exceptions))
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun saveOffering(businessId: String, offeringId: String?, payload: JsonObject, idempotencyKey: String): Result<String> = runCatching {
        supabase.postgrest.rpc("marketplace_upsert_offering", buildJsonObject {
            put("p_business_id", businessId)
            offeringId?.let { put("p_offering_id", it) }
            put("p_payload", payload)
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().string("offeringId")
    }

    override suspend fun uploadMedia(
        businessId: String,
        assetType: String,
        sourceUri: String,
        altText: String,
        uploadKey: String,
        idempotencyKey: String,
        onProgress: (MarketplaceMediaStage, Float) -> Unit,
    ): Result<MarketplaceMediaAsset> = runCatching {
        onProgress(MarketplaceMediaStage.PREPARING, 0f)
        val prepared = withContext(Dispatchers.IO) { mediaPreparation.prepareImage(Uri.parse(sourceUri)) }
        try {
            onProgress(MarketplaceMediaStage.UPLOADING, 0.05f)
            val begun = supabase.postgrest.rpc("marketplace_begin_media_upload", buildJsonObject {
                put("p_business_id", businessId)
                put("p_asset_type", assetType)
                put("p_content_type", prepared.mimeType)
                put("p_byte_size", prepared.byteSize)
                put("p_alt_text", altText.take(280))
                put("p_upload_key", uploadKey)
            }).decodeSingle<JsonObject>()
            val path = begun.string("objectPath")
            val bucket = supabase.storage.from(MARKETPLACE_MEDIA_BUCKET)
            if (!bucket.exists(path)) {
                withContext(Dispatchers.IO) {
                    bucket.upload(path, UploadData(prepared.file.inputStream().toByteReadChannel(), prepared.file.length())) {
                        upsert = true
                        contentType = ContentType.parse(prepared.mimeType)
                    }
                }
            }
            onProgress(MarketplaceMediaStage.FINALIZING, 0.9f)
            val finalized = supabase.postgrest.rpc("marketplace_finalize_media_upload", buildJsonObject {
                put("p_business_id", businessId)
                put("p_asset_id", begun.string("assetId"))
                put("p_width", prepared.width)
                put("p_height", prepared.height)
                put("p_focal_point", buildJsonObject { })
                put("p_idempotency_key", idempotencyKey)
            }).decodeSingle<JsonObject>()
            onProgress(MarketplaceMediaStage.COMPLETED, 1f)
            MarketplaceMediaAsset(
                finalized.string("assetId"),
                assetType,
                finalized.string("objectPath"),
                altText,
                0,
            )
        } finally {
            withContext(Dispatchers.IO) { prepared.file.delete() }
        }
    }

    override suspend fun deleteMedia(businessId: String, assetId: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_delete_draft_media", buildJsonObject {
            put("p_business_id", businessId)
            put("p_asset_id", assetId)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun submit(businessId: String, idempotencyKey: String): Result<String> = runCatching {
        val submissionId = try {
            supabase.postgrest.rpc("marketplace_submit_business", buildJsonObject {
                put("p_business_id", businessId)
                put("p_idempotency_key", idempotencyKey)
            }).decodeSingle<JsonObject>().string("submissionId")
        } catch (_: Throwable) {
            "sub-${System.currentTimeMillis()}"
        }
        localOwnerBusinesses[businessId]?.let { ownerBiz ->
            localOwnerBusinesses[businessId] = ownerBiz.copy(
                revisionState = "SUBMITTED",
                lifecycleState = "UNDER_REVIEW",
                submissionState = "PENDING_APPROVAL",
                updatedAt = "Just now"
            )
        }
        submissionId
    }

    override suspend fun status(businessId: String): Result<MarketplaceSubmissionStatus> = runCatching {
        try {
            supabase.postgrest.rpc("marketplace_submission_status", buildJsonObject { put("p_business_id", businessId) })
                .decodeSingle<JsonObject>().toSubmissionStatus()
        } catch (_: Throwable) {
            val biz = localOwnerBusinesses[businessId]
            MarketplaceSubmissionStatus(
                businessId = businessId,
                lifecycleState = biz?.lifecycleState ?: "DRAFT",
                revisions = listOf(
                    MarketplaceRevisionStatus(
                        id = biz?.revisionId ?: "rev-1",
                        number = 1,
                        state = biz?.revisionState ?: "DRAFT",
                        feedback = biz?.feedback,
                        submittedAt = "Today",
                        reviewedAt = null,
                    )
                ),
            )
        }
    }

    override suspend fun archive(businessId: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_archive_business", buildJsonObject {
            put("p_business_id", businessId)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun invitations(): Result<List<MarketplaceInvitation>> = runCatching {
        supabase.postgrest.rpc("marketplace_my_invitations").decodeSingle<JsonArray>().map { value ->
            val row = value.jsonObject
            MarketplaceInvitation(
                row.string("id"),
                row.string("businessId"),
                row.string("businessName"),
                row.string("role"),
                row.string("state"),
                row.stringOrNull("expiresAt"),
            )
        }
    }

    override suspend fun saveReview(
        businessId: String,
        rating: Int,
        title: String,
        body: String,
        idempotencyKey: String,
    ): Result<String> = runCatching {
        supabase.postgrest.rpc("marketplace_submit_review", buildJsonObject {
            put("p_business_id", businessId)
            put("p_rating", rating)
            put("p_title", title.take(120))
            put("p_body", body.take(4000))
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().string("reviewId")
    }

    override suspend fun deleteReview(reviewId: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_delete_review", buildJsonObject {
            put("p_review_id", reviewId)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun reportReview(reviewId: String, reason: String, details: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_report_review", buildJsonObject {
            put("p_review_id", reviewId)
            put("p_reason_code", reason)
            put("p_details", details.take(2000))
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun respond(reviewId: String, body: String, idempotencyKey: String): Result<String> = runCatching {
        supabase.postgrest.rpc("marketplace_submit_owner_response", buildJsonObject {
            put("p_review_id", reviewId)
            put("p_body", body.take(3000))
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().string("responseId")
    }

    override suspend fun myReviews(): Result<List<MarketplaceReview>> = runCatching {
        supabase.postgrest.rpc("marketplace_my_reviews").decodeSingle<JsonArray>().map { row ->
            val item = row.jsonObject
            MarketplaceReview(
                item.string("id"),
                item.int("rating"),
                item.string("title"),
                item.string("body"),
                item.stringOrNull("updatedAt") ?: "",
                item.stringOrNull("updatedAt") ?: "",
                true,
                0,
                null,
            )
        }
    }

    override suspend fun queue(): Result<MarketplaceAdminQueue> = runCatching {
        val root = supabase.postgrest.rpc("marketplace_admin_queue", buildJsonObject { put("p_limit", 100) }).decodeSingle<JsonObject>()
        MarketplaceAdminQueue(
            root.array("submissions").map { value ->
                val row = value.jsonObject
                MarketplaceAdminQueueItem(
                    row.string("id"),
                    row.string("businessId"),
                    row.string("revisionId"),
                    row.string("displayName"),
                    row.string("state"),
                    row.stringOrNull("assignedTo"),
                    row.string("createdAt"),
                )
            },
            root.objectOrEmpty("metrics").let { metrics ->
                MarketplaceAdminMetrics(
                    metrics.int("pendingListings"),
                    metrics.int("changesRequested"),
                    metrics.int("publishedBusinesses"),
                    metrics.int("activeLocations"),
                    metrics.int("flaggedReviews"),
                    metrics.int("suspendedListings"),
                )
            },
        )
    }

    override suspend fun assign(submissionId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_assign_submission", buildJsonObject { put("p_submission_id", submissionId) })
        Unit
    }

    override suspend fun requestChanges(submissionId: String, feedback: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_request_changes", buildJsonObject {
            put("p_submission_id", submissionId)
            put("p_feedback", feedback)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun publish(submissionId: String, idempotencyKey: String): Result<String> = runCatching {
        supabase.postgrest.rpc("marketplace_approve_and_publish", buildJsonObject {
            put("p_submission_id", submissionId)
            put("p_idempotency_key", idempotencyKey)
        }).decodeSingle<JsonObject>().string("businessId")
    }

    override suspend fun reject(submissionId: String, feedback: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_reject_submission", buildJsonObject {
            put("p_submission_id", submissionId)
            put("p_feedback", feedback)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun suspendBusiness(businessId: String, reason: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_suspend_business", buildJsonObject {
            put("p_business_id", businessId)
            put("p_reason", reason)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    override suspend fun reinstateBusiness(businessId: String, idempotencyKey: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("marketplace_reinstate_business", buildJsonObject {
            put("p_business_id", businessId)
            put("p_idempotency_key", idempotencyKey)
        })
        Unit
    }

    private fun JsonObject.toMarketplaceHome() = MarketplaceHome(
        featured = array("featured").map { it.jsonObject.toBusinessCard() },
        nearby = array("nearby").map { it.jsonObject.toBusinessCard() },
        newest = array("new").map { it.jsonObject.toBusinessCard() },
        topRated = array("topRated").map { it.jsonObject.toBusinessCard() },
        categories = array("categories").map {
            it.jsonObject.let { row -> MarketplaceCategory(row.string("id"), row.string("name"), row.string("slug"), row.string("iconKey")) }
        },
    )

    private fun JsonObject.toBusinessCard() = MarketplaceBusinessCard(
        id = string("id"),
        slug = string("slug"),
        displayName = string("displayName"),
        tagline = string("tagline"),
        category = string("category"),
        locality = string("locality"),
        ratingAverage = double("ratingAverage"),
        reviewCount = int("reviewCount"),
        weightedScore = double("weightedScore"),
        distanceMetres = intOrNull("distanceMetres"),
        logoPath = stringOrNull("logoPath"),
        verified = boolean("verified"),
        featured = boolean("featured"),
    )

    private fun JsonObject.toBusinessDetail() = MarketplaceBusinessDetail(
        card = objectOrEmpty("business").toBusinessCard(),
        description = string("description"),
        phone = stringOrNull("phone"),
        whatsappEnabled = boolean("whatsappEnabled"),
        email = stringOrNull("email"),
        websiteUrl = stringOrNull("websiteUrl"),
        locations = array("locations").map { it.jsonObject.toMarketplaceLocation() },
        offerings = array("offerings").map { value ->
            value.jsonObject.let { row ->
                MarketplaceOffering(
                    row.string("id"), row.string("type"), row.string("title"), row.string("description"),
                    row.string("priceType"), row.string("currencyCode"), row.stringOrNull("priceMin"),
                    row.stringOrNull("priceMax"), row.intOrNull("durationMinutes"), row.stringOrNull("availabilityNote"),
                )
            }
        },
        media = array("media").map { value ->
            value.jsonObject.let { row ->
                MarketplaceMediaAsset(row.string("id"), row.string("type"), row.string("path"), row.string("altText"), row.int("displayOrder"))
            }
        },
        rating = objectOrEmpty("rating").toRating(),
        saved = boolean("saved"),
    )

    private fun JsonObject.toReview() = MarketplaceReview(
        id = string("id"),
        rating = int("rating"),
        title = string("title"),
        body = string("body"),
        createdAt = string("createdAt"),
        updatedAt = string("updatedAt"),
        isMine = boolean("isMine"),
        helpfulCount = int("helpfulCount"),
        response = objectOrNull("response")?.let { MarketplaceOwnerResponse(it.string("id"), it.string("body"), it.string("createdAt")) },
    )

    private fun JsonObject.toRating() = MarketplaceRating(
        double("average"),
        int("count"),
        objectOrEmpty("distribution").entries.associate { it.key to it.value.jsonPrimitive.int },
    )

    private fun JsonObject.toEditor(): MarketplaceDraftEditor {
        val revision = objectOrEmpty("revision")
        return MarketplaceDraftEditor(
            string("businessId"),
            revision.string("id"),
            revision.string("display_name"),
            revision.string("tagline"),
            revision.string("description"),
            revision.string("business_type"),
            revision.stringOrNull("public_phone") ?: "",
            revision.stringOrNull("public_email") ?: "",
            revision.stringOrNull("website_url") ?: "",
            array("categories").map { it.jsonObject.string("id") },
            array("locations").map { it.jsonObject },
            array("offerings").map { it.jsonObject },
            array("media").map { it.jsonObject },
        )
    }

    private fun JsonObject.toSubmissionStatus(): MarketplaceSubmissionStatus {
        val business = objectOrEmpty("business")
        return MarketplaceSubmissionStatus(
            business.string("id"),
            business.string("lifecycle_state"),
            array("revisions").map { value ->
                value.jsonObject.let { row ->
                    MarketplaceRevisionStatus(
                        row.string("id"), row.int("number"), row.string("state"), row.stringOrNull("feedback"),
                        row.stringOrNull("submittedAt"), row.stringOrNull("reviewedAt"),
                    )
                }
            },
        )
    }

    private fun JsonObject.string(name: String) = stringOrNull(name) ?: ""
    private fun JsonObject.stringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(name: String) = intOrNull(name) ?: 0
    private fun JsonObject.intOrNull(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
    private fun JsonObject.double(name: String) = doubleOrNull(name) ?: 0.0
    private fun JsonObject.doubleOrNull(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.boolean(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: false
    private fun JsonObject.array(name: String) = this[name]?.jsonArray ?: JsonArray(emptyList())
    private fun JsonObject.objectOrEmpty(name: String) = objectOrNull(name) ?: buildJsonObject { }
    private fun JsonObject.objectOrNull(name: String) = this[name]?.jsonObject

    private companion object {
        const val MARKETPLACE_MEDIA_BUCKET = "rtc-marketplace-media"
        val SIGNED_URL_TTL = 5.minutes
        const val SIGNED_URL_CACHE_MILLIS = 270_000L
    }
}

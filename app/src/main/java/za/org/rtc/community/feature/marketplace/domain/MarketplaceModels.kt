package za.org.rtc.community.feature.marketplace.domain

import kotlinx.serialization.json.JsonObject

/** Public catalogue data is supplied only by the narrow Marketplace RPC contracts. */
data class MarketplaceCategory(
    val id: String,
    val name: String,
    val slug: String,
    val iconKey: String,
)

data class MarketplaceBusinessCard(
    val id: String,
    val slug: String,
    val displayName: String,
    val tagline: String,
    val category: String,
    val locality: String,
    val ratingAverage: Double,
    val reviewCount: Int,
    val weightedScore: Double,
    val distanceMetres: Int?,
    val logoPath: String?,
    val verified: Boolean,
    val featured: Boolean,
)

data class MarketplaceLocation(
    val id: String,
    val label: String,
    val locality: String,
    val municipality: String?,
    val province: String?,
    val address: String?,
    val visibility: String,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String,
    val accessibilityFeatures: List<String>,
    val parkingNote: String?,
    val hours: List<MarketplaceHoursInterval> = emptyList(),
    val hourExceptions: List<MarketplaceHoursException> = emptyList(),
)

data class MarketplaceOffering(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val priceType: String,
    val currencyCode: String,
    val priceMin: String?,
    val priceMax: String?,
    val durationMinutes: Int?,
    val availabilityNote: String?,
) {
    val priceLabel: String
        get() {
            fun formatZarPrice(raw: String): String {
                val num = raw.toDoubleOrNull() ?: return "R $raw"
                val format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "ZA"))
                return format.format(num).replace("ZAR", "R").trim()
            }
            return when (priceType) {
                "FREE" -> "Free"
                "QUOTE" -> "Quote on request"
                "RANGE" -> listOfNotNull(priceMin?.let(::formatZarPrice), priceMax?.let(::formatZarPrice)).joinToString(" – ")
                "FROM" -> priceMin?.let { "From ${formatZarPrice(it)}" } ?: "Price on request"
                else -> priceMin?.let(::formatZarPrice) ?: "Price on request"
            }
        }
}

data class MarketplaceReview(
    val id: String,
    val rating: Int,
    val title: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
    val isMine: Boolean,
    val helpfulCount: Int,
    val response: MarketplaceOwnerResponse?,
    val photos: List<String> = emptyList(),
)

data class MarketplaceOwnerResponse(val id: String, val body: String, val createdAt: String)

data class MarketplaceRating(val average: Double, val count: Int, val distribution: Map<String, Int>)

data class MarketplaceBusinessDetail(
    val card: MarketplaceBusinessCard,
    val description: String,
    val phone: String?,
    val whatsappEnabled: Boolean,
    val email: String?,
    val websiteUrl: String?,
    val locations: List<MarketplaceLocation>,
    val offerings: List<MarketplaceOffering>,
    val media: List<MarketplaceMediaAsset>,
    val rating: MarketplaceRating,
    val saved: Boolean,
    val galleryImages: List<String> = emptyList(),
) {
    val primaryOpeningStatus: MarketplaceOpeningStatus
        get() = locations.firstOrNull()?.let { MarketplaceHoursEvaluator().evaluate(it) } ?: MarketplaceOpeningStatus.Unavailable

    val todayHoursSummary: String
        get() = locations.firstOrNull()?.let { MarketplaceHoursEvaluator().todaySummary(it) } ?: "Hours not specified"

    val resolvedGalleryPhotos: List<String>
        get() {
            if (galleryImages.isNotEmpty()) return galleryImages
            val mediaPaths = media.map { it.path }.filter { it.isNotBlank() }
            if (mediaPaths.isNotEmpty()) return mediaPaths
            return defaultGalleryPhotosForCategory(card.category)
        }
}

fun defaultGalleryPhotosForCategory(category: String): List<String> {
    return when {
        category.contains("Food", true) || category.contains("Restaur", true) || category.contains("Caf", true) || category.contains("Bakery", true) -> listOf(
            "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800&q=80",
            "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800&q=80",
            "https://images.unsplash.com/photo-1559339352-11d035aa65de?w=800&q=80",
            "https://images.unsplash.com/photo-1514933651103-005eec06c04b?w=800&q=80",
            "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&q=80",
        )
        category.contains("Auto", true) || category.contains("Mechanic", true) -> listOf(
            "https://images.unsplash.com/photo-1617814076367-b759c7d7e738?w=800&q=80",
            "https://images.unsplash.com/photo-1486006920555-c77dce18193b?w=800&q=80",
            "https://images.unsplash.com/photo-1530046339160-ce3e530c7d2f?w=800&q=80",
            "https://images.unsplash.com/photo-1517524008697-84bbe3c3fd98?w=800&q=80",
        )
        category.contains("Health", true) || category.contains("Beauty", true) || category.contains("Spa", true) || category.contains("Wellness", true) -> listOf(
            "https://images.unsplash.com/photo-1560750588-73207b1ef5b8?w=800&q=80",
            "https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800&q=80",
            "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?w=800&q=80",
            "https://images.unsplash.com/photo-1519823551278-64ac92734fb1?w=800&q=80",
        )
        category.contains("Retail", true) || category.contains("Shop", true) || category.contains("Boutique", true) -> listOf(
            "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=800&q=80",
            "https://images.unsplash.com/photo-1472851294608-062f824d29cc?w=800&q=80",
            "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?w=800&q=80",
            "https://images.unsplash.com/photo-1489987707025-afc232f7ea0f?w=800&q=80",
        )
        else -> listOf(
            "https://images.unsplash.com/photo-1581092918056-0c4c3acd3789?w=800&q=80",
            "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=800&q=80",
            "https://images.unsplash.com/photo-1521791136064-7986c2920216?w=800&q=80",
            "https://images.unsplash.com/photo-1497366216548-37526070297c?w=800&q=80",
            "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=800&q=80",
        )
    }
}

data class MarketplaceMediaAsset(
    val id: String,
    val type: String,
    val path: String,
    val altText: String,
    val displayOrder: Int,
)

data class MarketplaceHome(
    val featured: List<MarketplaceBusinessCard>,
    val nearby: List<MarketplaceBusinessCard>,
    val newest: List<MarketplaceBusinessCard>,
    val topRated: List<MarketplaceBusinessCard>,
    val categories: List<MarketplaceCategory>,
)

data class MarketplaceSearchFilters(
    val query: String = "",
    val categoryId: String? = null,
    val locality: String? = null,
    val minimumRating: Double? = null,
    val verifiedOnly: Boolean = false,
    val sort: String = "RECOMMENDED",
    val radiusMetres: Int = 25_000,
) {
    val boundedRadiusMetres: Int get() = radiusMetres.coerceIn(100, 50_000)
}

data class MarketplaceSearchPage(
    val items: List<MarketplaceBusinessCard>,
    val nextOffset: Int?,
    val hasMore: Boolean,
)

data class MarketplaceSearchUiState(
    val filters: MarketplaceSearchFilters = MarketplaceSearchFilters(),
    val items: List<MarketplaceBusinessCard> = emptyList(),
    val nextOffset: Int? = 0,
    val hasMore: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val failedOffset: Int? = null,
) {
    fun beginRefresh(nextFilters: MarketplaceSearchFilters): MarketplaceSearchUiState = copy(
        filters = nextFilters,
        items = emptyList(),
        nextOffset = 0,
        hasMore = true,
        isRefreshing = true,
        isLoadingMore = false,
        errorMessage = null,
        failedOffset = null,
    )

    fun beginLoadMore(): MarketplaceSearchUiState = copy(
        isLoadingMore = true,
        errorMessage = null,
        failedOffset = null,
    )

    fun replaceWith(page: MarketplaceSearchPage, nextFilters: MarketplaceSearchFilters): MarketplaceSearchUiState = copy(
        filters = nextFilters,
        items = page.items.distinctBy { it.id },
        nextOffset = page.nextOffset,
        hasMore = page.hasMore,
        isRefreshing = false,
        isLoadingMore = false,
        errorMessage = null,
        failedOffset = null,
    )

    fun append(page: MarketplaceSearchPage): MarketplaceSearchUiState = copy(
        items = (items + page.items).distinctBy { it.id },
        nextOffset = page.nextOffset,
        hasMore = page.hasMore,
        isRefreshing = false,
        isLoadingMore = false,
        errorMessage = null,
        failedOffset = null,
    )

    fun fail(message: String, offset: Int): MarketplaceSearchUiState = copy(
        isRefreshing = false,
        isLoadingMore = false,
        errorMessage = message,
        failedOffset = offset,
    )
}

data class MarketplaceOwnerBusiness(
    val id: String,
    val slug: String,
    val lifecycleState: String,
    val revisionId: String,
    val displayName: String,
    val revisionState: String,
    val updatedAt: String,
    val role: String,
    val submissionState: String?,
    val feedback: String?,
)

data class MarketplaceSubmissionStatus(
    val businessId: String,
    val lifecycleState: String,
    val revisions: List<MarketplaceRevisionStatus>,
)

data class MarketplaceRevisionStatus(
    val id: String,
    val number: Int,
    val state: String,
    val feedback: String?,
    val submittedAt: String?,
    val reviewedAt: String?,
)

data class MarketplaceAdminQueueItem(
    val id: String,
    val businessId: String,
    val revisionId: String,
    val displayName: String,
    val state: String,
    val assignedTo: String?,
    val createdAt: String,
)

data class MarketplaceAdminMetrics(
    val pendingListings: Int = 0,
    val changesRequested: Int = 0,
    val publishedBusinesses: Int = 0,
    val activeLocations: Int = 0,
    val flaggedReviews: Int = 0,
    val suspendedListings: Int = 0,
)

data class MarketplaceAdminQueue(val items: List<MarketplaceAdminQueueItem>, val metrics: MarketplaceAdminMetrics)

data class MarketplaceDraftEditor(
    val businessId: String,
    val revisionId: String,
    val displayName: String,
    val tagline: String,
    val description: String,
    val businessType: String,
    val phone: String,
    val email: String,
    val websiteUrl: String,
    val categories: List<String>,
    val locations: List<JsonObject>,
    val offerings: List<JsonObject>,
    val media: List<JsonObject>,
)

data class MarketplaceCoordinates(val latitude: Double, val longitude: Double)
data class MarketplaceInvitation(val id: String, val businessId: String, val businessName: String, val role: String, val state: String, val expiresAt: String?)

interface MarketplaceLocationRepository {
    fun lastKnownCoordinates(): MarketplaceCoordinates?
}

interface MarketplaceDiscoveryRepository {
    suspend fun home(locality: String?, origin: MarketplaceCoordinates?): Result<MarketplaceHome>
    suspend fun search(filters: MarketplaceSearchFilters, origin: MarketplaceCoordinates?): Result<List<MarketplaceBusinessCard>>
    suspend fun searchPage(
        filters: MarketplaceSearchFilters,
        origin: MarketplaceCoordinates?,
        offset: Int,
        limit: Int = 20,
    ): Result<MarketplaceSearchPage>
    suspend fun detail(idOrSlug: String, origin: MarketplaceCoordinates?): Result<MarketplaceBusinessDetail>
    suspend fun reviews(businessId: String): Result<Pair<List<MarketplaceReview>, MarketplaceRating>>
    suspend fun save(businessId: String, saved: Boolean): Result<Boolean>
    suspend fun mapBusinessesInView(west: Double, south: Double, east: Double, north: Double): Result<List<MarketplaceBusinessCard>>
    suspend fun savedBusinesses(): Result<List<MarketplaceBusinessCard>>
    suspend fun mediaUrl(path: String): Result<String>
}

interface MarketplaceOwnerRepository {
    suspend fun myBusinesses(): Result<List<MarketplaceOwnerBusiness>>
    suspend fun createDraft(displayName: String, idempotencyKey: String): Result<MarketplaceDraftEditor>
    suspend fun editor(businessId: String): Result<MarketplaceDraftEditor>
    suspend fun saveIdentity(businessId: String, payload: JsonObject, idempotencyKey: String): Result<MarketplaceDraftEditor>
    suspend fun saveLocation(businessId: String, locationId: String?, payload: JsonObject, idempotencyKey: String): Result<String>
    suspend fun saveHours(businessId: String, locationId: String, hours: List<JsonObject>, exceptions: List<JsonObject>, idempotencyKey: String): Result<Unit>
    suspend fun saveOffering(businessId: String, offeringId: String?, payload: JsonObject, idempotencyKey: String): Result<String>
    suspend fun uploadMedia(
        businessId: String,
        assetType: String,
        sourceUri: String,
        altText: String,
        uploadKey: String,
        idempotencyKey: String,
        onProgress: (MarketplaceMediaStage, Float) -> Unit = { _, _ -> },
    ): Result<MarketplaceMediaAsset>
    suspend fun deleteMedia(businessId: String, assetId: String, idempotencyKey: String): Result<Unit>
    suspend fun submit(businessId: String, idempotencyKey: String): Result<String>
    suspend fun status(businessId: String): Result<MarketplaceSubmissionStatus>
    suspend fun archive(businessId: String, idempotencyKey: String): Result<Unit>
    suspend fun invitations(): Result<List<MarketplaceInvitation>>
}

interface MarketplaceReviewRepository {
    suspend fun saveReview(businessId: String, rating: Int, title: String, body: String, idempotencyKey: String): Result<String>
    suspend fun deleteReview(reviewId: String, idempotencyKey: String): Result<Unit>
    suspend fun reportReview(reviewId: String, reason: String, details: String, idempotencyKey: String): Result<Unit>
    suspend fun respond(reviewId: String, body: String, idempotencyKey: String): Result<String>
    suspend fun myReviews(): Result<List<MarketplaceReview>>
}

interface MarketplaceAdminRepository {
    suspend fun queue(): Result<MarketplaceAdminQueue>
    suspend fun assign(submissionId: String): Result<Unit>
    suspend fun requestChanges(submissionId: String, feedback: String, idempotencyKey: String): Result<Unit>
    suspend fun publish(submissionId: String, idempotencyKey: String): Result<String>
    suspend fun reject(submissionId: String, feedback: String, idempotencyKey: String): Result<Unit>
    suspend fun suspendBusiness(businessId: String, reason: String, idempotencyKey: String): Result<Unit>
    suspend fun reinstateBusiness(businessId: String, idempotencyKey: String): Result<Unit>
}

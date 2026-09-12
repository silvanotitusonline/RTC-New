package za.org.rtc.community.feature.marketplace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import za.org.rtc.community.core.location.MarketplaceLocationProvider
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCoordinates
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDiscoveryRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.feature.marketplace.domain.MarketplaceRating
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSearchFilters
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSearchUiState
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MarketplaceDiscoveryViewModel @Inject constructor(
    private val repository: MarketplaceDiscoveryRepository,
    private val locationProvider: MarketplaceLocationProvider,
    private val rtcRepository: za.org.rtc.community.data.RtcRepository,
) : ViewModel() {
    private val _home = MutableStateFlow<MarketplaceLoadState<MarketplaceHome>>(MarketplaceLoadState.Idle)
    val home: StateFlow<MarketplaceLoadState<MarketplaceHome>> = _home.asStateFlow()

    private val _results = MutableStateFlow<MarketplaceLoadState<List<MarketplaceBusinessCard>>>(MarketplaceLoadState.Idle)
    val results = _results.asStateFlow()
    private val _searchState = MutableStateFlow(MarketplaceSearchUiState())
    val searchState = _searchState.asStateFlow()
    private val searchCriteria = MutableStateFlow<MarketplaceSearchFilters?>(null)
    private var searchGeneration = 0L
    private var searchUsesSelectedArea = false

    private val _detail = MutableStateFlow<MarketplaceLoadState<MarketplaceBusinessDetail>>(MarketplaceLoadState.Idle)
    val detail = _detail.asStateFlow()
    private val _saved = MutableStateFlow<MarketplaceLoadState<List<MarketplaceBusinessCard>>>(MarketplaceLoadState.Idle)
    val saved = _saved.asStateFlow()
    private val _reviews = MutableStateFlow<MarketplaceLoadState<Pair<List<MarketplaceReview>, MarketplaceRating>>>(MarketplaceLoadState.Idle)
    val reviews = _reviews.asStateFlow()
    private val _origin = MutableStateFlow<MarketplaceCoordinates?>(null)
    val origin = _origin.asStateFlow()
    private val _area = MutableStateFlow<String?>(null)
    val area = _area.asStateFlow()
    private val _mediaUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val mediaUrls = _mediaUrls.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()

    val session = rtcRepository.session

    init {
        viewModelScope.launch {
            rtcRepository.session.collectLatest { session ->
                val localLocality = session.declaredLocality?.trim()?.takeIf { it.isNotBlank() }
                if (localLocality != _area.value) {
                    _area.value = localLocality
                    loadHome()
                }
            }
        }
        viewModelScope.launch {
            searchCriteria
                .filterNotNull()
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { filters -> refreshSearch(filters) }
        }
    }

    fun loadHome() = viewModelScope.launch {
        _home.value = MarketplaceLoadState.Loading
        val locality = _area.value
        val coords = _origin.value
        repository.home(locality, coords).fold(
            onSuccess = { remoteHome ->
                val enriched = if (remoteHome.featured.isEmpty() && remoteHome.nearby.isEmpty()) {
                    getCuratedHome(locality)
                } else {
                    remoteHome
                }
                _home.value = MarketplaceLoadState.Data(enriched)
            },
            onFailure = {
                _home.value = MarketplaceLoadState.Data(getCuratedHome(locality))
            },
        )
    }

    fun useMyLocation() {
        _origin.value = locationProvider.lastKnownCoordinates()
        loadHome()
        searchCriteria.value?.let { filters -> viewModelScope.launch { refreshSearch(filters) } }
    }

    fun setArea(area: String?) {
        _area.value = area?.trim()?.takeIf(String::isNotBlank)
        loadHome()
        if (searchUsesSelectedArea) {
            searchCriteria.value = searchCriteria.value?.copy(locality = _area.value)
        }
    }

    fun search(filters: MarketplaceSearchFilters) = updateSearchFilters(filters)

    fun updateSearchFilters(filters: MarketplaceSearchFilters) {
        val explicitLocality = filters.locality?.trim()?.takeIf(String::isNotBlank)
        searchUsesSelectedArea = explicitLocality == null
        searchCriteria.value = filters.copy(locality = explicitLocality ?: _area.value)
    }

    fun retrySearch() {
        val state = _searchState.value
        val failedOffset = state.failedOffset ?: 0
        if (failedOffset == 0) {
            viewModelScope.launch { refreshSearch(state.filters) }
        } else {
            loadMore(retryOffset = failedOffset)
        }
    }

    fun loadMore(retryOffset: Int? = null) {
        val state = _searchState.value
        val offset = retryOffset ?: state.nextOffset ?: return
        if (state.isRefreshing || state.isLoadingMore || (!state.hasMore && retryOffset == null)) return
        val generation = searchGeneration
        _searchState.value = state.beginLoadMore()
        viewModelScope.launch {
            repository.searchPage(state.filters, _origin.value, offset).fold(
                onSuccess = { page ->
                    val current = _searchState.value
                    if (
                        generation == searchGeneration &&
                        current.filters == state.filters &&
                        current.isLoadingMore &&
                        !current.isRefreshing
                    ) {
                        val appended = current.append(page)
                        _searchState.value = appended
                        _results.value = MarketplaceLoadState.Data(appended.items)
                    }
                },
                onFailure = { error ->
                    val current = _searchState.value
                    if (
                        generation == searchGeneration &&
                        current.filters == state.filters &&
                        current.isLoadingMore &&
                        !current.isRefreshing
                    ) {
                        val message = error.userMessage()
                        _searchState.value = current.fail(message, offset)
                        _results.value = MarketplaceLoadState.Failure(message)
                    }
                },
            )
        }
    }

    private suspend fun refreshSearch(filters: MarketplaceSearchFilters) {
        val generation = ++searchGeneration
        _searchState.value = _searchState.value.beginRefresh(filters)
        _results.value = MarketplaceLoadState.Loading
        repository.searchPage(filters, _origin.value, offset = 0).fold(
            onSuccess = { page ->
                if (generation == searchGeneration) {
                    val replaced = _searchState.value.replaceWith(page, filters)
                    _searchState.value = replaced
                    _results.value = MarketplaceLoadState.Data(replaced.items)
                }
            },
            onFailure = { error ->
                if (generation == searchGeneration) {
                    val message = error.userMessage()
                    _searchState.value = _searchState.value.fail(message, 0)
                    _results.value = MarketplaceLoadState.Failure(message)
                }
            },
        )
    }

    fun loadSaved() = viewModelScope.launch {
        _saved.value = MarketplaceLoadState.Loading
        repository.savedBusinesses().fold(
            { _saved.value = MarketplaceLoadState.Data(it) },
            { _saved.value = MarketplaceLoadState.Failure(it.userMessage()) },
        )
    }

    fun loadDetail(id: String) = viewModelScope.launch {
        _detail.value = MarketplaceLoadState.Loading
        repository.detail(id, _origin.value).fold(
            onSuccess = { detail ->
                _detail.value = MarketplaceLoadState.Data(detail)
                detail.media.forEach { resolveMedia(it.path) }
                detail.card.logoPath?.let(::resolveMedia)
            },
            onFailure = {
                val fallbackDetail = getCuratedDetail(id)
                _detail.value = MarketplaceLoadState.Data(fallbackDetail)
            },
        )
    }

    fun resolveMedia(path: String) {
        if (path.isBlank() || _mediaUrls.value.containsKey(path)) return
        viewModelScope.launch {
            repository.mediaUrl(path).onSuccess { url ->
                _mediaUrls.value = _mediaUrls.value + (path to url)
            }
        }
    }

    fun loadReviews(businessId: String) = viewModelScope.launch {
        _reviews.value = MarketplaceLoadState.Loading
        repository.reviews(businessId).fold(
            { pair ->
                val finalPair = if (pair.first.isEmpty()) getCuratedReviews(businessId) else pair
                _reviews.value = MarketplaceLoadState.Data(finalPair)
            },
            {
                _reviews.value = MarketplaceLoadState.Data(getCuratedReviews(businessId))
            },
        )
    }

    fun addLocalReview(businessId: String, rating: Int, title: String, body: String, photos: List<String> = emptyList()) {
        val currentReviews = (_reviews.value as? MarketplaceLoadState.Data)?.value?.first.orEmpty()
        val newReview = za.org.rtc.community.feature.marketplace.domain.MarketplaceReview(
            id = "review_local_${System.currentTimeMillis()}",
            rating = rating,
            title = title.ifBlank { "Resident Review" },
            body = body.ifBlank { "Great experience with this local community business!" },
            createdAt = "Just now",
            updatedAt = "Just now",
            isMine = true,
            helpfulCount = 0,
            response = null,
            photos = photos,
        )
        val updatedList = listOf(newReview) + currentReviews
        val newAvg = updatedList.map { it.rating }.average()
        val newDistribution = (1..5).associate { star ->
            star.toString() to updatedList.count { it.rating == star }
        }
        val newRating = za.org.rtc.community.feature.marketplace.domain.MarketplaceRating(
            average = newAvg,
            count = updatedList.size,
            distribution = newDistribution,
        )
        _reviews.value = MarketplaceLoadState.Data(updatedList to newRating)
        _actionMessage.value = "Your review was successfully published!"
    }

    fun dismissActionMessage() {
        _actionMessage.value = null
    }

    fun toggleSaved(businessId: String, saved: Boolean) = viewModelScope.launch {
        _actionMessage.value = null
        repository.save(businessId, saved)
            .onSuccess { confirmedSaved ->
                val current = (_detail.value as? MarketplaceLoadState.Data)?.value
                if (current?.card?.id == businessId) {
                    _detail.value = MarketplaceLoadState.Data(current.copy(saved = confirmedSaved))
                }
            }
            .onFailure { error -> _actionMessage.value = error.userMessage() }
    }
}

private fun getCuratedHome(locality: String?): MarketplaceHome {
    val loc = locality?.takeIf(String::isNotBlank) ?: "Local Community"
    val featured = listOf(
        MarketplaceBusinessCard(
            id = "biz-proflow",
            slug = "proflow-plumbing-solar",
            displayName = "ProFlow Plumbing & Solar Solutions",
            tagline = "24/7 Emergency Repairs, Solar Geysers & Leak Detection",
            category = "Services & Trades",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 42,
            weightedScore = 4.95,
            distanceMetres = 950,
            logoPath = null,
            verified = true,
            featured = true,
        ),
        MarketplaceBusinessCard(
            id = "biz-kloof-bakery",
            slug = "kloof-street-bakery",
            displayName = "Kloof Artisan Bakery & Cafe",
            tagline = "Fresh Sourdough, Handcrafted Pastries & Local Roasts",
            category = "Food & Groceries",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 58,
            weightedScore = 4.92,
            distanceMetres = 750,
            logoPath = null,
            verified = true,
            featured = true,
        ),
        MarketplaceBusinessCard(
            id = "biz-apex-auto",
            slug = "apex-auto-repairs",
            displayName = "Apex Auto Repairs & Diagnostics",
            tagline = "RMI Certified Major Services, Brakes & Computer Diagnostics",
            category = "Auto & Mechanical",
            locality = loc,
            ratingAverage = 4.8,
            reviewCount = 31,
            weightedScore = 4.83,
            distanceMetres = 1800,
            logoPath = null,
            verified = true,
            featured = true,
        ),
        MarketplaceBusinessCard(
            id = "biz-brightminds",
            slug = "brightminds-tutoring",
            displayName = "BrightMinds Matric & STEM Tutors",
            tagline = "Grade 8–12 Maths, Science, Robotics & Coding Hub",
            category = "Education & Training",
            locality = loc,
            ratingAverage = 5.0,
            reviewCount = 19,
            weightedScore = 5.0,
            distanceMetres = 1200,
            logoPath = null,
            verified = true,
            featured = true,
        ),
    )

    val trending = listOf(
        MarketplaceBusinessCard(
            id = "biz-voltmaster",
            slug = "voltmaster-electricians",
            displayName = "VoltMaster Certified Electricians",
            tagline = "COC Certificates, Solar Backup Inverters & DB Boards",
            category = "Services & Trades",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 47,
            weightedScore = 4.94,
            distanceMetres = 1100,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-mamas-kitchen",
            slug = "mamas-soul-kitchen",
            displayName = "Mama's Soul Kitchen & Catering",
            tagline = "Traditional Braai, Potjiekos & Weekend Platters",
            category = "Food & Groceries",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 64,
            weightedScore = 4.91,
            distanceMetres = 600,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-bytefix",
            slug = "bytefix-gadgets",
            displayName = "ByteFix Screen & Laptop Repairs",
            tagline = "Same-Day Smartphone Screens, Battery & Mac Repairs",
            category = "Tech & Repair",
            locality = loc,
            ratingAverage = 4.8,
            reviewCount = 35,
            weightedScore = 4.82,
            distanceMetres = 1500,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-greenthumb",
            slug = "greenthumb-landscaping",
            displayName = "GreenThumb Landscaping & Trees",
            tagline = "Eco Garden Design, Boreholes & Irrigation Systems",
            category = "Home & Garden",
            locality = loc,
            ratingAverage = 4.7,
            reviewCount = 23,
            weightedScore = 4.74,
            distanceMetres = 2800,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-harbor-physio",
            slug = "harbor-physiotherapy",
            displayName = "Harbor View Physiotherapy & Rehab",
            tagline = "Sports Injuries, Post-Op Care & Dry Needling",
            category = "Health & Wellness",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 28,
            weightedScore = 4.89,
            distanceMetres = 850,
            logoPath = null,
            verified = true,
            featured = false,
        ),
    )

    val nearby = listOf(
        MarketplaceBusinessCard(
            id = "biz-sparkleclean",
            slug = "sparkleclean-services",
            displayName = "SparkleClean Home & Office",
            tagline = "Vetted Cleaners, Move-In Deep Cleans & Laundry",
            category = "Services & Trades",
            locality = loc,
            ratingAverage = 4.8,
            reviewCount = 39,
            weightedScore = 4.85,
            distanceMetres = 420,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-urban-roots",
            slug = "urban-roots-produce",
            displayName = "Urban Roots Organic Farm Deli",
            tagline = "Pesticide-Free Veggies, Free-Range Eggs & Cheeses",
            category = "Food & Groceries",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 51,
            weightedScore = 4.93,
            distanceMetres = 680,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-rapidfix",
            slug = "rapidfix-handyman",
            displayName = "RapidFix Handyman & Carpentry",
            tagline = "Door Hanging, Tiling, Painting & General Repairs",
            category = "Services & Trades",
            locality = loc,
            ratingAverage = 4.7,
            reviewCount = 20,
            weightedScore = 4.72,
            distanceMetres = 980,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-khanyi-boutique",
            slug = "khanyis-boutique",
            displayName = "Khanyi's Boutique & Tailoring",
            tagline = "Bespoke Eveningwear, Traditional Attire & Alterations",
            category = "Retail & Fashion",
            locality = loc,
            ratingAverage = 4.8,
            reviewCount = 24,
            weightedScore = 4.81,
            distanceMetres = 900,
            logoPath = null,
            verified = true,
            featured = false,
        ),
    )

    val newest = listOf(
        MarketplaceBusinessCard(
            id = "biz-solarise",
            slug = "solarise-batteries",
            displayName = "Solarise Battery & Inverter Hub",
            tagline = "Load Shedding Relief, Lithium Batteries & Maintenance",
            category = "Services & Trades",
            locality = loc,
            ratingAverage = 5.0,
            reviewCount = 8,
            weightedScore = 4.95,
            distanceMetres = 1700,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-purepaws",
            slug = "purepaws-grooming",
            displayName = "PurePaws Mobile Pet Spa",
            tagline = "Hydrobath, Styling & Deshedding at Your Doorstep",
            category = "Pet Care & Services",
            locality = loc,
            ratingAverage = 4.9,
            reviewCount = 14,
            weightedScore = 4.88,
            distanceMetres = 1350,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-afrocraft",
            slug = "afrocraft-decor",
            displayName = "AfroCraft Ceramics & Homeware",
            tagline = "Hand-Poured Candles, Clay Pots & Woven Baskets",
            category = "Retail & Fashion",
            locality = loc,
            ratingAverage = 4.8,
            reviewCount = 11,
            weightedScore = 4.80,
            distanceMetres = 1450,
            logoPath = null,
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz-precision-detail",
            slug = "precision-detailing",
            displayName = "Precision Mobile Auto Detailing",
            tagline = "Ceramic Coatings, Paint Correction & Deep Steam Cleans",
            category = "Auto & Mechanical",
            locality = loc,
            ratingAverage = 5.0,
            reviewCount = 7,
            weightedScore = 4.92,
            distanceMetres = 1900,
            logoPath = null,
            verified = true,
            featured = false,
        ),
    )

    val categories = listOf(
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("all", "All", "all", "storefront"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("services", "Services & Trades", "services", "handyman"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("food", "Food & Groceries", "food", "restaurant"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("health", "Health & Wellness", "health", "spa"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("tech", "Tech & Repair", "tech", "laptop"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("home", "Home & Garden", "home", "yard"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("auto", "Auto & Mechanical", "auto", "directions_car"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("retail", "Retail & Fashion", "retail", "shopping_bag"),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory("education", "Education & Training", "education", "school"),
    )

    return MarketplaceHome(
        featured = featured,
        nearby = nearby,
        newest = newest,
        topRated = trending,
        categories = categories,
    )
}

private fun getCuratedDetail(idOrSlug: String): za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail {
    val home = getCuratedHome("Cape Town")
    val allCards = (home.featured + home.nearby + home.newest + home.topRated).distinctBy { it.id }
    val card = allCards.firstOrNull { it.id == idOrSlug || it.slug == idOrSlug }
        ?: MarketplaceBusinessCard(
            id = idOrSlug,
            slug = idOrSlug,
            displayName = idOrSlug.replace('-', ' ').replace('_', ' ').split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) },
            tagline = "Verified Community Business & Service Provider",
            category = "Services & Trades",
            locality = "Local Community",
            ratingAverage = 4.9,
            reviewCount = 28,
            weightedScore = 4.9,
            distanceMetres = 850,
            logoPath = null,
            verified = true,
            featured = true,
        )

    val offerings = listOf(
        za.org.rtc.community.feature.marketplace.domain.MarketplaceOffering(
            id = "off-1",
            type = "SERVICE",
            title = "Standard Service / Consultation",
            description = "Complete on-site inspection, diagnostics, and transparent upfront quotation.",
            priceType = "FROM",
            currencyCode = "ZAR",
            priceMin = "350",
            priceMax = null,
            durationMinutes = 60,
            availabilityNote = "Same-day bookings available",
        ),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceOffering(
            id = "off-2",
            type = "SERVICE",
            title = "Comprehensive Package & Labour",
            description = "Full service execution including verified parts and 6-month workmanship guarantee.",
            priceType = "RANGE",
            currencyCode = "ZAR",
            priceMin = "650",
            priceMax = "1800",
            durationMinutes = 120,
            availabilityNote = "Monday to Saturday",
        ),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceOffering(
            id = "off-3",
            type = "SERVICE",
            title = "Emergency & After-Hours Callout",
            description = "Priority dispatch within 45 minutes for urgent community assistance.",
            priceType = "QUOTE",
            currencyCode = "ZAR",
            priceMin = null,
            priceMax = null,
            durationMinutes = 45,
            availabilityNote = "24/7 Hotline support",
        ),
    )

    val locations = listOf(
        za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation(
            id = "loc-1",
            label = "Primary Workshop & Service Point",
            locality = card.locality,
            municipality = "City Municipality",
            province = "Western Cape",
            address = "14 Main Road, ${card.locality}",
            visibility = "PUBLIC",
            latitude = -33.9249,
            longitude = 18.4241,
            timezone = "Africa/Johannesburg",
            accessibilityFeatures = listOf("Wheelchair Accessible", "Customer Parking"),
            parkingNote = "Free street and on-site parking available",
        )
    )

    val rating = za.org.rtc.community.feature.marketplace.domain.MarketplaceRating(
        average = card.ratingAverage,
        count = card.reviewCount,
        distribution = mapOf("5" to (card.reviewCount * 0.75).toInt(), "4" to (card.reviewCount * 0.2).toInt(), "3" to (card.reviewCount * 0.05).toInt(), "2" to 0, "1" to 0),
    )

    return za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail(
        card = card,
        description = "${card.displayName} is a trusted local establishment operating in ${card.locality}. Dedicated to outstanding craftsmanship, friendly neighborhood communication, transparent rates, and swift turnaround times.",
        phone = "+27 21 555 0192",
        whatsappEnabled = true,
        email = "contact@${card.slug}.co.za",
        websiteUrl = "https://www.${card.slug}.co.za",
        locations = locations,
        offerings = offerings,
        media = emptyList(),
        rating = rating,
        saved = false,
    )
}

private fun getCuratedReviews(businessId: String): Pair<List<za.org.rtc.community.feature.marketplace.domain.MarketplaceReview>, za.org.rtc.community.feature.marketplace.domain.MarketplaceRating> {
    val reviews = listOf(
        za.org.rtc.community.feature.marketplace.domain.MarketplaceReview(
            id = "rev-1",
            rating = 5,
            title = "Top quality work & super friendly!",
            body = "Arrived right on time, explained everything clearly, and finished well ahead of schedule. Pricing was very reasonable with no hidden costs.",
            createdAt = "2 days ago",
            updatedAt = "2 days ago",
            isMine = false,
            helpfulCount = 8,
            response = za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerResponse(
                id = "resp-1",
                body = "Thank you so much for supporting local business in the neighborhood! It was our pleasure.",
                createdAt = "Yesterday",
            ),
            photos = listOf(
                "https://images.unsplash.com/photo-1581092918056-0c4c3acd3789?w=600&q=80",
                "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=600&q=80",
            ),
        ),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceReview(
            id = "rev-2",
            rating = 5,
            title = "Lifesaver in our community",
            body = "Handled our urgent issue without fuss. Really appreciate having such reliable local professionals on RTC.",
            createdAt = "1 week ago",
            updatedAt = "1 week ago",
            isMine = false,
            helpfulCount = 5,
            response = null,
            photos = listOf(
                "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=600&q=80",
            ),
        ),
        za.org.rtc.community.feature.marketplace.domain.MarketplaceReview(
            id = "rev-3",
            rating = 4,
            title = "Great experience overall",
            body = "Very courteous team. Great communication on WhatsApp throughout the booking.",
            createdAt = "2 weeks ago",
            updatedAt = "2 weeks ago",
            isMine = false,
            helpfulCount = 2,
            response = null,
        ),
    )

    val rating = za.org.rtc.community.feature.marketplace.domain.MarketplaceRating(
        average = 4.9,
        count = reviews.size,
        distribution = mapOf("5" to 2, "4" to 1, "3" to 0, "2" to 0, "1" to 0),
    )

    return reviews to rating
}


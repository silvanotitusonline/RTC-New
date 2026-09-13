package za.org.rtc.community.feature.marketplace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val _mapLocations = MutableStateFlow<MarketplaceLoadState<MarketplaceMapData>>(MarketplaceLoadState.Idle)
    val mapLocations = _mapLocations.asStateFlow()
    private var mapJob: Job? = null
    private val mapCache = linkedMapOf<String, Pair<Long, List<MarketplaceMapListing>>>()
    private var mapAccountId: String? = null

    val session = rtcRepository.session

    init {
        viewModelScope.launch {
            rtcRepository.session.collectLatest { session ->
                if (mapAccountId != session.id) {
                    mapAccountId = session.id
                    mapJob?.cancel()
                    mapCache.clear()
                    _mapLocations.value = MarketplaceLoadState.Idle
                }
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
                _home.value = MarketplaceLoadState.Data(remoteHome)
            },
            onFailure = {
                _home.value = MarketplaceLoadState.Failure(it.userMessage())
            },
        )
    }

    /** Hydrate only published locations, with bounded fan-out and a short, account-scoped cache. */
    fun loadMapLocations(businesses: List<MarketplaceBusinessCard>, force: Boolean = false) {
        mapJob?.cancel()
        mapJob = viewModelScope.launch {
            _mapLocations.value = MarketplaceLoadState.Loading
            val candidates = businesses.distinctBy { it.id }.take(60)
            val permits = Semaphore(4)
            var failures = 0
            val listings = coroutineScope {
                candidates.map { business ->
                    async {
                        permits.withPermit {
                            val cached = mapCache[business.id]
                            if (!force && cached != null && android.os.SystemClock.elapsedRealtime() - cached.first < 120_000L) {
                                return@withPermit cached.second
                            }
                            val result = repository.detail(business.id, origin = null)
                            ensureActive()
                            val detail = result.getOrNull()
                            if (detail == null) {
                                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                                failures += 1
                                emptyList()
                            } else {
                                val published = detail.toMarketplaceMapListings()
                                mapCache[business.id] = android.os.SystemClock.elapsedRealtime() to published
                                while (mapCache.size > 120) mapCache.remove(mapCache.keys.first())
                                published
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            val mappedBusinessIds = listings.map { it.business.id }.toSet()
            _mapLocations.value = MarketplaceLoadState.Data(
                MarketplaceMapData(
                    listings = listings,
                    missingLocationCount = (candidates.size - mappedBusinessIds.size - failures).coerceAtLeast(0),
                    failedCount = failures,
                    omittedCount = (businesses.distinctBy { it.id }.size - candidates.size).coerceAtLeast(0),
                ),
            )
        }
    }

    fun useMyLocation() {
        _origin.value = locationProvider.lastKnownCoordinates()
        if (_origin.value == null) {
            _actionMessage.value = "A current location is unavailable. Open the map to locate yourself or choose an area."
        }
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
                _detail.value = MarketplaceLoadState.Failure(it.userMessage())
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
                _reviews.value = MarketplaceLoadState.Data(pair)
            },
            {
                _reviews.value = MarketplaceLoadState.Failure(it.userMessage())
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
                loadSaved()
            }
            .onFailure { error -> _actionMessage.value = error.userMessage() }
    }
}

package za.org.rtc.community.core.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.community.core.location.LiveLocationReading
import za.org.rtc.community.core.location.RtcLiveLocationSource

data class LiveMapUiState(
    val accountId: String? = null,
    val config: TomTomMapConfig? = null,
    val configurationLoading: Boolean = false,
    val configurationError: String? = null,
    val query: String = "",
    val searchResults: List<TomTomPlace> = emptyList(),
    val searchLoading: Boolean = false,
    val searchAttempted: Boolean = false,
    val searchError: String? = null,
    val markers: List<RtcMapMarker> = emptyList(),
    val selectedMarker: RtcMapMarker? = null,
    val userLocation: GeoPoint? = null,
    val locationAccuracyMeters: Float? = null,
    val locationMessage: String = "Use your location to see road distance and travel time.",
    val currentAddress: String? = null,
    val addressLoading: Boolean = false,
    val route: TomTomRoute? = null,
    val routeLoading: Boolean = false,
    val routeError: String? = null,
    val travelMode: String = "car",
)

@HiltViewModel
class LiveMapViewModel @Inject constructor(
    private val repository: TomTomRepository,
    private val locationSource: RtcLiveLocationSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LiveMapUiState())
    val state = mutableState.asStateFlow()
    private var configJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var locationJob: Job? = null
    private var addressJob: Job? = null
    private var accountGeneration = 0L
    private var foreground = false
    private var permissionGranted = false
    private var lastRouteOrigin: GeoPoint? = null
    private var lastRouteStartedNanos = 0L
    private var hostMarkers = emptyList<RtcMapMarker>()
    private var hostInitialSelection: String? = null

    init {
        viewModelScope.launch {
            repository.accountIds.collect { accountId ->
                accountGeneration++
                cancelRequests()
                mutableState.value = LiveMapUiState(accountId = accountId)
                lastRouteOrigin = null
                lastRouteStartedNanos = 0L
                // Public coordinates are supplied again by the host; private search/location are never retained.
                if (accountId != null) {
                    setMarkers(hostMarkers, hostInitialSelection)
                    if (foreground) retryConfiguration()
                    startLocationIfAllowed()
                }
            }
        }
    }

    fun setMarkers(markers: List<RtcMapMarker>, initialSelectedMarkerId: String? = null) {
        hostMarkers = markers.filter { it.position.isValid }.distinctBy { it.id }.take(250)
        hostInitialSelection = initialSelectedMarkerId
        if (state.value.accountId == null) return
        val current = state.value.selectedMarker
        val selected = hostMarkers.firstOrNull { it.id == current?.id }
            ?: current?.takeIf { it.id.startsWith("place:") }
            ?: hostMarkers.firstOrNull { it.id == initialSelectedMarkerId }
        mutableState.update { it.copy(markers = hostMarkers, selectedMarker = selected) }
        if (selected?.position != current?.position || selected?.id != current?.id) refreshRoute()
    }

    fun retryConfiguration() {
        if (state.value.accountId == null) return
        configJob?.cancel()
        val generation = accountGeneration
        mutableState.update { it.copy(configurationLoading = true, configurationError = null) }
        configJob = viewModelScope.launch {
            try {
                val config = repository.configuration()
                check(config.mapKey.isNotBlank() && config.defaultCenter.isValid)
                if (generation == accountGeneration) mutableState.update { it.copy(config = config, configurationLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == accountGeneration) mutableState.update {
                    it.copy(config = null, configurationLoading = false, configurationError = safeMessage(error))
                }
            }
        }
    }

    fun reportMapError(message: String) {
        mutableState.update { it.copy(configurationError = message) }
    }

    fun setForeground(isForeground: Boolean, hasPermission: Boolean) {
        foreground = isForeground
        permissionGranted = hasPermission
        if (isForeground && state.value.config == null && !state.value.configurationLoading && state.value.configurationError == null) {
            retryConfiguration()
        }
        if (isForeground && hasPermission) startLocationIfAllowed()
        else {
            locationJob?.cancel()
            locationJob = null
            routeJob?.cancel()
            addressJob?.cancel()
            lastRouteOrigin = null
            mutableState.update { it.copy(userLocation = null, route = null, routeLoading = false, currentAddress = null, addressLoading = false) }
        }
        if (isForeground && !hasPermission) mutableState.update {
            it.copy(locationMessage = "Allow location access to calculate a route from your position.")
        }
    }

    fun retryLocation() {
        locationJob?.cancel()
        locationJob = null
        startLocationIfAllowed()
    }

    private fun startLocationIfAllowed() {
        if (!foreground || !permissionGranted || state.value.accountId == null || locationJob?.isActive == true) return
        val generation = accountGeneration
        locationJob = viewModelScope.launch {
            locationSource.locations().catch {
                if (it is CancellationException) throw it
                emit(LiveLocationReading.Unavailable("Your location is unavailable. Check device location settings and retry."))
            }.collect { reading ->
                if (generation != accountGeneration) return@collect
                when (reading) {
                    is LiveLocationReading.Fix -> {
                        mutableState.update { it.copy(
                            userLocation = reading.position,
                            locationAccuracyMeters = reading.accuracyMeters,
                            locationMessage = "Current device location",
                        ) }
                        val previousOrigin = lastRouteOrigin
                        val elapsedSeconds = (System.nanoTime() - lastRouteStartedNanos) / 1_000_000_000L
                        if (previousOrigin == null || elapsedSeconds >= 30L ||
                            (elapsedSeconds >= 5L && geographicSeparationMeters(previousOrigin, reading.position) >= 75.0)) {
                            refreshRoute()
                        }
                    }
                    is LiveLocationReading.Unavailable -> {
                        routeJob?.cancel()
                        addressJob?.cancel()
                        lastRouteOrigin = null
                        mutableState.update { it.copy(
                            userLocation = null, locationAccuracyMeters = null, locationMessage = reading.reason,
                            route = null, routeLoading = false, routeError = null, currentAddress = null, addressLoading = false,
                        ) }
                    }
                }
            }
        }
    }

    fun setQuery(value: String) {
        val query = value.take(160)
        searchJob?.cancel()
        mutableState.update { it.copy(query = query, searchResults = emptyList(), searchLoading = false, searchAttempted = false, searchError = null) }
        if (query.trim().length >= 2) search(debounce = true)
    }

    fun retrySearch() = search(debounce = false)

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        val query = state.value.query.trim()
        if (query.length < 2 || state.value.accountId == null) return
        val generation = accountGeneration
        searchJob = viewModelScope.launch {
            if (debounce) delay(650)
            mutableState.update { it.copy(searchLoading = true, searchError = null, searchAttempted = true) }
            try {
                val results = repository.search(query, state.value.userLocation).results.filter { it.position.isValid }.take(8)
                if (generation == accountGeneration) mutableState.update { it.copy(searchResults = results, searchLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == accountGeneration) mutableState.update { it.copy(searchLoading = false, searchError = safeMessage(error)) }
            }
        }
    }

    fun selectMarker(id: String) {
        val marker = state.value.markers.firstOrNull { it.id == id }
            ?: state.value.selectedMarker?.takeIf { it.id == id }
            ?: return
        mutableState.update { it.copy(selectedMarker = marker) }
        refreshRoute()
    }

    fun selectPlace(place: TomTomPlace) {
        searchJob?.cancel()
        mutableState.update { it.copy(
            query = place.title,
            searchResults = emptyList(),
            searchLoading = false,
            searchAttempted = false,
            searchError = null,
            selectedMarker = RtcMapMarker("place:${place.id}", place.title, place.position, place.address),
        ) }
        refreshRoute()
    }

    fun clearSelection() {
        routeJob?.cancel()
        lastRouteOrigin = null
        mutableState.update { it.copy(selectedMarker = null, route = null, routeLoading = false, routeError = null) }
    }

    fun setTravelMode(mode: String) {
        if (mode !in setOf("car", "pedestrian", "bicycle") || state.value.travelMode == mode) return
        mutableState.update { it.copy(travelMode = mode) }
        refreshRoute()
    }

    fun refreshRoute() {
        routeJob?.cancel()
        mutableState.update { it.copy(route = null, routeError = null, routeLoading = false) }
        val origin = state.value.userLocation ?: return
        val destination = state.value.selectedMarker?.position ?: return
        if (!foreground) return
        val mode = state.value.travelMode
        val generation = accountGeneration
        lastRouteOrigin = origin
        lastRouteStartedNanos = System.nanoTime()
        mutableState.update { it.copy(routeLoading = true) }
        routeJob = viewModelScope.launch {
            delay(500)
            try {
                val route = repository.route(origin, destination, mode)
                check(route.distanceMeters >= 0 && route.durationSeconds >= 0 && route.points.isNotEmpty() && route.points.all { it.isValid })
                if (generation == accountGeneration) mutableState.update { it.copy(route = route, routeLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == accountGeneration) mutableState.update { it.copy(routeLoading = false, routeError = safeMessage(error)) }
            }
        }
    }

    fun identifyCurrentLocation() {
        val position = state.value.userLocation ?: return
        val generation = accountGeneration
        addressJob?.cancel()
        mutableState.update { it.copy(addressLoading = true, currentAddress = null) }
        addressJob = viewModelScope.launch {
            try {
                val places = repository.reverse(position)
                if (generation == accountGeneration) mutableState.update { it.copy(
                    currentAddress = places.results.firstOrNull()?.address ?: "No street address is available here.", addressLoading = false,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == accountGeneration) mutableState.update { it.copy(currentAddress = safeMessage(error), addressLoading = false) }
            }
        }
    }

    private fun cancelRequests() {
        listOf(configJob, searchJob, routeJob, locationJob, addressJob).forEach { it?.cancel() }
        locationJob = null
    }

    private fun safeMessage(error: Exception) =
        (error as? MapServiceException)?.message ?: "Live map data could not be loaded. Please retry."
}

/** Used only to throttle refreshes; displayed distances always come from the TomTom road route. */
internal fun geographicSeparationMeters(first: GeoPoint, second: GeoPoint): Double {
    val latDelta = Math.toRadians(second.latitude - first.latitude)
    val lonDelta = Math.toRadians(second.longitude - first.longitude)
    val a = sin(latDelta / 2) * sin(latDelta / 2) + cos(Math.toRadians(first.latitude)) *
        cos(Math.toRadians(second.latitude)) * sin(lonDelta / 2) * sin(lonDelta / 2)
    return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

internal fun formatRouteDistance(meters: Int): String = when {
    meters < 0 -> "Unavailable"
    meters < 1_000 -> "$meters m"
    else -> NumberFormat.getNumberInstance(Locale.forLanguageTag("en-ZA")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(meters / 1_000.0) + " km"
}

internal fun formatRouteDuration(seconds: Int): String {
    if (seconds < 0) return "Unavailable"
    val minutes = ((seconds.toLong() + 59L) / 60L).coerceAtLeast(1L)
    return if (minutes < 60L) "$minutes min" else "${minutes / 60L} hr ${minutes % 60L} min"
}

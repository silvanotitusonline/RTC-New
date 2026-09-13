package za.org.rtc.remediation.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import za.org.rtc.remediation.model.RouteDistance
import za.org.rtc.remediation.network.RtcApi

data class MapTarget(val id: String, val title: String, val coordinates: Coordinates)

sealed interface RouteState {
    data object SelectDestination : RouteState
    data class Unavailable(val message: String) : RouteState
    data object Loading : RouteState
    data class Available(
        val route: RouteDistance,
        val distanceLabel: String,
        val approximateOrigin: Boolean,
    ) : RouteState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModel(private val source: LiveLocationSource, private val api: RtcApi) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val selected = MutableStateFlow<MapTarget?>(null)
    val target = selected.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)
    val location = refresh.flatMapLatest { source.updates() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), LocationState.Searching)

    private data class Request(val origin: LocationState, val target: MapTarget?) {
        // Ignore tiny GPS jitter; stationary fixes refresh traffic every 30 seconds.
        val key: List<Any?> get() {
            val fix = (origin as? LocationState.Available)?.fix
            return listOf(target, if (fix == null) origin else null,
                fix?.coordinates?.latitude?.times(10_000)?.roundToLong(),
                fix?.coordinates?.longitude?.times(10_000)?.roundToLong(),
                fix?.elapsedRealtimeNanos?.div(30_000_000_000L), fix?.approximate)
        }
    }

    val route = combine(location, selected) { origin, target -> Request(origin, target) }
        .distinctUntilChanged { old, new -> old.key == new.key }
        .flatMapLatest { request ->
            flow<RouteState> {
                val target = request.target
                val fix = (request.origin as? LocationState.Available)?.fix
                when {
                    target == null -> emit(RouteState.SelectDestination)
                    fix == null -> emit(RouteState.Unavailable("Current location is required for road distance."))
                    else -> {
                        emit(RouteState.Loading)
                        delay(750L) // New destinations/fixes cancel both this delay and Retrofit's in-flight call.
                        if (!LiveLocationSource.isFresh(fix.elapsedRealtimeNanos)) {
                            emit(RouteState.Unavailable("Waiting for a fresh location fix."))
                            return@flow
                        }
                        try {
                            val result = api.route(fix.coordinates.latitude, fix.coordinates.longitude,
                                target.coordinates.latitude, target.coordinates.longitude)
                            require(result.distanceMeters >= 0 && result.travelTimeSeconds >= 0)
                            emit(RouteState.Available(result, formatRoadDistance(result.distanceMeters), fix.approximate))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: IOException) {
                            emit(RouteState.Unavailable("Road distance is unavailable while offline."))
                        } catch (error: HttpException) {
                            emit(RouteState.Unavailable(if (error.code() == 404 || error.code() == 422)
                                "No drivable route was found." else "Road distance is temporarily unavailable."))
                        } catch (_: SerializationException) {
                            emit(RouteState.Unavailable("The route service returned unreadable distance data."))
                        } catch (_: IllegalArgumentException) {
                            emit(RouteState.Unavailable("The route service returned invalid distance data."))
                        }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), RouteState.SelectDestination)

    fun selectTarget(target: MapTarget?) { selected.value = target }
    fun refreshPermissionAndSettings() { refresh.update { it + 1 } }

    class Factory(private val source: LiveLocationSource, private val api: RtcApi) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocationViewModel::class.java))
            return LocationViewModel(source, api) as T
        }
    }
}

fun formatRoadDistance(meters: Long): String {
    require(meters >= 0)
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-ZA"))
    return if (meters < 1_000) "${formatter.format(meters)} m" else {
        formatter.maximumFractionDigits = 1
        "${formatter.format(meters / 1_000.0)} km"
    }
}

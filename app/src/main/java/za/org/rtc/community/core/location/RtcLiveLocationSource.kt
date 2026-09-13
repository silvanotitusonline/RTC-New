package za.org.rtc.community.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import za.org.rtc.community.core.maps.GeoPoint

sealed interface LiveLocationReading {
    data class Fix(val position: GeoPoint, val accuracyMeters: Float) : LiveLocationReading
    data class Unavailable(val reason: String) : LiveLocationReading
}

/** Collected only by a visible map; cancellation removes every location callback and receiver. */
@Singleton
class RtcLiveLocationSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locations(): Flow<LiveLocationReading> = callbackFlow {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            trySend(LiveLocationReading.Unavailable("Allow location access to calculate a route from your position."))
            close()
            return@callbackFlow
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var expiry: Job? = null
        fun unavailable(message: String) {
            expiry?.cancel()
            trySend(LiveLocationReading.Unavailable(message))
        }
        fun accept(location: Location) {
            val point = GeoPoint(location.latitude, location.longitude)
            val remaining = locationFreshnessRemainingMillis(location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos())
            if (!point.isValid || remaining <= 0 || !LocationManagerCompat.isLocationEnabled(manager)) return
            expiry?.cancel()
            trySend(LiveLocationReading.Fix(point, location.accuracy))
            expiry = launch {
                delay(remaining)
                trySend(LiveLocationReading.Unavailable("Your location is out of date. Waiting for a fresh position."))
            }
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::accept)
            }
        }
        val providerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!LocationManagerCompat.isLocationEnabled(manager)) {
                    unavailable("Location services are off. Turn them on in Settings to find your position.")
                }
            }
        }
        ContextCompat.registerReceiver(context, providerReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (!LocationManagerCompat.isLocationEnabled(manager)) unavailable("Location services are off. Turn them on in Settings to find your position.")
        else unavailable("Waiting for your current location…")
        try {
            val request = LocationRequest.Builder(
                if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                10_000L,
            ).setMinUpdateIntervalMillis(5_000L).setWaitForAccurateLocation(false).build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { unavailable("Your location is unavailable. Check device location settings and retry.") }
            client.lastLocation.addOnSuccessListener { it?.let(::accept) }
                .addOnFailureListener { /* Active updates can still deliver a fresh position. */ }
        } catch (_: SecurityException) {
            unavailable("Location permission changed. Allow access to resume live directions.")
        }
        awaitClose {
            expiry?.cancel()
            client.removeLocationUpdates(callback)
            context.unregisterReceiver(providerReceiver)
        }
    }
}

/** Monotonic age is immune to wall-clock changes; fixes older than one minute are never routed. */
internal fun locationFreshnessRemainingMillis(fixElapsedNanos: Long, nowElapsedNanos: Long): Long {
    if (fixElapsedNanos <= 0 || fixElapsedNanos > nowElapsedNanos) return 0
    return (60_000L - (nowElapsedNanos - fixElapsedNanos) / 1_000_000L).coerceAtLeast(0L)
}

package za.org.rtc.remediation.location

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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class LocationFix(
    val coordinates: Coordinates,
    val accuracyMeters: Float,
    val approximate: Boolean,
    val elapsedRealtimeNanos: Long,
)

sealed interface LocationState {
    data object PermissionRequired : LocationState
    data object SettingsDisabled : LocationState
    data object Searching : LocationState
    data class Available(val fix: LocationFix) : LocationState
    data class Unavailable(val message: String) : LocationState
}

/** Collect only while the location screen is visible; collection cancellation releases GPS. */
class LiveLocationSource(context: Context) {
    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean = has(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        has(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun has(permission: String): Boolean = ContextCompat.checkSelfPermission(
        appContext, permission,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Rechecked before registration, delivery and watchdog ticks.
    fun updates(): Flow<LocationState> = callbackFlow {
        if (!hasPermission()) {
            trySend(LocationState.PermissionRequired)
            close()
            return@callbackFlow
        }
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) !=
            ConnectionResult.SUCCESS
        ) {
            trySend(LocationState.Unavailable("Google Play services is unavailable on this device."))
            close()
            return@callbackFlow
        }

        var lastFix: Location? = null
        val startedAt = SystemClock.elapsedRealtimeNanos()
        fun publish(location: Location?) {
            when {
                !hasPermission() -> trySend(LocationState.PermissionRequired)
                !LocationManagerCompat.isLocationEnabled(manager) -> {
                    lastFix = null
                    trySend(LocationState.SettingsDisabled)
                }
                location == null -> trySend(LocationState.Searching)
                !isFresh(location.elapsedRealtimeNanos) ->
                    trySend(LocationState.Unavailable("Waiting for a fresh location fix."))
                !location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
                    !location.longitude.isFinite() || location.longitude !in -180.0..180.0 ||
                    !location.hasAccuracy() || !location.accuracy.isFinite() ->
                    trySend(LocationState.Unavailable("Location accuracy is unavailable."))
                else -> {
                    lastFix = location
                    trySend(LocationState.Available(LocationFix(
                        coordinates = Coordinates(location.latitude, location.longitude),
                        accuracyMeters = location.accuracy,
                        approximate = !has(Manifest.permission.ACCESS_FINE_LOCATION),
                        elapsedRealtimeNanos = location.elapsedRealtimeNanos,
                    )))
                }
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                publish(result.locations.maxByOrNull { it.elapsedRealtimeNanos })
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable && lastFix == null) {
                    if (!LocationManagerCompat.isLocationEnabled(manager)) {
                        trySend(LocationState.SettingsDisabled)
                    } else {
                        trySend(LocationState.Unavailable("Location is temporarily unavailable."))
                    }
                }
            }
        }
        val providerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { publish(lastFix) }
        }
        ContextCompat.registerReceiver(appContext, providerReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_EXPORTED)

        publish(null)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .build()
        try {
            fused.lastLocation.addOnSuccessListener { cached ->
                // A late cached result must never overwrite a newer live fix.
                if (cached != null && cached.elapsedRealtimeNanos >
                    (lastFix?.elapsedRealtimeNanos ?: Long.MIN_VALUE)
                ) publish(cached)
            }
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    trySend(if (error is SecurityException) LocationState.PermissionRequired else
                        LocationState.Unavailable("Location updates could not be started."))
                    close()
                }
        } catch (_: SecurityException) {
            trySend(LocationState.PermissionRequired)
            close()
        }
        val watchdog = launch {
            while (isActive) {
                delay(10_000L)
                if (!hasPermission()) {
                    trySend(LocationState.PermissionRequired)
                    close()
                    break
                }
                if (!LocationManagerCompat.isLocationEnabled(manager)) {
                    lastFix = null
                    trySend(LocationState.SettingsDisabled)
                } else if (lastFix?.let { !isFresh(it.elapsedRealtimeNanos) } == true) {
                    trySend(LocationState.Unavailable("Waiting for a fresh location fix."))
                } else if (lastFix == null && SystemClock.elapsedRealtimeNanos() - startedAt > 30_000_000_000L) {
                    trySend(LocationState.Unavailable("No location fix is available. Check location settings or retry outdoors."))
                }
            }
        }
        awaitClose {
            watchdog.cancel()
            fused.removeLocationUpdates(callback)
            appContext.unregisterReceiver(providerReceiver)
        }
    }.flowOn(Dispatchers.Main.immediate).conflate()

    companion object {
        const val MAX_FIX_AGE_MS = 60_000L
        fun isFresh(fixElapsedNanos: Long, nowNanos: Long = SystemClock.elapsedRealtimeNanos()): Boolean =
            fixElapsedNanos > 0 && nowNanos >= fixElapsedNanos &&
                nowNanos - fixElapsedNanos <= MAX_FIX_AGE_MS * 1_000_000L
    }
}

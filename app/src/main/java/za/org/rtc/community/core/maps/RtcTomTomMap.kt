package za.org.rtc.community.core.maps

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.common.WidthByZoom
import com.tomtom.sdk.map.display.compose.TomTomMap
import com.tomtom.sdk.map.display.compose.model.MapDisplayInfrastructure
import com.tomtom.sdk.map.display.compose.model.MarkerData
import com.tomtom.sdk.map.display.compose.model.PolylineData
import com.tomtom.sdk.map.display.compose.nodes.Logo
import com.tomtom.sdk.map.display.compose.nodes.Marker
import com.tomtom.sdk.map.display.compose.nodes.Polyline
import com.tomtom.sdk.map.display.compose.nodes.Traffic
import com.tomtom.sdk.map.display.compose.properties.MarkerProperties
import com.tomtom.sdk.map.display.compose.properties.PolylineProperties
import com.tomtom.sdk.map.display.compose.state.rememberMapViewState
import com.tomtom.sdk.map.display.compose.state.rememberTrafficState
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.telemetry.Telemetry
import com.tomtom.sdk.telemetry.UserConsent
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import com.tomtom.sdk.location.GeoPoint as TomTomGeoPoint

/**
 * Native TomTom Standard Compose renderer using the publicly available Complete flavor.
 * The SDK owns map lifecycle handling. RTC owns only foreground permissioned location input,
 * actual service/report markers and the route geometry returned by the authenticated backend.
 * API keys arrive at runtime; no REST credential is embedded in this renderer or the APK.
 */
@Composable
fun RtcTomTomMap(
    mapKey: String,
    markers: List<RtcMapMarker>,
    userLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    selectedMarkerId: String?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    defaultCenter: GeoPoint? = null,
    onMapError: (String) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val errorCallback by rememberUpdatedState(onMapError)
    var initialized by remember(mapKey) { mutableStateOf(false) }
    var initializationError by remember(mapKey) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(mapKey, retry) {
        initializationError = null
        try {
            if (mapKey.isBlank()) throw MapStartupException("Map configuration is unavailable. Reopen the map to retry.")
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (activityManager.deviceConfigurationInfo.reqGlEsVersion < 0x30000) {
                throw MapStartupException("This device does not support the native map renderer (OpenGL ES 3.0).")
            }
            RtcTomTomSdk.ensureInitialized(context, mapKey)
            initialized = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MapStartupException) {
            initializationError = failure.message
        } catch (_: LinkageError) {
            initializationError = "The native map renderer is unavailable on this device."
        } catch (_: Exception) {
            // SDK exceptions can contain request URLs. Never expose/log a URL carrying the key.
            initializationError = "The map could not start. Check your connection and retry."
        }
        initializationError?.let(errorCallback)
    }
    Box(modifier) {
        when {
            initialized -> NativeRtcMap(
                markers = markers,
                userLocation = userLocation?.takeIf { it.isValid },
                routePoints = routePoints.takeIf { points -> points.all { it.isValid } }.orEmpty(),
                selectedMarkerId = selectedMarkerId,
                onMarkerClick = onMarkerClick,
                defaultCenter = defaultCenter?.takeIf { it.isValid },
                modifier = Modifier.fillMaxSize(),
                onMapError = errorCallback,
            )
            initializationError != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(requireNotNull(initializationError))
                TextButton(onClick = { retry++ }) { Text("Retry map") }
            }
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

/** Process-wide initialization is serialized and kept off the UI thread. */
private class MapStartupException(message: String) : IllegalStateException(message)

private object RtcTomTomSdk {
    private val mutex = Mutex()
    private var configuredKeyDigest: String? = null

    suspend fun ensureInitialized(context: Context, mapKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val digest = MessageDigest.getInstance("SHA-256").digest(mapKey.toByteArray())
                .joinToString("") { "%02x".format(it) }
            if (TomTomSdk.isInitialized) {
                // SDK2 initialization is process-scoped. A rotated credential needs a cold start.
                if (configuredKeyDigest != digest) {
                    throw MapStartupException("Restart the app to load the updated map configuration.")
                }
                return@withLock
            }
            TomTomSdk.initialize(
                context = context,
                sdkConfiguration = buildSdkConfiguration(
                    context = context,
                    apiKey = mapKey,
                    telemetryUserConsent = { UserConsent.TelemetryOff },
                ),
            )
            configuredKeyDigest = digest
        }
    }
}

@Composable
private fun NativeRtcMap(
    markers: List<RtcMapMarker>,
    userLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    selectedMarkerId: String?,
    onMarkerClick: (String) -> Unit,
    defaultCenter: GeoPoint?,
    modifier: Modifier,
    onMapError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val mapInstance = rememberSaveable { UUID.randomUUID().toString() }
    val validMarkers = remember(markers) { markers.filter { it.position.isValid }.distinctBy { it.id } }
    val initialPoint = remember { userLocation ?: validMarkers.firstOrNull()?.position ?: defaultCenter }
    val infrastructure = remember { MapDisplayInfrastructure(sdkContext = TomTomSdk.sdkContext) }
    val state = rememberMapViewState(
        key = mapInstance,
        initialCameraOptions = InitialCameraOptions.LocationBased(
            position = initialPoint?.toTomTom() ?: TomTomGeoPoint(0.0, 0.0),
            zoom = if (initialPoint == null) 1.0 else 12.0,
        ),
        // Texture rendering respects the rounded bounds of RTC cards and overlapping UI.
        renderToTextureView = true,
    ) {
        safeArea = PaddingValues(start = 24.dp, top = 24.dp, end = 80.dp, bottom = 48.dp)
        frameRate = 30
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var mapReady by remember { mutableStateOf(false) }
    var mapLoadDelayed by remember { mutableStateOf(false) }
    var trafficEnabled by remember { mutableStateOf(false) }
    var showTrafficConsent by remember { mutableStateOf(false) }
    var showCopyright by remember { mutableStateOf(false) }
    var copyrightText by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("© TomTom") }
    val traffic = rememberTrafficState()
    val onError by rememberUpdatedState(onMapError)
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val ordinaryPin = remember(density) { ImageFactory.fromBitmap(mapPin(density, 0xFF176B44.toInt())) }
    val selectedPin = remember(density) { ImageFactory.fromBitmap(mapPin(density, 0xFFD59D17.toInt())) }
    val userPin = remember(density) { ImageFactory.fromBitmap(mapPin(density, 0xFF1565C0.toInt(), true)) }
    val routeData = remember(routePoints) { routePoints.map { it.toTomTom() } }
    val routeProperties = remember(routeColor) {
        PolylineProperties {
            lineColor = routeColor
            lineWidths = listOf(WidthByZoom(width = 5.0))
            outlineColor = android.graphics.Color.WHITE
            outlineWidths = listOf(WidthByZoom(width = 1.5))
            isClickable = false
        }
    }

    fun fitContent() {
        val points = routePoints.ifEmpty { validMarkers.map { it.position } + listOfNotNull(userLocation) }
        val camera = fitRtcMapCamera(points, viewport.width / density, viewport.height / density) ?: return
        state.cameraState.moveCamera(
            CameraOptions(position = camera.center.toTomTom(), zoom = camera.zoom, tilt = 0.0, rotation = 0.0),
        )
    }

    LaunchedEffect(state, mapReady) {
        if (!mapReady) {
            delay(25_000)
            mapLoadDelayed = true
            onError("Map tiles are taking longer to load. Check your connection.")
        } else {
            mapLoadDelayed = false
            readMapText { state.copyrightsState.getCaption() }?.let { caption = it }
        }
    }
    // SDK-backed camera state survives rotation. Refit on a new destination only; a live
    // location/traffic refresh must not wrest control away from someone browsing the map.
    var lastDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var fittedRouteDestination by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(mapReady, selectedMarkerId, routePoints.isNotEmpty(), viewport) {
        if (!mapReady || viewport == IntSize.Zero) return@LaunchedEffect
        val routeDestination = selectedMarkerId ?: routePoints.lastOrNull()?.let {
            "${it.latitude},${it.longitude}"
        }
        if (routePoints.size >= 2 && fittedRouteDestination != routeDestination) {
            fitContent()
            fittedRouteDestination = routeDestination
            lastDestination = selectedMarkerId
        } else if (routePoints.isEmpty() && selectedMarkerId != null && selectedMarkerId != lastDestination) {
            fittedRouteDestination = null
            validMarkers.firstOrNull { it.id == selectedMarkerId }?.let { marker ->
                state.cameraState.animateCamera(
                    CameraOptions(position = marker.position.toTomTom(), zoom = 14.0),
                    animationDuration = 350.milliseconds,
                )
                lastDestination = marker.id
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.cameraState.data }.filterNotNull().collect { camera ->
            if (camera.isStable) {
                readMapText { state.copyrightsState.getCaption() }?.let { caption = it }
            }
        }
    }
    LaunchedEffect(state) {
        TomTomSdk.failures.collect {
            onError("Map data is unavailable. Reopen the map when your connection returns.")
        }
    }
    TrafficConsentLifecycle(trafficEnabled && userLocation != null, onFailure = {
        trafficEnabled = false
        onError("Live traffic needs location access. Enable location and try again.")
    })
    LaunchedEffect(trafficEnabled, userLocation) {
        traffic.showTrafficFlow = trafficEnabled && userLocation != null
        traffic.showTrafficIncidents = trafficEnabled && userLocation != null
    }

    Box(modifier.onSizeChanged { viewport = it }) {
        TomTomMap(
            infrastructure = infrastructure,
            state = state,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { mapReady = true },
        ) {
            Logo()
            Traffic(state = traffic)
            if (routeData.size >= 2) {
                Polyline(data = PolylineData(geoPoints = routeData), properties = routeProperties)
            }
            validMarkers.forEach { marker ->
                key(marker.id) {
                    Marker(
                        data = MarkerData(geoPoint = marker.position.toTomTom()),
                        properties = MarkerProperties(pinImage = if (marker.id == selectedMarkerId) selectedPin else ordinaryPin),
                        onClick = { onMarkerClick(marker.id) },
                    )
                }
            }
            userLocation?.let { point ->
                key("rtc-current-location") {
                    Marker(
                        data = MarkerData(geoPoint = point.toTomTom()),
                        properties = MarkerProperties(pinImage = userPin) {
                            placementAnchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                        },
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                modifier = Modifier.size(48.dp),
                enabled = mapReady && userLocation != null,
                onClick = {
                    userLocation?.let { point ->
                        scope.launch {
                            state.cameraState.animateCamera(
                                CameraOptions(position = point.toTomTom(), zoom = 15.0, rotation = 0.0),
                                animationDuration = 350.milliseconds,
                            )
                        }
                    }
                },
            ) { Icon(Icons.Default.MyLocation, "Center on my location") }
            FilledTonalIconButton(
                modifier = Modifier.size(48.dp),
                enabled = mapReady && (routePoints.isNotEmpty() || validMarkers.isNotEmpty()),
                onClick = { fitContent() },
            ) { Icon(Icons.Default.CenterFocusStrong, "Fit route and locations") }
            FilledTonalIconButton(
                modifier = Modifier.size(48.dp),
                enabled = mapReady && userLocation != null,
                onClick = {
                    if (trafficEnabled) trafficEnabled = false else showTrafficConsent = true
                },
            ) {
                Icon(
                    Icons.Default.Traffic,
                    if (trafficEnabled) "Turn live traffic off" else "Turn live traffic on",
                    tint = if (trafficEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (!mapReady) {
            Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(if (mapLoadDelayed) "Waiting for map tiles…" else "Loading map…", Modifier.padding(top = 8.dp))
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
            TextButton(onClick = {
                showCopyright = true
                scope.launch {
                    copyrightText = readMapText { state.copyrightsState.getCopyrights() }
                        ?: "Copyright information is temporarily unavailable. Map data © TomTom."
                }
            }, enabled = mapReady) { Text(caption, Modifier.widthIn(max = 240.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (showTrafficConsent) {
        AlertDialog(
            onDismissRequest = { showTrafficConsent = false },
            title = { Text("Enable live traffic?") },
            text = {
                Text("TomTom uses your location with timestamps to provide and improve live traffic. " +
                    "Location sharing is active only while this map is open. Turn Traffic off at any time to stop sharing.")
            },
            confirmButton = {
                TextButton(onClick = { showTrafficConsent = false; trafficEnabled = true }) { Text("Enable traffic") }
            },
            dismissButton = { TextButton(onClick = { showTrafficConsent = false }) { Text("Keep off") } },
        )
    }
    if (showCopyright) {
        AlertDialog(
            onDismissRequest = { showCopyright = false },
            title = { Text("Map attribution") },
            text = { Text(copyrightText.ifBlank { "Loading attribution…" }, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showCopyright = false }) { Text("Close") } },
        )
    }
}

/** Traffic consent and the SDK provider expire when the map leaves the foreground. */
@Composable
private fun TrafficConsentLifecycle(enabled: Boolean, onFailure: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failure by rememberUpdatedState(onFailure)
    DisposableEffect(lifecycle, enabled) {
        fun stop() {
            TomTomSdk.locationProvider.disable()
            Telemetry.setConsent(UserConsent.TelemetryOff)
        }
        fun update() {
            if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    Telemetry.setConsent(UserConsent.LocationOnly)
                    TomTomSdk.locationProvider.enable()
                } catch (_: SecurityException) {
                    stop()
                    failure()
                }
            } else stop()
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
}

private fun GeoPoint.toTomTom() = TomTomGeoPoint(latitude = latitude, longitude = longitude)

/** Ordinary UI marker artwork, independent of RTC's supplied logo. */
private fun mapPin(density: Float, color: Int, isLocation: Boolean = false): Bitmap {
    val size = (48 * density).toInt().coerceAtLeast(48)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.scale(size / 48f, size / 48f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (!isLocation) {
            val path = Path().apply {
                moveTo(24f, 46f)
                cubicTo(19f, 37f, 7f, 27f, 7f, 18f)
                cubicTo(7f, -3f, 41f, -3f, 41f, 18f)
                cubicTo(41f, 27f, 29f, 37f, 24f, 46f)
                close()
            }
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawPath(path, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(24f, 18f, 6f, paint)
        } else {
            paint.color = 0x331565C0
            canvas.drawCircle(24f, 24f, 22f, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(24f, 24f, 12f, paint)
            paint.color = color
            canvas.drawCircle(24f, 24f, 9f, paint)
        }
    }
}

private suspend fun readMapText(read: suspend () -> String): String? = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

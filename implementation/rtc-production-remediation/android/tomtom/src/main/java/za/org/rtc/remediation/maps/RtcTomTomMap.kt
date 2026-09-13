package za.org.rtc.remediation.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.InitialCameraOptions
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.marker.MarkerOptions
import com.tomtom.sdk.map.display.ui.MapView
import kotlinx.coroutines.delay
import za.org.rtc.remediation.location.Coordinates
import za.org.rtc.remediation.location.MapTarget

/** Native TomTom MapView, SDK2.5.3 Standard renderer, Extended flavor. */
@Composable
fun RtcTomTomMap(
    apiKey: String,
    target: MapTarget?,
    currentLocation: Coordinates?,
    modifier: Modifier = Modifier,
) {
    val center = target?.coordinates ?: currentLocation
    if (apiKey.isBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("The map service has not been configured.") }
        return
    }
    if (center == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Choose a real destination or enable your location to view the map.") }
        return
    }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val saved = rememberSaveable(saver = MapSavedState.Saver) { MapSavedState(null) }
    val creation = remember(context, apiKey, owner) {
        runCatching { MapHandle(context, apiKey, center, saved, target, currentLocation) }
    }
    val handle = creation.getOrNull()
    if (handle == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("The map could not start on this device.") }
        return
    }

    DisposableEffect(handle, owner) {
        owner.lifecycle.addObserver(handle)
        onDispose {
            owner.lifecycle.removeObserver(handle)
            handle.destroy()
        }
    }
    LaunchedEffect(handle, target, currentLocation) { handle.update(target, currentLocation) }
    var takingLong by remember(handle) { mutableStateOf(false) }
    LaunchedEffect(handle) {
        delay(20_000)
        takingLong = true
    }
    Box(modifier) {
        AndroidView(factory = { handle.view }, modifier = Modifier.fillMaxSize())
        if (!handle.ready) {
            Surface(Modifier.align(Alignment.TopCenter)) {
                Text(if (takingLong) "Map is taking longer to load. Check your connection and map access."
                    else "Loading map…")
            }
        }
    }
}

private class MapSavedState(var bundle: Bundle?) {
    var handle: MapHandle? = null
    fun snapshot(): Bundle = handle?.snapshot() ?: bundle ?: Bundle()
    companion object {
        val Saver = Saver<MapSavedState, Bundle>(save = { it.snapshot() }, restore = { MapSavedState(it) })
    }
}

private class MapHandle(
    context: Context,
    apiKey: String,
    center: Coordinates,
    private val saved: MapSavedState,
    initialTarget: MapTarget?,
    initialLocation: Coordinates?,
) : DefaultLifecycleObserver {
    val view = MapView(context, MapOptions(
        mapKey = apiKey,
        initialCameraOptions = InitialCameraOptions.LocationBased(position = center.geoPoint(), zoom = 13.0),
        renderToTexture = true,
    ))
    var ready by mutableStateOf(false)
        private set
    private var map: TomTomMap? = null
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var target: MapTarget? = initialTarget
    private var current: Coordinates? = initialLocation
    private var lastTarget: MapTarget? = null
    private var firstRender = true
    private val restored = saved.bundle != null
    private val targetImage = ImageFactory.fromBitmap(markerBitmap(context, Color.rgb(215, 89, 24)))
    private val originImage = ImageFactory.fromBitmap(markerBitmap(context, Color.rgb(30, 104, 205)))
    private val pendingMap: () -> Unit

    init {
        view.onCreate(saved.bundle)
        saved.handle = this
        val request = view.getMapAsync { loaded ->
            if (!destroyed) {
                map = loaded
                ready = true
                render()
            }
        }
        pendingMap = { request.cancel() }
    }

    fun update(target: MapTarget?, currentLocation: Coordinates?) {
        this.target = target
        this.current = currentLocation
        render()
    }

    private fun render() {
        val loaded = map ?: return
        if (destroyed) return
        // Remove tagged restored markers too, so rotation cannot duplicate old pins.
        loaded.removeMarkers(TARGET_TAG)
        loaded.removeMarkers(ORIGIN_TAG)
        target?.let {
            loaded.addMarker(MarkerOptions(coordinate = it.coordinates.geoPoint(), pinImage = targetImage,
                placementAnchor = PointF(0.5f, 0.5f), tag = TARGET_TAG, balloonText = it.title))
        }
        current?.let {
            loaded.addMarker(MarkerOptions(coordinate = it.geoPoint(), pinImage = originImage,
                placementAnchor = PointF(0.5f, 0.5f), tag = ORIGIN_TAG, balloonText = "Your location"))
        }
        // Preserve restored camera and the user's pan/zoom; recenter only for a new destination.
        if (target != null && target != lastTarget && !(firstRender && restored)) {
            loaded.moveCamera(CameraOptions(position = target!!.coordinates.geoPoint(), zoom = 13.0))
        }
        lastTarget = target
        firstRender = false
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!destroyed && !started) { view.onStart(); started = true }
    }
    override fun onResume(owner: LifecycleOwner) {
        onStart(owner)
        if (!destroyed && !resumed) { view.onResume(); resumed = true }
    }
    override fun onPause(owner: LifecycleOwner) {
        if (!destroyed && resumed) { view.onPause(); resumed = false }
    }
    override fun onStop(owner: LifecycleOwner) {
        onPause(owner)
        if (!destroyed && started) { saved.bundle = snapshot(); view.onStop(); started = false }
    }
    override fun onDestroy(owner: LifecycleOwner) { destroy() }

    fun snapshot(): Bundle = if (destroyed) saved.bundle ?: Bundle() else Bundle().also(view::onSaveInstanceState)
    fun destroy() {
        if (destroyed) return
        saved.bundle = snapshot()
        pendingMap()
        if (resumed) view.onPause()
        if (started) view.onStop()
        view.onDestroy()
        destroyed = true
        resumed = false
        started = false
        map = null
        if (saved.handle === this) saved.handle = null
    }

    companion object {
        private const val TARGET_TAG = "rtc-selected-destination"
        private const val ORIGIN_TAG = "rtc-current-location"
    }
}

private fun Coordinates.geoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

private fun markerBitmap(context: Context, color: Int): Bitmap {
    val size = (28 * context.resources.displayMetrics.density).toInt().coerceAtLeast(28)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center - 1f, paint)
        paint.color = color
        canvas.drawCircle(center, center, center * 0.70f, paint)
    }
}

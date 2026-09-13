package za.org.rtc.community.core.maps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Native live map plus an accessible text equivalent; can fill a screen or a bounded card. */
@Composable
fun LiveMapPanel(
    markers: List<RtcMapMarker>,
    onOpenMarker: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedMarkerId: String? = null,
    onChooseLocation: ((GeoPoint) -> Unit)? = null,
    viewModel: LiveMapViewModel = hiltViewModel(key = rememberSaveable { "rtc-live-map-${UUID.randomUUID()}" }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLocations by remember(state.accountId) { mutableStateOf(false) }
    var showInstructions by remember(state.accountId, state.selectedMarker?.id, state.travelMode) { mutableStateOf(false) }
    var reloadAttempt by remember(state.accountId) { mutableIntStateOf(0) }
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), hasLocationPermission())
        if (hasLocationPermission()) viewModel.retryLocation()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), hasLocationPermission())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), hasLocationPermission())
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setForeground(false, false)
        }
    }
    LaunchedEffect(markers, initialSelectedMarkerId, state.accountId) {
        viewModel.setMarkers(markers, initialSelectedMarkerId)
    }
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (onChooseLocation == null) "Live map" else "Choose location", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (state.accountId == null) {
            Text("Sign in to view live maps and directions.")
            return@Column
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text("Search places or street addresses") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.retrySearch() }),
            trailingIcon = {
                if (state.query.isNotEmpty()) TextButton(onClick = { viewModel.setQuery("") }) { Text("Clear") }
            },
        )
        if (state.searchLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.searchError?.let { MapError(it, viewModel::retrySearch) }
        if (state.searchAttempted && !state.searchLoading && state.searchError == null && state.searchResults.isEmpty()) {
            Text("No places found. Try another street, business name or town.", style = MaterialTheme.typography.bodyMedium)
        }
        state.searchResults.forEach { place ->
            OutlinedButton(onClick = { viewModel.selectPlace(place) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(place.title, style = MaterialTheme.typography.labelLarge)
                    Text(place.address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.configurationLoading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("Connecting to live maps…")
            }
        }
        state.configurationError?.let { message ->
            MapError(message) {
                reloadAttempt++
                viewModel.retryConfiguration()
            }
        }
        state.config?.let { config ->
            val renderedMarkers = remember(state.markers, state.selectedMarker) {
                (state.markers + listOfNotNull(state.selectedMarker)).distinctBy { it.id }
            }
            key(state.accountId, config.mapKey, reloadAttempt) {
                RtcTomTomMap(
                    mapKey = config.mapKey,
                    markers = renderedMarkers,
                    userLocation = state.userLocation,
                    routePoints = state.route?.points.orEmpty(),
                    selectedMarkerId = state.selectedMarker?.id,
                    onMarkerClick = viewModel::selectMarker,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    defaultCenter = config.defaultCenter,
                    onMapError = viewModel::reportMapError,
                )
            }
            Text(config.attribution, style = MaterialTheme.typography.labelSmall)
        }
        Text(state.locationMessage, style = MaterialTheme.typography.bodyMedium)
        state.locationAccuracyMeters?.takeIf { it.isFinite() && it >= 0f }?.let { accuracy ->
            Text("Device accuracy: approximately ${accuracy.toInt()} m", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (hasLocationPermission()) viewModel.retryLocation()
                else requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.weight(1f)) { Text(if (onChooseLocation == null) "Use my location" else "Find my location") }
            TextButton(onClick = {
                val intent = if (hasLocationPermission()) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }
            }, modifier = Modifier.widthIn(min = 48.dp)) { Text(if (hasLocationPermission()) "Settings" else "Permissions") }
        }
        onChooseLocation?.let { choose ->
            Button(
                onClick = {
                    val latest = viewModel.state.value
                    if (latest.accountId == state.accountId) latest.userLocation?.takeIf { it.isValid }?.let(choose)
                },
                enabled = state.userLocation?.isValid == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use current location") }
        }
        if (state.userLocation != null) {
            TextButton(onClick = viewModel::identifyCurrentLocation, enabled = !state.addressLoading) {
                Text(if (state.addressLoading) "Finding address…" else "Where am I?")
            }
        }
        state.currentAddress?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.markers.isEmpty()) {
            Text("No community locations are available in this view. You can still search for a place.", style = MaterialTheme.typography.bodyMedium)
        } else {
            TextButton(onClick = { showLocations = !showLocations }) {
                Text(if (showLocations) "Hide locations" else "Show locations (${state.markers.size})")
            }
            if (showLocations) state.markers.forEach { marker ->
                OutlinedButton(onClick = { viewModel.selectMarker(marker.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(marker.title + if (state.selectedMarker?.id == marker.id) " — selected" else "")
                }
            }
        }
        state.selectedMarker?.let { selected ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (selected.subtitle.isNotBlank()) Text(selected.subtitle, style = MaterialTheme.typography.bodyMedium)
                    onChooseLocation?.let { choose ->
                        Button(
                            onClick = {
                                val latest = viewModel.state.value
                                if (latest.accountId == state.accountId) latest.selectedMarker
                                    ?.takeIf { it.id == selected.id && it.position.isValid }
                                    ?.position?.let(choose)
                            },
                            enabled = selected.position.isValid,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Use selected location") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("car" to "Drive", "pedestrian" to "Walk", "bicycle" to "Cycle").forEach { (mode, label) ->
                            FilterChip(selected = state.travelMode == mode, onClick = { viewModel.setTravelMode(mode) }, label = { Text(label) })
                        }
                    }
                    if (state.userLocation == null) Text("A fresh device location is required for road distance and travel time.")
                    if (state.routeLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Calculating your route…", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.routeError?.let { MapError(it, viewModel::refreshRoute) }
                    state.route?.let { route ->
                        Text(
                            "${formatRouteDistance(route.distanceMeters)} · ${formatRouteDuration(route.durationSeconds)}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (route.travelMode == "car" && route.trafficDelaySeconds > 0) {
                            Text("Includes ${formatRouteDuration(route.trafficDelaySeconds)} traffic delay", style = MaterialTheme.typography.bodySmall)
                        }
                        val calculated = remember(route.calculatedAt) {
                            runCatching { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(route.calculatedAt)) }.getOrNull()
                        }
                        calculated?.let { Text("Route calculated at $it", style = MaterialTheme.typography.bodySmall) }
                        if (route.instructions.isNotEmpty()) {
                            TextButton(onClick = { showInstructions = !showInstructions }) {
                                Text(if (showInstructions) "Hide directions" else "Show directions")
                            }
                            if (showInstructions) route.instructions.forEachIndexed { index, instruction ->
                                HorizontalDivider()
                                Text("${index + 1}. ${instruction.message}", style = MaterialTheme.typography.bodyMedium)
                                Text("At ${formatRouteDistance(instruction.routeOffsetInMeters)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = viewModel::refreshRoute) { Text("Refresh route") }
                    }
                    if (!selected.id.startsWith("place:")) Button(onClick = { onOpenMarker(selected.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open details")
                    }
                    TextButton(onClick = viewModel::clearSelection) { Text("Clear destination") }
                }
            }
        }
    }
}

@Composable
private fun MapError(message: String, onRetry: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

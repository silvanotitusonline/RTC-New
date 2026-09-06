package za.org.rtc.community.feature.servicecentre.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreProviderViewModel
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ServiceCentreProviderProfileRoute(
    viewModel: ServiceCentreProviderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var categoryId by rememberSaveable { mutableStateOf("") }
    var locality by rememberSaveable { mutableStateOf("") }
    var startingPrice by rememberSaveable { mutableStateOf("") }
    var radiusKm by rememberSaveable { mutableIntStateOf(25) }
    var profileSeeded by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.useMyLocation()
    }
    val useMyLocation = {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarse || fine) viewModel.useMyLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.profile) {
        val profile = state.profile
        if (profile != null && !profileSeeded) {
            categoryId = profile.primaryCategoryId
            locality = profile.locality
            startingPrice = profile.startingPrice.toPlainString()
            radiusKm = profile.serviceRadiusKm
            profileSeeded = true
        }
    }
    LaunchedEffect(state.coordinates) {
        if (state.coordinates != null) viewModel.suggestedLocality()?.let { locality = it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text(if (state.profile == null) "Become a Provider" else "Provider profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Add only your service, service area and starting price. You can update or pause this profile at any time.")
                ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                    Text("What service do you offer?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                        state.categories.forEach { category ->
                            FilterChip(selected = categoryId == category.id, onClick = { categoryId = category.id }, label = { Text(category.name) })
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                    Text("Where do you work?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = locality,
                        onValueChange = { locality = it.take(120) },
                        label = { Text("Town or suburb") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = useMyLocation, modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(Modifier.size(RtcSpacing.compact))
                        Text("Use my current location")
                    }
                    Text("Travel radius · $radiusKm km", style = MaterialTheme.typography.labelLarge)
                    Slider(value = radiusKm.toFloat(), onValueChange = { radiusKm = it.roundToInt().coerceIn(1, 50) }, valueRange = 1f..50f)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                    Text("Starting price", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = startingPrice,
                        onValueChange = { startingPrice = it.take(12) },
                        label = { Text("Starting price") },
                        prefix = { Text("R ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.save(categoryId, locality, startingPrice, radiusKm) },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget),
            ) { Text(if (state.profile == null) "Start offering services" else "Save changes") }
        }
        state.profile?.let { profile ->
            item {
                OutlinedButton(
                    onClick = { viewModel.setActive(!profile.active) },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget),
                ) { Text(if (profile.active) "Pause profile" else "Resume profile") }
            }
        }
    }
}

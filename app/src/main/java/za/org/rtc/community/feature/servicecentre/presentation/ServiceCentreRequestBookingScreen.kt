package za.org.rtc.community.feature.servicecentre.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreBookingViewModel
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ServiceCentreRequestBookingRoute(
    providerReference: String,
    marketplaceBusinessId: String? = null,
    marketplaceOfferingId: String? = null,
    onNavigate: (String) -> Unit,
    viewModel: ServiceCentreBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var whenText by rememberSaveable { mutableStateOf(ServiceCentreBookingViewModel.defaultWhenText()) }
    var location by rememberSaveable { mutableStateOf("") }
    var offer by rememberSaveable { mutableStateOf("") }
    var locationSeeded by rememberSaveable { mutableStateOf(false) }

    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(providerReference) { viewModel.loadProvider(providerReference) }
    LaunchedEffect(state.provider) {
        if (!locationSeeded) {
            state.provider?.locality?.takeIf(String::isNotBlank)?.let {
                location = it
                locationSeeded = true
            }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            isSubmitting = false
        }
    }
    LaunchedEffect(state.createdBookingId) {
        state.createdBookingId?.let { bookingId ->
            isSubmitting = false
            viewModel.consumeCreatedBooking()
            onNavigate(RtcRoute.serviceCentreBooking(bookingId))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text("Request booking", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                state.provider?.let { provider ->
                    Text(provider.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${provider.categoryName} · Starting at ${serviceCentreMoney(provider.startingPrice)}", color = MaterialTheme.colorScheme.primary)
                }
                if (state.loading || state.working || isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
                ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
            }
        }
        item {
            OutlinedTextField(
                value = whenText,
                onValueChange = { whenText = it.take(16) },
                label = { Text("When?") },
                supportingText = { Text("YYYY-MM-DD HH:mm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(240) },
                label = { Text("Where?") },
                supportingText = { Text("Service address or meeting place") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = offer,
                onValueChange = { offer = it.take(12) },
                label = { Text("Your offer") },
                prefix = { Text("R ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text("The provider confirms the request directly in RTC.", style = MaterialTheme.typography.bodySmall)
                val canSubmit = !state.working && !isSubmitting && state.provider != null && whenText.isNotBlank() && location.isNotBlank()
                val providerLabel = state.provider?.displayName?.ifBlank { "Provider" } ?: "Provider"
                Button(
                    onClick = {
                        isSubmitting = true
                        viewModel.createBooking(whenText, location, offer, marketplaceBusinessId, marketplaceOfferingId)
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget),
                ) {
                    Text(
                        if (state.working || isSubmitting) "Confirming Booking…" else "Confirm Booking with $providerLabel"
                    )
                }
            }
        }
    }
}

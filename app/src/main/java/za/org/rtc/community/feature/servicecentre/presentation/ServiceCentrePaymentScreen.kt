package za.org.rtc.community.feature.servicecentre.presentation

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreActorRole
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingStatus
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreBookingViewModel
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ServiceCentrePaymentRoute(
    bookingId: String,
    viewModel: ServiceCentreBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(bookingId) { viewModel.loadDetail(bookingId) }
    DisposableEffect(lifecycleOwner, bookingId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadDetail(bookingId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.checkout) {
        state.checkout?.let { checkout ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(checkout.redirectUrl))
            viewModel.consumeCheckout()
        }
    }
    val booking = state.detail
    val canPay = booking?.actorRole == ServiceCentreActorRole.CUSTOMER && booking.status == ServiceCentreBookingStatus.ACCEPTED_AWAITING_PAYMENT

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text("Secure your date", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("RTC uses Yoco's hosted checkout. Card details are entered on Yoco's secure payment page, not inside RTC Community.")
                ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
                if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        booking?.let { item { ServiceCentreBookingCard(booking, onOpen = {}, onChat = {}) } }
        item {
            when {
                booking?.status == ServiceCentreBookingStatus.CONFIRMED -> Text("Payment confirmed · Date secured", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                canPay && booking != null -> Button(
                    onClick = { viewModel.createCheckout(bookingId) },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget),
                ) { Text("Pay ${serviceCentreMoney(booking.commitmentFeeAmount ?: java.math.BigDecimal.ZERO)} with Yoco") }
                booking != null -> Text("This booking is not awaiting a commitment payment.")
            }
        }
        item {
            Text(
                "Returning from the browser does not confirm payment. RTC waits for Yoco's verified server webhook and refreshes this booking when you return.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

package za.org.rtc.community.feature.servicecentre.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreActorRole
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingStatus
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProvider
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ServiceCentreProviderCard(
    provider: ServiceCentreProvider,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(RtcSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                if (!provider.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = provider.avatarUrl,
                        contentDescription = "${provider.displayName} profile image",
                        modifier = Modifier.size(RtcSize.avatarLarge).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.size(RtcSize.avatarLarge), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(RtcSize.largeIcon))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (provider.verified) {
                            Spacer(Modifier.size(RtcSpacing.compact))
                            Icon(Icons.Filled.Verified, contentDescription = "Verified provider", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RtcSize.inlineIcon))
                        }
                    }
                    Text(provider.categoryName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(provider.distanceMetres?.let(::serviceCentreDistanceLabel) ?: provider.locality, style = MaterialTheme.typography.bodySmall)
            Text("Starting at ${serviceCentreMoney(provider.startingPrice)}", fontWeight = FontWeight.SemiBold)
            val rating = provider.ratingAverage
            val reviews = provider.reviewCount ?: 0
            Text(
                if (rating != null && reviews > 0) "%.1f · %d review%s".format(rating, reviews, if (reviews == 1) "" else "s") else "New provider",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)) {
                Text("Request")
            }
        }
    }
}

@Composable
fun ServiceCentreBookingCard(
    booking: ServiceCentreBooking,
    onOpen: () -> Unit,
    onChat: () -> Unit,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(booking.counterpartyDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = onOpen, label = { Text(serviceCentreStatusLabel(booking.status)) })
            }
            Text(booking.categoryName, color = MaterialTheme.colorScheme.primary)
            Text(serviceCentreDateTime(booking.requestedStartAt), style = MaterialTheme.typography.bodyMedium)
            Text(booking.serviceLocationText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Offer · ${serviceCentreMoney(booking.offerAmount)}", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                onAccept?.let { Button(onClick = it, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Accept") } }
                onDecline?.let { OutlinedButton(onClick = it, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Decline") } }
                onCancel?.let { OutlinedButton(onClick = it, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Cancel") } }
                onComplete?.let { Button(onClick = it, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Complete") } }
            }
            if (booking.status == ServiceCentreBookingStatus.CONFIRMED) {
                Text("Booking Accepted", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                Spacer(Modifier.size(RtcSpacing.compact))
                Text("Chat")
            }
        }
    }
}

@Composable
fun ServiceCentreMessageBanner(message: String?, onDismiss: () -> Unit) {
    if (message.isNullOrBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(RtcSpacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

fun ServiceCentreBooking.primaryActions(): Set<String> = buildSet {
    when {
        actorRole == ServiceCentreActorRole.PROVIDER && status == ServiceCentreBookingStatus.PENDING_PROVIDER -> {
            add("ACCEPT"); add("DECLINE")
        }
        actorRole == ServiceCentreActorRole.CUSTOMER && status == ServiceCentreBookingStatus.PENDING_PROVIDER -> add("CANCEL")
        actorRole == ServiceCentreActorRole.PROVIDER && status == ServiceCentreBookingStatus.CONFIRMED && !requestedStartAt.isAfter(java.time.Instant.now()) -> add("COMPLETE")
    }
}

fun serviceCentreMoney(value: BigDecimal): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA"))
    formatter.currency = java.util.Currency.getInstance("ZAR")
    return formatter.format(value)
}

fun serviceCentreDistanceLabel(distanceMetres: Int): String = when {
    distanceMetres < 1000 -> "$distanceMetres m away"
    else -> "%.1f km away".format(distanceMetres / 1000.0)
}

fun serviceCentreStatusLabel(status: ServiceCentreBookingStatus): String = when (status) {
    ServiceCentreBookingStatus.PENDING_PROVIDER -> "Pending provider"
    ServiceCentreBookingStatus.CONFIRMED -> "Confirmed"
    ServiceCentreBookingStatus.COMPLETED -> "Completed"
    ServiceCentreBookingStatus.DECLINED -> "Declined"
    ServiceCentreBookingStatus.CANCELLED -> "Cancelled"
}

fun serviceCentreDateTime(value: java.time.Instant): String = value
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm"))

package za.org.rtc.community.feature.marketplace.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursEvaluator
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursException
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursInterval
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOpeningStatus
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable

fun MarketplaceLocationCard(
    location: MarketplaceLocation,
    businessName: String,
    onGetDirections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedSchedule by rememberSaveable { mutableStateOf(false) }
    val evaluator = remember { MarketplaceHoursEvaluator() }
    val status = remember(location) { evaluator.evaluate(location) }
    val todaySummary = remember(location) { evaluator.todaySummary(location) }
    val weeklySchedule = remember(location) { evaluator.weeklySchedule(location) }

    val statusContainerColor = when (status) {
        MarketplaceOpeningStatus.OpenNow, MarketplaceOpeningStatus.Open24Hours -> Color(0xFFE8F5E9)
        MarketplaceOpeningStatus.Closed -> Color(0xFFFFEBEE)
        MarketplaceOpeningStatus.ByAppointment -> Color(0xFFE3F2FD)
        MarketplaceOpeningStatus.Unavailable -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusContentColor = when (status) {
        MarketplaceOpeningStatus.OpenNow, MarketplaceOpeningStatus.Open24Hours -> Color(0xFF2E7D32)
        MarketplaceOpeningStatus.Closed -> Color(0xFFC62828)
        MarketplaceOpeningStatus.ByAppointment -> Color(0xFF1565C0)
        MarketplaceOpeningStatus.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Location Header & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = location.label.ifBlank { "Primary Location" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusContainerColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusContentColor)
                        )
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusContentColor,
                        )
                    }
                }
            }

            // Address Details
            val fullAddress = listOfNotNull(location.address, location.locality, location.municipality, location.province)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (fullAddress.isNotBlank()) {
                Text(
                    text = fullAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            location.parkingNote?.takeIf(String::isNotBlank)?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalParking,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Today's Hours Highlight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Today's Hours",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Text(
                    text = todaySummary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (status.isOpen) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Expandable Weekly Schedule Accordion
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expandedSchedule = !expandedSchedule }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expandedSchedule) "Hide weekly schedule" else "View full weekly hours",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (expandedSchedule) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (expandedSchedule) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        weeklySchedule.forEach { dayInfo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (dayInfo.isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = dayInfo.dayName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = if (dayInfo.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (dayInfo.isToday) {
                                        Text(
                                            text = "(Today)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    text = dayInfo.summary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (dayInfo.isToday) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (dayInfo.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Holiday / Single-date exceptions if present
                        if (location.hourExceptions.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Special Holiday Hours:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            location.hourExceptions.forEach { exception ->
                                Text(
                                    text = exception.marketplaceDisplayLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Get Directions Button
            Button(
                onClick = onGetDirections,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Directions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun MarketplaceHoursInterval.marketplaceDisplayLabel(): String {
    val day = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").getOrElse(dayOfWeek) { "Day $dayOfWeek" }
    val stateLabel = when (state) {
        "OPEN" -> listOfNotNull(opensAt, closesAt).joinToString(" – ")
        "OPEN_24_HOURS" -> "Open 24 hours"
        "APPOINTMENT_ONLY" -> "By appointment"
        "CLOSED" -> "Closed"
        else -> "Schedule unavailable"
    }
    return "$day · $stateLabel"
}

private fun MarketplaceHoursException.marketplaceDisplayLabel(): String {
    val stateLabel = when (state) {
        "OPEN" -> listOfNotNull(opensAt, closesAt).joinToString(" – ")
        "OPEN_24_HOURS" -> "Open 24 hours"
        "APPOINTMENT_ONLY" -> "By appointment"
        "CLOSED" -> "Closed"
        else -> "Schedule unavailable"
    }
    return listOfNotNull(date, stateLabel, note?.takeIf(String::isNotBlank)).joinToString(" · ")
}

@Composable
internal fun ReportBusinessDialog(
    businessName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit,
) {
    val reasons = listOf(
        "Incorrect information or address",
        "Inappropriate or offensive content",
        "Fake listing or scam",
        "Permanently closed",
        "Spam or misleading information",
        "Other",
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var detailsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $businessName", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select a reason to flag this business listing for moderation:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it },
                    label = { Text("Additional details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, detailsText) },
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

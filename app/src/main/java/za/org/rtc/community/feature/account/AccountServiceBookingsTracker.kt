package za.org.rtc.community.feature.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.ui.components.RtcCard

internal data class TrackerBookingItem(
    val id: String,
    val providerName: String,
    val serviceTitle: String,
    val dateText: String,
    val status: String,
    val offerAmount: String,
    val locationText: String,
)

@Composable
internal fun BookingStatusTrackerCard(
    onNavigateToBooking: (String) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }

    val sampleBookings = remember {
        listOf(
            TrackerBookingItem(
                id = "bk-101",
                providerName = "Apex Electrical & Plumbing Services",
                serviceTitle = "Solar Inverter & DB Board Inspection",
                dateText = "Tomorrow at 10:00 AM",
                status = "CONFIRMED",
                offerAmount = "R 650.00",
                locationText = "Main Rd Corridor, Sector 4",
            ),
            TrackerBookingItem(
                id = "bk-102",
                providerName = "Kimberley Cleaning Specialists",
                serviceTitle = "Deep Carpet & Upholstery Cleaning",
                dateText = "Friday at 09:00 AM",
                status = "PENDING",
                offerAmount = "R 420.00",
                locationText = "Block B, Unit 14",
            ),
            TrackerBookingItem(
                id = "bk-103",
                providerName = "Northern Cape Auto Care",
                serviceTitle = "Annual Vehicle Inspection & Oil Change",
                dateText = "3 Sep 2026 at 14:30 PM",
                status = "COMPLETED",
                offerAmount = "R 850.00",
                locationText = "Industrial Zone, Lot 12",
            ),
        )
    }

    val filteredBookings = remember(sampleBookings, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> sampleBookings.filter { it.status == "PENDING" }
            "CONFIRMED" -> sampleBookings.filter { it.status == "CONFIRMED" }
            "COMPLETED" -> sampleBookings.filter { it.status == "COMPLETED" }
            else -> sampleBookings
        }
    }

    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Booking Status Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${sampleBookings.size} Requests",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    "ALL" to "All",
                    "PENDING" to "Pending",
                    "CONFIRMED" to "Confirmed",
                    "COMPLETED" to "Completed",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = (selectedFilter == key),
                        onClick = { selectedFilter = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (filteredBookings.isEmpty()) {
                Text(
                    text = "No service requests match the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                filteredBookings.forEach { booking ->
                    BookingTimelineCard(booking = booking, onNavigateToBooking = onNavigateToBooking)
                }
            }
        }
    }
}

@Composable
private fun BookingTimelineCard(
    booking: TrackerBookingItem,
    onNavigateToBooking: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToBooking("account/service-centre/booking/${booking.id}") },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.serviceTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = booking.providerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (booking.status) {
                        "PENDING" -> MaterialTheme.colorScheme.tertiaryContainer
                        "CONFIRMED" -> MaterialTheme.colorScheme.primaryContainer
                        "COMPLETED" -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        text = booking.status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = when (booking.status) {
                            "PENDING" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "CONFIRMED" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "COMPLETED" -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "📍 ${booking.locationText} · 🕒 ${booking.dateText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = booking.offerAmount,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Timeline Progress Visualizer
            BookingTimelineVisualizer(status = booking.status)

            OutlinedButton(
                onClick = { onNavigateToBooking("account/service-centre/booking/${booking.id}") },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("View Timeline & Chat", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun BookingTimelineVisualizer(status: String) {
    val currentStepIndex = when (status) {
        "PENDING" -> 1
        "CONFIRMED" -> 2
        "COMPLETED" -> 3
        else -> 1
    }

    val steps = listOf(
        "Requested",
        "Confirmed",
        "Completed",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEachIndexed { index, label ->
                val stepNum = index + 1
                val isDone = stepNum <= currentStepIndex
                val isCurrent = stepNum == currentStepIndex

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        } else {
                            Text(
                                text = stepNum.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }

                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (stepNum < currentStepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                    )
                }
            }
        }
    }
}

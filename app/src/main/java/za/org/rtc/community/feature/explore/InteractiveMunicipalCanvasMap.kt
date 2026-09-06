package za.org.rtc.community.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcCivicGold
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

internal enum class MapLayerFilter(val label: String) {
    ALL("All Issues"),
    IN_PROGRESS("In Progress"),
    CRITICAL_HAZARDS("Critical Hazards"),
    FACILITIES("Municipal Facilities"),
}

internal data class MunicipalFacilityMarker(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val hours: String,
    val xRatio: Float,
    val yRatio: Float,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

internal sealed interface SelectedMapPin {
    data class ReportPin(val report: PublicReport) : SelectedMapPin
    data class FacilityPin(val facility: MunicipalFacilityMarker) : SelectedMapPin
}

private val municipalFacilities = listOf(
    MunicipalFacilityMarker("fac_1", "City Hall & Municipal Offices", "Government Hub", "1 Civic Square, Sector 1", "Mon-Fri 08:00 - 16:30", 0.50f, 0.35f, Icons.Default.AccountBalance),
    MunicipalFacilityMarker("fac_2", "RTC Public Library", "Public Library", "42 Library Walk, Sector 1", "Mon-Sat 09:00 - 18:00", 0.28f, 0.52f, Icons.Default.Book),
    MunicipalFacilityMarker("fac_3", "Municipal Eco-Park Grounds", "Park Zone", "Community Park North, Sector 3", "Open Daily 06:00 - 19:00", 0.72f, 0.25f, Icons.Default.Park),
    MunicipalFacilityMarker("fac_4", "Civic Recycling & Waste Hub", "Recycling Center", "8 Industrial Ring Rd, Sector 4", "Mon-Fri 07:30 - 17:00", 0.82f, 0.72f, Icons.Default.Recycling),
)

internal fun reportToCoordinates(report: PublicReport): Pair<Float, Float> {
    return when {
        report.id.contains("001") || report.publicLocationLabel.contains("Main", ignoreCase = true) -> Pair(0.35f, 0.42f)
        report.id.contains("002") || report.publicLocationLabel.contains("Park", ignoreCase = true) -> Pair(0.68f, 0.28f)
        report.id.contains("003") || report.publicLocationLabel.contains("Recreation", ignoreCase = true) -> Pair(0.24f, 0.65f)
        report.id.contains("004") || report.publicLocationLabel.contains("Pine", ignoreCase = true) -> Pair(0.78f, 0.58f)
        report.id.contains("005") || report.publicLocationLabel.contains("Hub", ignoreCase = true) -> Pair(0.52f, 0.72f)
        else -> {
            val hash = Math.abs(report.id.hashCode())
            val x = 0.20f + ((hash % 55) / 100f)
            val y = 0.20f + (((hash / 7) % 55) / 100f)
            Pair(x, y)
        }
    }
}

@Composable
internal fun InteractiveMunicipalCanvasMap(
    verifiedReports: List<PublicReport>,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeFilter by remember { mutableStateOf(MapLayerFilter.ALL) }
    var selectedPin by remember { mutableStateOf<SelectedMapPin?>(null) }
    var userUpvotes by remember { mutableStateOf(setOf<String>()) }

    // Map gesture & view tool state
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showLegend by remember { mutableStateOf(false) }

    // STRICT MANDATE: Map displays geotags ONLY for Verified Public Reports
    val verifiedOnly = remember(verifiedReports) {
        verifiedReports.filter { it.verified || it.verificationReason != null }
    }

    val visibleReports = remember(verifiedOnly, activeFilter) {
        when (activeFilter) {
            MapLayerFilter.ALL -> verifiedOnly
            MapLayerFilter.IN_PROGRESS -> verifiedOnly.filter { it.status == PublicReportStatus.IN_PROGRESS }
            MapLayerFilter.CRITICAL_HAZARDS -> verifiedOnly.filter { it.urgency == PublicReportUrgency.CRITICAL }
            MapLayerFilter.FACILITIES -> emptyList()
        }
    }

    val visibleFacilities = remember(activeFilter) {
        if (activeFilter == MapLayerFilter.FACILITIES || activeFilter == MapLayerFilter.ALL) {
            municipalFacilities
        } else {
            emptyList()
        }
    }

    RtcCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text("Community Map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                RtcStatusChip("${verifiedOnly.size} verified geotags", RtcStatusTone.SUCCESS)
            }

            Text(
                "Interactive community map displaying verified public report geotags and local municipal facilities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Interactive Quick-Filter Chips with Tactile Scale Animation
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MapLayerFilter.entries, key = { it.name }) { filter ->
                    val isSelected = activeFilter == filter
                    val chipScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "chipScaleAnimation",
                    )

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeFilter = filter
                            selectedPin = null
                        },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        },
                    )
                }
            }

            // Stylized Canvas Map Container Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(RtcSpacing.standard))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(RtcSpacing.standard),
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (zoomLevel * zoom).coerceIn(1.0f, 3.0f)
                            val maxPanX = (newZoom - 1f) * 160f
                            val maxPanY = (newZoom - 1f) * 130f
                            val newPanX = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                            val newPanY = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            zoomLevel = newZoom
                            panOffset = if (newZoom == 1.0f) Offset.Zero else Offset(newPanX, newPanY)
                        }
                    },
            ) {
                // Interactive Transformable Canvas Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomLevel
                            scaleY = zoomLevel
                            translationX = panOffset.x
                            translationY = panOffset.y
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                ) {
                    // Background Styling Colors
                    val parkColor = Color(0xFFC8E6C9)
                    val parkDarkColor = Color(0xFF1B382B)
                    val waterColor = Color(0xFF90CAF9)
                    val waterDarkColor = Color(0xFF152A42)
                    val roadColor = Color(0xFFFFFFFF)
                    val roadBorderColor = Color(0xFFB0BEC5)
                    val landColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // 1. Base Land
                        drawRect(color = landColor)

                        // 2. Park Zones
                        val parkPath1 = Path().apply {
                            moveTo(w * 0.60f, h * 0.10f)
                            cubicTo(w * 0.75f, h * 0.08f, w * 0.90f, h * 0.20f, w * 0.85f, h * 0.38f)
                            cubicTo(w * 0.80f, h * 0.48f, w * 0.65f, h * 0.40f, w * 0.62f, h * 0.25f)
                            close()
                        }
                        drawPath(parkPath1, color = if (isDark) parkDarkColor else parkColor)

                        val parkPath2 = Path().apply {
                            moveTo(w * 0.05f, h * 0.65f)
                            cubicTo(w * 0.20f, h * 0.60f, w * 0.35f, h * 0.75f, w * 0.28f, h * 0.92f)
                            cubicTo(w * 0.15f, h * 0.95f, w * 0.02f, h * 0.82f, w * 0.05f, h * 0.65f)
                            close()
                        }
                        drawPath(parkPath2, color = if (isDark) parkDarkColor else parkColor)

                        // 3. Civic Waterway
                        val riverPath = Path().apply {
                            moveTo(0f, h * 0.20f)
                            cubicTo(w * 0.30f, h * 0.22f, w * 0.45f, h * 0.55f, w * 0.80f, h * 0.78f)
                            cubicTo(w * 0.90f, h * 0.85f, w * 0.98f, h * 0.90f, w, h * 0.92f)
                        }
                        drawPath(
                            path = riverPath,
                            color = if (isDark) waterDarkColor else waterColor,
                            style = Stroke(width = 24.dp.toPx()),
                        )

                        // 4. Roadways
                        val mainRoad = Path().apply {
                            moveTo(w * 0.10f, 0f)
                            lineTo(w * 0.10f, h)
                            moveTo(w * 0.50f, 0f)
                            lineTo(w * 0.50f, h)
                            moveTo(0f, h * 0.45f)
                            lineTo(w, h * 0.45f)
                            moveTo(0f, h * 0.75f)
                            lineTo(w, h * 0.75f)
                        }
                        drawPath(mainRoad, color = roadBorderColor, style = Stroke(width = 8.dp.toPx()))
                        drawPath(mainRoad, color = roadColor, style = Stroke(width = 5.dp.toPx()))

                        // 5. Resident Location Indicator (Pulsing Radar Pin)
                        val resX = w * 0.46f
                        val resY = h * 0.48f
                        drawCircle(color = Color(0x332196F3), radius = 18.dp.toPx(), center = Offset(resX, resY))
                        drawCircle(color = Color(0xFF2196F3), radius = 6.dp.toPx(), center = Offset(resX, resY))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(resX, resY))
                    }

                    // Resident Badge
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((0.46f * 800).toInt(), (0.48f * 500).toInt()) }
                            .padding(bottom = 12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.semantics { contentDescription = "Your current resident location" },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(10.dp))
                                Text("You", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Plot Geotagged Pins for Verified Reports
                    visibleReports.forEach { report ->
                        val (xR, yR) = reportToCoordinates(report)
                        val pinColor = when (report.urgency) {
                            PublicReportUrgency.CRITICAL -> Color(0xFFE53935)
                            PublicReportUrgency.HIGH -> Color(0xFFFB8C00)
                            else -> when (report.status) {
                                PublicReportStatus.IN_PROGRESS -> Color(0xFFFB8C00)
                                PublicReportStatus.COMPLETED, PublicReportStatus.CLOSED -> Color(0xFF43A047)
                                else -> Color(0xFF1E88E5)
                            }
                        }

                        val isSelected = (selectedPin as? SelectedMapPin.ReportPin)?.report?.id == report.id

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset((xR * 320).dp.roundToPx(), (yR * 220).dp.roundToPx()) }
                                    .clip(CircleShape)
                                    .clickable { selectedPin = SelectedMapPin.ReportPin(report) },
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color.White else pinColor,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, pinColor) else null,
                                    modifier = Modifier.size(if (isSelected) 34.dp else 28.dp),
                                    shadowElevation = 4.dp,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (report.urgency == PublicReportUrgency.CRITICAL) Icons.Default.Warning else Icons.Default.LocationOn,
                                            contentDescription = "Report: ${report.title}",
                                            tint = if (isSelected) pinColor else Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Plot Municipal Facilities Pins
                    visibleFacilities.forEach { facility ->
                        val isSelected = (selectedPin as? SelectedMapPin.FacilityPin)?.facility?.id == facility.id

                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset((facility.xRatio * 320).dp.roundToPx(), (facility.yRatio * 220).dp.roundToPx()) }
                                    .clip(CircleShape)
                                    .clickable { selectedPin = SelectedMapPin.FacilityPin(facility) },
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(if (isSelected) 34.dp else 28.dp),
                                    shadowElevation = 4.dp,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = facility.icon,
                                            contentDescription = facility.name,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Map Navigation Tools Overlay (Zoom, Center, Legend, Compass)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp),
                    ) {
                        // Zoom In
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel + 0.35f).coerceAtMost(3.0f) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                        }

                        // Zoom Out
                        IconButton(
                            onClick = {
                                zoomLevel = (zoomLevel - 0.35f).coerceAtLeast(1.0f)
                                if (zoomLevel == 1.0f) panOffset = Offset.Zero
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                        }

                        // Reset View / Center Focus
                        IconButton(
                            onClick = {
                                zoomLevel = 1.0f
                                panOffset = Offset.Zero
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.CenterFocusStrong,
                                contentDescription = "Reset map view",
                                tint = if (zoomLevel != 1.0f || panOffset != Offset.Zero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Map Legend Toggle
                        IconButton(
                            onClick = { showLegend = !showLegend },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Toggle map legend",
                                tint = if (showLegend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Compass / Zoom Level Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.CompassCalibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(
                            text = "N · ${String.format("%.1fx", zoomLevel)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Map Legend Overlay Sheet
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLegend,
                        enter = fadeIn() + slideInVertically { -it / 2 },
                        exit = fadeOut() + slideOutVertically { -it / 2 },
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 6.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Map Legend", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { showLegend = false }, modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close legend", modifier = Modifier.size(12.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = CircleShape, color = Color(0xFFE53935), modifier = Modifier.size(10.dp)) {}
                                    Text("Critical Hazard", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = CircleShape, color = Color(0xFFFB8C00), modifier = Modifier.size(10.dp)) {}
                                    Text("In Progress", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = CircleShape, color = Color(0xFF43A047), modifier = Modifier.size(10.dp)) {}
                                    Text("Resolved Issue", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(10.dp)) {}
                                    Text("Municipal Facility", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Animated Detail Card Overlay for Selected Marker
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedPin != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                selectedPin?.let { pin ->
                    Surface(
                        shape = RoundedCornerShape(RtcSpacing.standard),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(RtcSpacing.standard),
                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when (pin) {
                                    is SelectedMapPin.ReportPin -> {
                                        val r = pin.report
                                        RtcStatusChip(
                                            text = if (r.urgency == PublicReportUrgency.CRITICAL) "Critical Hazard" else r.status.name.replace("_", " "),
                                            tone = if (r.urgency == PublicReportUrgency.CRITICAL) RtcStatusTone.PROTECTED else RtcStatusTone.NEUTRAL,
                                        )
                                    }
                                    is SelectedMapPin.FacilityPin -> {
                                        RtcStatusChip(
                                            text = pin.facility.category,
                                            tone = RtcStatusTone.NEUTRAL,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { selectedPin = null },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close pin card", modifier = Modifier.size(16.dp))
                                }
                            }

                            when (pin) {
                                is SelectedMapPin.ReportPin -> {
                                    val r = pin.report
                                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(r.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Text("Verified Report · ${r.publicLocationLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val hasVoted = r.id in userUpvotes
                                        val currentVotes = r.thumbsUpCount + if (hasVoted) 1 else 0

                                        OutlinedButton(
                                            onClick = {
                                                userUpvotes = if (hasVoted) userUpvotes - r.id else userUpvotes + r.id
                                            },
                                            modifier = Modifier.height(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (hasVoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("$currentVotes Upvotes", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Button(
                                            onClick = { onOpenReport(r.id) },
                                            modifier = Modifier.height(36.dp),
                                        ) {
                                            Text("View Timeline", style = MaterialTheme.typography.labelSmall)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                is SelectedMapPin.FacilityPin -> {
                                    val f = pin.facility
                                    Text(f.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(f.address, style = MaterialTheme.typography.bodySmall)
                                    Text("Hours: ${f.hours}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

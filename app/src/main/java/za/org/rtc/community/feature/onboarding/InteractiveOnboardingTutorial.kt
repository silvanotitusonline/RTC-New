package za.org.rtc.community.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import za.org.rtc.community.navigation.RtcRoute

private val SnapshotOpenColor = Color(0xFFF59E0B)
private val SnapshotInProgressColor = Color(0xFF2563EB)
private val SnapshotResolvedColor = Color(0xFF22C55E)

@Composable
fun InteractiveOnboardingTutorial(
    onDismiss: () -> Unit,
    onNavigateToFeature: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 2

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(16.dp)
                .testTag("interactive_onboarding_tutorial"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    // Top Bar: Step Indicator Pill + Skip button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Step ${currentStep + 1} of $totalSteps",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("onboarding_skip_button"),
                        ) {
                            Text(
                                text = "Skip Tutorial",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated Step Content
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> -width } + fadeOut()
                                    )
                                } else {
                                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> width } + fadeOut()
                                    )
                                }
                            },
                            label = "onboarding_step_transition",
                        ) { step ->
                            when (step) {
                                0 -> CommunitySnapshotTutorialStep(
                                    onJump = { onNavigateToFeature(RtcRoute.HOME) },
                                )
                                1 -> MarketplaceTutorialStep(
                                    onJump = { onNavigateToFeature(RtcRoute.MARKETPLACE_HOME) },
                                )
                                else -> MarketplaceTutorialStep(
                                    onJump = { onNavigateToFeature(RtcRoute.MARKETPLACE_HOME) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Indicator Dots
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(totalSteps) { index ->
                            val isSelected = index == currentStep
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(
                                        width = if (isSelected) 24.dp else 8.dp,
                                        height = 8.dp,
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Navigation Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep -= 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("onboarding_prev_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Previous")
                            }
                        }

                        if (currentStep < totalSteps - 1) {
                            Button(
                                onClick = { currentStep += 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("onboarding_next_button"),
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("onboarding_finish_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get Started")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 1: Community Snapshot Tutorial Step
 */
@Composable
private fun CommunitySnapshotTutorialStep(
    onJump: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_community_snapshot"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = "CIVIC PULSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Community Snapshot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Text(
            text = "Your real-time community dashboard on the Home screen. Visualize verified municipal reports, active maintenance dispatches, and resolved community cases.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Interactive Donut Chart Simulation
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Interactive mini donut canvas
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(130.dp)) {
                        val stroke = 22.dp.toPx()
                        val diameter = size.minDimension - stroke
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        val arcSize = Size(diameter, diameter)

                        val gap = 3f
                        val openSweep = (0.24f * 360f - gap).coerceAtLeast(1f)
                        val inProgressSweep = (0.18f * 360f - gap).coerceAtLeast(1f)
                        val resolvedSweep = (0.58f * 360f - gap).coerceAtLeast(1f)

                        val openAlpha = if (selectedFilter == "All" || selectedFilter == "Open") 1f else 0.25f
                        val inProgAlpha = if (selectedFilter == "All" || selectedFilter == "In Progress") 1f else 0.25f
                        val resolvedAlpha = if (selectedFilter == "All" || selectedFilter == "Resolved") 1f else 0.25f

                        var start = -90f
                        // 1. Open
                        drawArc(
                            color = SnapshotOpenColor.copy(alpha = openAlpha),
                            startAngle = start,
                            sweepAngle = openSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        start += openSweep + gap

                        // 2. In Progress
                        drawArc(
                            color = SnapshotInProgressColor.copy(alpha = inProgAlpha),
                            startAngle = start,
                            sweepAngle = inProgressSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        start += inProgressSweep + gap

                        // 3. Resolved
                        drawArc(
                            color = SnapshotResolvedColor.copy(alpha = resolvedAlpha),
                            startAngle = start,
                            sweepAngle = resolvedSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (selectedFilter) {
                                "Open" -> "24"
                                "In Progress" -> "18"
                                "Resolved" -> "58"
                                else -> "100"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = when (selectedFilter) {
                                "Open" -> "Open"
                                "In Progress" -> "Active"
                                "Resolved" -> "Fixed"
                                else -> "Total"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "Tap a category below to test interactive inspection:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Interactive Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    listOf("All", "Open", "In Progress", "Resolved").forEach { label ->
                        FilterChip(
                            selected = selectedFilter == label,
                            onClick = { selectedFilter = label },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .minimumInteractiveComponentSize(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (label) {
                                    "Open" -> SnapshotOpenColor.copy(alpha = 0.2f)
                                    "In Progress" -> SnapshotInProgressColor.copy(alpha = 0.2f)
                                    "Resolved" -> SnapshotResolvedColor.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                        )
                    }
                }

                // Dynamic Insight Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = when (selectedFilter) {
                                "Open" -> "⚠️ 24 Open Reports"
                                "In Progress" -> "🔧 18 Maintenance Dispatches"
                                "Resolved" -> "✅ 58 Community Issues Resolved"
                                else -> "📊 100 Total Tracked Incidents"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (selectedFilter) {
                                "Open" -> SnapshotOpenColor
                                "In Progress" -> SnapshotInProgressColor
                                "Resolved" -> SnapshotResolvedColor
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                        Text(
                            text = when (selectedFilter) {
                                "Open" -> "Logged by residents in Postmasburg sectors. Awaiting municipal crew assignment."
                                "In Progress" -> "Active work teams dispatched for road resurfacing and water valve repairs."
                                "Resolved" -> "Infrastructure restored with before-and-after photo verification by inspectors."
                                else -> "58% overall resolution rate this cycle. The snapshot automatically syncs on Home."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Jump to Feature Action
        OutlinedButton(
            onClick = onJump,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("onboarding_jump_feature_button"),
        ) {
            Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Jump to Home Snapshot")
        }
    }
}

/**
 * Step 2: Marketplace Tutorial Step
 */
@Composable
private fun MarketplaceTutorialStep(
    onJump: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("Services") }
    var isSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_step_marketplace"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = "LOCAL COMMERCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Community Marketplace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Text(
            text = "Discover and support local Tsantsabane businesses, hire trusted local artisans, find fresh farm produce, or register your own business enterprise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Interactive Category Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Tap a category to preview verified listings:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("Services", "Artisans", "Produce", "Repairs").forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
            }
        }

        // Dynamic Interactive Listing Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (selectedCategory) {
                                    "Artisans" -> "Tsantsabane Craft Guild"
                                    "Produce" -> "Kalahari Greens Market"
                                    "Repairs" -> "Postmasburg Auto & Solar"
                                    else -> "Northern Cape Electrical"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified business",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = when (selectedCategory) {
                                "Artisans" -> "Handmade crafts, pottery & beadwork"
                                "Produce" -> "Farm fresh organic fruit, veg & honey"
                                "Repairs" -> "Vehicle diagnostics & solar installs"
                                else -> "Certified residential wiring & maintenance"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Interactive Save / Bookmark Heart Toggle
                    IconButton(
                        onClick = { isSaved = !isSaved },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Save listing",
                            tint = if (isSaved) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = when (selectedCategory) {
                                "Artisans" -> "5.0 (19)"
                                "Produce" -> "4.8 (42)"
                                "Repairs" -> "4.7 (28)"
                                else -> "4.9 (34)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = when (selectedCategory) {
                                "Artisans" -> "New Town • 2.1 km"
                                "Produce" -> "Agricultural Plots • 3.4 km"
                                "Repairs" -> "Industrial Zone • 0.9 km"
                                else -> "Postmasburg Central • 1.2 km"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(visible = isSaved) {
                    Surface(
                        color = Color(0xFFE11D48).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "❤️ Saved to your personal bookmarks in Account > Saved businesses!",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFBE123C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // Jump to Marketplace Action
        OutlinedButton(
            onClick = onJump,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("onboarding_jump_feature_button"),
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Explore Full Marketplace")
        }
    }
}

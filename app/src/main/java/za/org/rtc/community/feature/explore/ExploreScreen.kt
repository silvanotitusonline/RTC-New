package za.org.rtc.community.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import za.org.rtc.community.ui.animation.RtcMotionPatterns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

import za.org.rtc.community.feature.publicreports.data.PublicReportMockData
import za.org.rtc.community.feature.publicreports.domain.PublicReport

@Composable
internal fun ExploreScreen(
    projects: List<ProjectRecord>,
    opportunities: List<OpportunityRecord>,
    notices: List<OfficialNotice>,
    events: List<za.org.rtc.community.feature.events.domain.CommunityEvent>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onToggleRsvp: (String) -> Unit,
    reports: List<PublicReport> = PublicReportMockData.getSampleReports(),
    onOpenReport: (String) -> Unit = {},
) {
    val publishedNoticeCount = notices.count { it.status == NoticeStatus.PUBLISHED }
    var activeTab by remember { mutableStateOf(0) }

    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item {
                RtcSectionHeader(
                    title = "Explore",
                    subtitle = "Notices, projects, and opportunities.",
                )
            }
            item {
                TabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Overview", style = MaterialTheme.typography.titleSmall) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Events Calendar", style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }
            item {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = RtcMotionPatterns.lateralTransitionSpec(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount < -40 && activeTab == 0) {
                                    activeTab = 1
                                } else if (dragAmount > 40 && activeTab == 1) {
                                    activeTab = 0
                                }
                            }
                        },
                    label = "ExploreTabLateral",
                ) { tab ->
                    if (tab == 0) {
                        if (isRefreshing && projects.isEmpty() && opportunities.isEmpty() && notices.isEmpty()) {
                            ExploreSkeletonLoader()
                        } else if (projects.isEmpty() && opportunities.isEmpty() && notices.isEmpty() && reports.isEmpty()) {
                            ExploreLocalizedEmptyState(onRefresh = onRefresh)
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
                            ) {
                                RtcCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onOpenDirectory("notices") },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ExploreIcon(Icons.Filled.Campaign)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
                                        ) {
                                            Text("Community Notices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Read official updates published for the community.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        RtcStatusChip("$publishedNoticeCount published", RtcStatusTone.NEUTRAL)
                                    }
                                }
                                InteractiveMunicipalCanvasMap(
                                    verifiedReports = reports,
                                    onOpenReport = onOpenReport,
                                )
                                RtcCard(modifier = Modifier.fillMaxWidth()) {
                                    Text("Projects and Opportunities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Track current community work and find ways to participate.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ExploreActionRow(
                                        title = "Projects",
                                        subtitle = "Current community projects",
                                        count = "${projects.size} available",
                                        icon = Icons.Filled.Assignment,
                                        onClick = { onOpenDirectory("projects") },
                                    )
                                    ExploreActionRow(
                                        title = "Opportunities",
                                        subtitle = "Jobs, support, and learning",
                                        count = "${opportunities.size} open",
                                        icon = Icons.Filled.Article,
                                        onClick = { onOpenDirectory("opportunities") },
                                    )
                                }
                            }
                        }
                    } else {
                        MonthlyEventsCalendar(
                            events = events,
                            onToggleRsvp = onToggleRsvp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyEventsCalendar(
    events: List<za.org.rtc.community.feature.events.domain.CommunityEvent>,
    onToggleRsvp: (String) -> Unit,
) {
    var selectedDay by remember { mutableStateOf(4) } // Default to Sept 4, 2026
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf("All", "Civic", "Environment", "Youth", "Safety", "Culture")

    val septemberEvents = remember(events) {
        events.filter { event ->
            runCatching {
                val zdt = event.startsAt.atZone(java.time.ZoneId.of(event.timeZone))
                zdt.year == 2026 && zdt.monthValue == 9 && event.state == za.org.rtc.community.feature.events.domain.CommunityEventState.PUBLISHED
            }.getOrDefault(false)
        }
    }

    val filteredEvents = remember(septemberEvents, selectedCategory, searchQuery) {
        septemberEvents.filter { event ->
            (selectedCategory == "All" || event.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || event.title.contains(searchQuery, ignoreCase = true) || event.description.contains(searchQuery, ignoreCase = true))
        }
    }

    val daysWithEvents = remember(filteredEvents) {
        filteredEvents.mapNotNull { event ->
            runCatching {
                event.startsAt.atZone(java.time.ZoneId.of(event.timeZone)).dayOfMonth
            }.getOrNull()
        }.toSet()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "September 2026",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            RtcStatusChip(
                text = "${septemberEvents.size} active events",
                tone = RtcStatusTone.PROTECTED,
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search events...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories, key = { it }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            weekdays.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        val firstDayOffset = 2 // September 2026 starts on Tuesday
        val totalDays = 30
        val gridSlots = firstDayOffset + totalDays
        val rows = (gridSlots + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (c in 0..6) {
                        val slotIndex = r * 7 + c
                        val dayNumber = slotIndex - firstDayOffset + 1
                        if (dayNumber in 1..totalDays) {
                            val isSelected = selectedDay == dayNumber
                            val hasEvent = daysWithEvents.contains(dayNumber)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (dayNumber == 4) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .clickable { selectedDay = dayNumber },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || dayNumber == 4) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasEvent) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.error
                                                )
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        val selectedDayEvents = remember(filteredEvents, selectedDay) {
            filteredEvents.filter { event ->
                runCatching {
                    event.startsAt.atZone(java.time.ZoneId.of(event.timeZone)).dayOfMonth == selectedDay
                }.getOrDefault(false)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Events on September $selectedDay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (selectedDayEvents.isNotEmpty()) {
                Text(
                    text = "${selectedDayEvents.size} scheduled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (selectedDayEvents.isEmpty()) {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No events scheduled for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            selectedDayEvents.forEach { event ->
                RtcCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = event.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = formatEventTime(event),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = event.venueLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }

                            FilterChip(
                                selected = event.isRsvped,
                                onClick = { onToggleRsvp(event.id) },
                                label = {
                                    Text(if (event.isRsvped) "✓ Attending (${event.rsvpCount})" else "RSVP (${event.rsvpCount})")
                                },
                                leadingIcon = {
                                    if (event.isRsvped) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatEventTime(event: za.org.rtc.community.feature.events.domain.CommunityEvent): String {
    return runCatching {
        val zdt = event.startsAt.atZone(java.time.ZoneId.of(event.timeZone))
        val hour = zdt.hour.toString().padStart(2, '0')
        val minute = zdt.minute.toString().padStart(2, '0')
        val ezdt = event.endsAt.atZone(java.time.ZoneId.of(event.timeZone))
        val ehour = ezdt.hour.toString().padStart(2, '0')
        val eminute = ezdt.minute.toString().padStart(2, '0')
        "$hour:$minute - $ehour:$eminute"
    }.getOrDefault("18:00 - 20:00")
}

@Composable
private fun ExploreActionRow(
    title: String,
    subtitle: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RtcSize.minimumTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreIcon(icon)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RtcStatusChip(count, RtcStatusTone.NEUTRAL)
    }
}

@Composable
private fun ExploreIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(RtcSize.avatarStandard),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RtcSize.actionIcon),
            )
        }
    }
}

@Composable
fun ExploreSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
    ) {
        repeat(3) {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(RtcSpacing.compact),
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(RtcSize.avatarStandard),
                    ) {}
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(0.6f).height(18.dp),
                        ) {}
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(0.85f).height(14.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreLocalizedEmptyState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RtcCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = RtcSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "No Community Updates Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "New notices, municipal projects, and local opportunities will appear here once published.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = RtcSpacing.standard),
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Check for Updates")
            }
        }
    }
}


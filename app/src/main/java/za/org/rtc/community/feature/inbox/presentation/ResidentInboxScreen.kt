package za.org.rtc.community.feature.inbox.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import za.org.rtc.community.feature.inbox.domain.ResidentInboxItem
import za.org.rtc.community.feature.inbox.domain.ResidentInboxTab
import za.org.rtc.community.ui.animation.RtcMotionPatterns
import za.org.rtc.community.ui.components.ResidentPullToRefresh
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ResidentInboxScreen(
    viewModel: ResidentInboxViewModel = hiltViewModel(),
    onOpenRoute: (String) -> Unit = {},
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    ResidentPullToRefresh(
        isRefreshing = state.loading,
        onRefresh = { viewModel.load() },
    ) {
        RtcScreenScaffold {
            item {
                RtcSectionHeader(
                    title = "Inbox",
                    subtitle = "Official notices and community updates.",
                    trailing = {
                        if (state.items.any { !it.isRead }) {
                            TextButton(onClick = viewModel::markAllRead) {
                                Icon(Icons.Filled.MarkEmailRead, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Mark all read")
                            }
                        }
                    },
                )
            }
            val tabIndex = if (state.tab == ResidentInboxTab.UPDATES) 0 else 1
            item {
                PrimaryTabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { viewModel.load(ResidentInboxTab.UPDATES) },
                        text = { Text("Updates") },
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { viewModel.load(ResidentInboxTab.MESSAGES) },
                        text = { Text("Messages") },
                    )
                }
            }
            item {
                AnimatedContent(
                    targetState = tabIndex,
                    transitionSpec = RtcMotionPatterns.lateralTransitionSpec(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount < -40 && tabIndex == 0) {
                                    viewModel.load(ResidentInboxTab.MESSAGES)
                                } else if (dragAmount > 40 && tabIndex == 1) {
                                    viewModel.load(ResidentInboxTab.UPDATES)
                                }
                            }
                        },
                    label = "InboxTabLateral",
                ) { activeIdx ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
                    ) {
                        if (state.items.isEmpty() && !state.loading) {
                            RtcEmptyState(
                                title = "Your inbox is clear",
                                message = if (activeIdx == 0) "No new updates or notices." else "No active support tickets.",
                            )
                        } else {
                            state.items.forEach { item ->
                                InboxItemCard(
                                    item = item,
                                    onOpen = {
                                        viewModel.markRead(item)
                                        onOpenRoute(item.route)
                                    },
                                )
                            }
                        }
                        state.message?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private val inboxTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@Composable
private fun InboxItemCard(item: ResidentInboxItem, onOpen: () -> Unit) {
    RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (!item.isRead) {
                    RtcStatusChip("New", RtcStatusTone.SUCCESS)
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = try { inboxTimeFormatter.format(item.occurredAt) } catch (_: Exception) { "" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

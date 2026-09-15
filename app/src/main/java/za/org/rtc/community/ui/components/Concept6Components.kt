package za.org.rtc.community.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import za.org.rtc.community.core.CaseStage
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.ui.theme.RtcCivicGold
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcElevation
import za.org.rtc.community.ui.theme.LocalRtcContentDensity
import za.org.rtc.community.ui.theme.RtcRadius
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing
import za.org.rtc.community.ui.theme.RtcStroke
import za.org.rtc.community.ui.theme.metrics

@Composable
fun RtcScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    density: RtcContentDensity = RtcContentDensity.RESIDENT_COMFORTABLE,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val metrics = density.metrics()
    CompositionLocalProvider(
        LocalRtcContentDensity provides density,
        LocalLazyListState provides listState,
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
        ) { insets ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = metrics.contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = metrics.outerPadding,
                        end = metrics.outerPadding,
                        top = insets.calculateTopPadding() + RtcSpacing.standard,
                        bottom = insets.calculateBottomPadding() + RtcSpacing.section,
                    ),
                    verticalArrangement = Arrangement.spacedBy(metrics.listGap),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun RtcCard(
    modifier: Modifier = Modifier,
    protected: Boolean = false,
    onClick: (() -> Unit)? = null,
    density: RtcContentDensity? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = (density ?: LocalRtcContentDensity.current).metrics()
    val outline = if (protected) RtcCivicGold.copy(alpha = .42f) else MaterialTheme.colorScheme.outlineVariant
    val cardBorder = border ?: BorderStroke(RtcStroke.hairline, outline)
    val clickableModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    val targetModifier = if (onClick != null) modifier.sizeIn(minHeight = RtcSize.minimumTouchTarget) else modifier
    Card(
        modifier = targetModifier.fillMaxWidth().then(clickableModifier),
        shape = RoundedCornerShape(RtcRadius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = RtcElevation.flat),
    ) {
        Column(
            modifier = Modifier.padding(metrics.cardPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.groupGap),
            content = content,
        )
    }
}

@Composable
fun RtcStatusChip(text: String, tone: RtcStatusTone = RtcStatusTone.NEUTRAL) {
    val color = when (tone) {
        RtcStatusTone.SUCCESS -> MaterialTheme.colorScheme.primary
        RtcStatusTone.PROTECTED -> RtcCivicGold
        RtcStatusTone.DANGER -> MaterialTheme.colorScheme.error
        RtcStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.heightIn(min = RtcSize.statusChipHeight),
        color = color.copy(alpha = .13f),
        contentColor = color,
        border = BorderStroke(RtcStroke.hairline, color.copy(alpha = .35f)),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = RtcSpacing.small, vertical = RtcSpacing.tiny),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

enum class RtcStatusTone { SUCCESS, PROTECTED, DANGER, NEUTRAL }

data class RtcResidentNavItem(val destination: MainDestination, val label: String, val icon: ImageVector)

@Composable
fun RtcResidentBottomNavigation(
    selected: MainDestination,
    items: List<RtcResidentNavItem>,
    onNavigate: (MainDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.heightIn(min = RtcSize.bottomNavigationHeight),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.destination,
                onClick = { onNavigate(item.destination) },
                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(RtcSize.actionIcon)) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                ),
            )
        }
    }
}

@Composable
fun RtcSectionHeader(title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun RtcSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String = "Search") {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = RtcSize.minimumTouchTarget),
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(RtcRadius.large),
    )
}

@Composable
fun RtcMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    RtcCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RtcQuickActionTile(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    RtcCard(onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RtcSize.actionIcon))
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RtcSettingsRow(title: String, description: String? = null, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = RtcSize.settingsRowHeight)
            .clickable(onClick = onClick)
            .padding(vertical = RtcSpacing.tiny),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        trailing()
    }
}

@Composable
fun RtcAccountGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { RtcSectionHeader(title); RtcCard(content = content) }
}

@Composable
fun RtcEmergencyBanner(title: String, message: String) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = .10f),
        border = BorderStroke(RtcStroke.hairline, MaterialTheme.colorScheme.error.copy(alpha = .4f)),
        shape = RoundedCornerShape(RtcRadius.large),
    ) {
        Column(
            Modifier.padding(RtcSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
        ) {
            Text(title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RtcCaseProgress(stage: CaseStage) {
    val label = stage.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
        Text("Case progress", style = MaterialTheme.typography.labelLarge)
        RtcStatusChip(label, RtcStatusTone.SUCCESS)
    }
}

@Composable
fun RtcCommunityComposerCard(displayName: String, onClick: () -> Unit) {
    RtcCard(onClick = onClick) { Text("What's happening in your community?", style = MaterialTheme.typography.bodyLarge); Text("Photo  •  Video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun CommunityFeedAvatar(url: String?, name: String, syntheticAvatarRes: Int? = null) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(RtcSize.avatarCompact),
    ) {
        if (url != null || syntheticAvatarRes != null) {
            AsyncImage(
                model = url ?: syntheticAvatarRes,
                contentDescription = "$name's profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RtcCommunityFeedCard(
    post: CommunityPost,
    onOpen: () -> Unit,
    syntheticAvatarRes: Int? = null,
    timestampLabel: String = post.createdAt,
    isUnread: Boolean = false,
    headerTrailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    RtcCard(
        onClick = onOpen,
        border = if (isUnread) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                CommunityFeedAvatar(post.authorAvatarUrl, post.author, syntheticAvatarRes)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Text(post.author, fontWeight = FontWeight.Bold)
                        if (isUnread) {
                            RtcStatusChip("NEW", RtcStatusTone.PROTECTED)
                        }
                    }
                    Text(timestampLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                if (post.isOfficial) RtcStatusChip("Official", RtcStatusTone.SUCCESS)
                headerTrailing?.invoke()
            }
        }
        Text(post.content, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
fun RtcOfficialUpdateCard(title: String, summary: String, onClick: () -> Unit) {
    RtcCard(protected = true, onClick = onClick) { RtcStatusChip("Official update", RtcStatusTone.PROTECTED); Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
fun RtcDirectoryCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = RtcCard(modifier = modifier, onClick = onClick) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
fun RtcWorkQueueCard(title: String, description: String, priority: String, onClick: (() -> Unit)? = null) = RtcCard(onClick = onClick) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.titleMedium); RtcStatusChip(priority, if (priority.equals("High", true)) RtcStatusTone.PROTECTED else RtcStatusTone.NEUTRAL) }; Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
fun RtcProtectedToolCard(title: String, description: String, onClick: () -> Unit) = RtcCard(protected = true, onClick = onClick) { RtcStatusChip("Protected", RtcStatusTone.PROTECTED); Text(title, style = MaterialTheme.typography.titleMedium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
fun RtcProtectedAreaBanner(message: String) {
    Surface(
        color = RtcCivicGold.copy(alpha = .12f),
        border = BorderStroke(RtcStroke.hairline, RtcCivicGold.copy(alpha = .45f)),
        shape = RoundedCornerShape(RtcRadius.large),
    ) {
        Text(
            message,
            modifier = Modifier.padding(RtcSpacing.standard),
            color = RtcCivicGold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun RtcEmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    RtcCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Surface(
                modifier = Modifier
                    .sizeIn(minHeight = RtcSize.minimumTouchTarget)
                    .clickable(onClick = onAction)
                    .semantics { role = Role.Button; contentDescription = actionLabel },
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(RtcRadius.medium),
            ) {
                Box(Modifier.padding(horizontal = RtcSpacing.standard, vertical = RtcSpacing.small)) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun RtcComposeFab(icon: ImageVector, label: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(RtcSize.fabDiameter),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(RtcSize.actionIcon))
    }
}

@Composable
fun RtcNoResultsFound(
    modifier: Modifier = Modifier,
    title: String = "No Results Found",
    message: String = "We couldn't find any published items matching your search. Try checking for typos or searching with different keywords.",
    searchQuery: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(RtcRadius.large)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RtcSpacing.standard),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)
        ) {
            Surface(
                shape = RoundedCornerShape(RtcRadius.large),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.size(140.dp)
            ) {
                Image(
                    painter = painterResource(id = za.org.rtc.community.R.drawable.img_no_results),
                    contentDescription = "No results found illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = if (!searchQuery.isNullOrBlank()) "No results for \"$searchQuery\"" else title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = RtcSize.minimumTouchTarget)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

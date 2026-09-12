package za.org.rtc.community.feature.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import za.org.rtc.community.core.*
import za.org.rtc.community.feature.community.CommunityPostCard
import za.org.rtc.community.feature.home.ContinueDraftCard
import za.org.rtc.community.ui.components.*
import za.org.rtc.community.ui.theme.*

@Composable
internal fun ExploreDirectoryScreen(
    directory: String,
    notices: List<OfficialNotice>,
    projects: List<ProjectRecord>,
    centres: List<CentreRecord>,
    opportunities: List<OpportunityRecord>,
    helpArticles: List<HelpArticle>,
    communityPosts: List<CommunityPost>,
    readingMode: Boolean,
    draft: LocalDraft?,
    onSubmit: (String, String) -> Unit,
    onSaveDraft: (String, String) -> Unit,
    onDiscardDraft: () -> Unit,
    canLoadMoreProjects: Boolean,
    canLoadMoreCentres: Boolean,
    canLoadMoreOpportunities: Boolean,
    onLoadMoreProjects: () -> Unit,
    onLoadMoreCentres: () -> Unit,
    onLoadMoreOpportunities: () -> Unit,
    onHelp: () -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
    onOpenDirectoryItem: (String, String) -> Unit,
    onOpenNotice: (OfficialNotice) -> Unit,
) {
    var submitOpen by rememberSaveable { mutableStateOf(false) }
    val title = when (directory) {
        "projects" -> "Projects"
        "centres" -> "Centres"
        "opportunities" -> "Opportunities"
        "notices" -> "Community Notices"
        "help" -> "Help Centre"
        "community" -> "Community"
        else -> "Explore"
    }
    LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("Browse ${title.lowercase()} in a focused view.", style = if (readingMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (directory) {
            "projects" -> {
                if (projects.isEmpty()) item { DirectoryEmptyState("No published projects are available yet.") }
                items(projects, key = { it.id }) { project -> ProjectDirectoryCard(project) { onOpenDirectoryItem("PROJECT", project.id) } }
                if (canLoadMoreProjects) item { DirectoryLoadMoreButton("Load more projects", onLoadMoreProjects) }
            }
            "centres" -> {
                if (centres.isEmpty()) item { DirectoryEmptyState("Centres will appear here as staff publish their service information.") }
                items(centres, key = { it.id }) { centre -> CentreDirectoryCard(centre) { onOpenDirectoryItem("CENTRE", centre.id) } }
                if (canLoadMoreCentres) item { DirectoryLoadMoreButton("Load more centres", onLoadMoreCentres) }
            }
            "opportunities" -> {
                if (opportunities.isEmpty()) item { DirectoryEmptyState("No published opportunities are available at the moment.") }
                items(opportunities, key = { it.id }) { opportunity -> OpportunityDirectoryCard(opportunity) { onOpenDirectoryItem("OPPORTUNITY", opportunity.id) } }
                if (canLoadMoreOpportunities) item { DirectoryLoadMoreButton("Load more opportunities", onLoadMoreOpportunities) }
            }
            "notices" -> {
                draft?.let { savedDraft -> item { ContinueDraftCard(draft = savedDraft, onResume = { submitOpen = true }, onDiscard = onDiscardDraft) } }
                item { SectionHeader("Official updates", "Submit an update") { submitOpen = true } }
                val publishedNotices = notices.filter { it.status == NoticeStatus.PUBLISHED }
                if (publishedNotices.isEmpty()) item { DirectoryEmptyState("No published community notices are available yet.") }
                items(publishedNotices, key = { it.id }) { notice -> NoticeStatusCard(notice) { onOpenNotice(notice) } }
            }
            "help" -> {
                if (helpArticles.isEmpty()) item { DirectoryEmptyState("No published Help Centre articles are available yet.") }
                items(helpArticles, key = { it.id }) { article -> HelpArticleCard(article) }
                item { TextButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text("Open Help Centre") } }
            }
            "community" -> {
                if (communityPosts.isEmpty()) item { DirectoryEmptyState("No Community posts are available yet.") }
                items(communityPosts, key = { it.id }) { post -> CommunityPostCard(post, readingMode, onOpenPost) }
            }
            else -> item { DirectoryEmptyState("Choose another category from Explore.") }
        }
    }
    if (submitOpen) {
        NoticeSubmissionSheet(
            draft = draft,
            onDismiss = { submitOpen = false },
            onSubmit = { titleValue, body -> onSubmit(titleValue, body); submitOpen = false },
            onSaveDraft = { titleValue, body -> onSaveDraft(titleValue, body); submitOpen = false },
            onDiscardDraft = { onDiscardDraft(); submitOpen = false },
        )
    }
}

@Composable
private fun ProjectDirectoryCard(project: ProjectRecord, onClick: () -> Unit) {
    val statusLabel = project.projectStatus.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(project.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("$statusLabel · ${project.sector}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
private fun CentreDirectoryCard(centre: CentreRecord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(centre.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(listOfNotNull(centre.category, centre.locality).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
private fun OpportunityDirectoryCard(opportunity: OpportunityRecord, onClick: () -> Unit) {
    val typeLabel = opportunity.opportunityType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    val detail = listOfNotNull(typeLabel, opportunity.closingAt?.take(10)?.let { "Closes $it" }).joinToString(" · ")
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(opportunity.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
private fun HelpArticleCard(article: HelpArticle) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(article.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(article.category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
internal fun DirectoryRecordDetailScreen(
    type: String,
    project: ProjectRecord?,
    centre: CentreRecord?,
    opportunity: OpportunityRecord?,
) {
    val title = when (type) {
        "PROJECT" -> project?.title
        "CENTRE" -> centre?.name
        "OPPORTUNITY" -> opportunity?.title
        else -> null
    }
    if (title == null) {
        PurposefulEmptyState("This item is no longer available.", "Return to Explore", {})
        return
    }
    LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                when (type) {
                    "PROJECT" -> project?.let {
                        Text("${it.projectStatus.replace('_', ' ')} · ${it.sector}", color = MaterialTheme.colorScheme.primary)
                        Text(it.details ?: it.summary, style = MaterialTheme.typography.bodyLarge)
                        it.sourceAsOf?.let { sourceDate -> Text("Information source date: $sourceDate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    "CENTRE" -> centre?.let {
                        Text(listOfNotNull(it.category, it.locality).joinToString(" · "), color = MaterialTheme.colorScheme.primary)
                        Text(it.summary, style = MaterialTheme.typography.bodyLarge)
                        listOfNotNull(it.address, it.openingHours, it.phone, it.email).forEach { detail -> Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    "OPPORTUNITY" -> opportunity?.let {
                        Text(it.opportunityType.replace('_', ' '), color = MaterialTheme.colorScheme.primary)
                        Text(it.summary, style = MaterialTheme.typography.bodyLarge)
                        listOfNotNull(it.organisation, it.locality, it.closingAt?.let { close -> "Closes $close" }, it.contactEmail).forEach { detail -> Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoticeDetailScreen(notice: OfficialNotice?) {
    if (notice == null) {
        PurposefulEmptyState("This notice is no longer available.", "Return to Explore", {})
        return
    }
    LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
        item {
            Text(notice.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            notice.publishedAt?.let { date -> Text(date, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        }
        item { Text(notice.summary, style = MaterialTheme.typography.bodyLarge) }
        if (notice.requiresSafetyReview) item { Text("This update includes a safety-review indicator.", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DirectoryLoadMoreButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun NoticeStatusCard(notice: OfficialNotice, onClick: () -> Unit = {}) {
    RtcCard(onClick = onClick, protected = notice.requiresSafetyReview) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(notice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            RtcStatusChip(notice.status.label, if (notice.requiresSafetyReview) RtcStatusTone.PROTECTED else RtcStatusTone.NEUTRAL)
        }
        Text(notice.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.requiresSafetyReview) Text("Safety review required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun DirectoryCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit = {}) {
    RtcCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(RtcSize.largeIcon), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

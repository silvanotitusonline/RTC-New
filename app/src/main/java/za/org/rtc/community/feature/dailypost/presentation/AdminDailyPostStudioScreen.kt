package za.org.rtc.community.feature.dailypost.presentation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import za.org.rtc.community.feature.dailypost.domain.DailyPost
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlock
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlockType
import za.org.rtc.community.feature.dailypost.domain.DailyPostDraft
import za.org.rtc.community.feature.dailypost.domain.DailyPostMediaUpload
import za.org.rtc.community.feature.dailypost.domain.DailyPostState
import za.org.rtc.community.feature.dailypost.domain.DailyPostTemplate
import za.org.rtc.community.feature.dailypost.domain.DailyPostTemplates
import za.org.rtc.community.feature.dailypost.domain.DailyPostType
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun AdminDailyPostStudioScreen(
    onBack: () -> Unit,
    viewModel: DailyPostStudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.editing) {
        DailyPostEditor(
            state = state,
            onBack = viewModel::closeEditor,
            onDraft = viewModel::updateDraft,
            onPreview = { viewModel.preview(true) },
            onSave = { viewModel.save() },
            onPublish = viewModel::publishNow,
            onSchedule = viewModel::schedule,
            onUpload = viewModel::uploadMedia,
            onDeleteMedia = viewModel::deleteMedia,
        )
    } else {
        DailyPostStudioHome(
            state = state,
            onBack = onBack,
            onTemplate = viewModel::newDraft,
            onFilter = viewModel::refresh,
            onEdit = viewModel::edit,
            onArchive = viewModel::archive,
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Daily Post Studio") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }
    if (state.previewing) {
        EditorialPreviewDialog(draft = state.draft, onDismiss = { viewModel.preview(false) })
    }
}

@Composable
private fun DailyPostStudioHome(
    state: DailyPostStudioState,
    onBack: () -> Unit,
    onTemplate: (DailyPostTemplate) -> Unit,
    onFilter: (DailyPostState?) -> Unit,
    onEdit: (DailyPost) -> Unit,
    onArchive: (DailyPost) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onBack) { Text("← Administration") }
                Text("THE DAILY POST STUDIO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text("Editorial publishing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Choose a professional layout, replace its content, preview the resident experience and publish immediately or on a schedule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Start from a template", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.size(6.dp)) }
                items(DailyPostTemplates.all, key = { it.key }) { template -> TemplateCard(template, onTemplate) }
                item { Spacer(Modifier.size(6.dp)) }
            }
        }
        item { Divider(Modifier.padding(vertical = 4.dp)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Publications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = state.filter == null, onClick = { onFilter(null) }, label = { Text("All") })
                    DailyPostState.entries.forEach { value ->
                        FilterChip(selected = state.filter == value, onClick = { onFilter(value) }, label = { Text(value.name.lowercase().replaceFirstChar(Char::titlecase)) })
                    }
                }
            }
        }
        if (state.working && state.publications.isEmpty()) {
            item { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        }
        if (!state.working && state.publications.isEmpty()) {
            item { Surface(Modifier.fillMaxWidth().padding(16.dp), tonalElevation = 1.dp, shape = RoundedCornerShape(16.dp)) { Text("No publications match this view.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        items(state.publications, key = { it.id }) { post ->
            PublicationManagementCard(post = post, onEdit = { onEdit(post) }, onArchive = { onArchive(post) })
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun TemplateCard(template: DailyPostTemplate, onTemplate: (DailyPostTemplate) -> Unit) {
    Card(
        modifier = Modifier.size(width = 220.dp, height = 155.dp).clickable { onTemplate(template) },
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (template.key == "breaking_news") Icons.Default.Campaign else Icons.Default.Article, null, tint = MaterialTheme.colorScheme.primary)
            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PublicationManagementCard(post: DailyPost, onEdit: () -> Unit, onArchive: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                    Text(post.state.name, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Text("r${post.revision}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(post.headline.ifBlank { "Untitled publication" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (post.excerpt.isNotBlank()) Text(post.excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (post.state in setOf(DailyPostState.DRAFT, DailyPostState.SCHEDULED)) OutlinedButton(onClick = onEdit) { Text("Edit") }
                if (post.state == DailyPostState.PUBLISHED) OutlinedButton(onClick = onArchive) { Text("Archive") }
            }
        }
    }
}

@Composable
private fun DailyPostEditor(
    state: DailyPostStudioState,
    onBack: () -> Unit,
    onDraft: ((DailyPostDraft) -> DailyPostDraft) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onPublish: () -> Unit,
    onSchedule: (java.time.Instant) -> Unit,
    onUpload: (DailyPostMediaUpload, String?) -> Unit,
    onDeleteMedia: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingMediaBlockId by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleText by rememberSaveable { mutableStateOf("") }
    var scheduleError by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take(8).forEach { uri ->
            readUpload(context, uri)?.let { upload -> onUpload(upload, pendingMediaBlockId) }
        }
        pendingMediaBlockId = null
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("← Publications") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Publication editor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(DailyPostTemplates.all.firstOrNull { it.key == state.draft.templateKey }?.name ?: state.draft.templateKey, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.working) CircularProgressIndicator(modifier = Modifier.size(26.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.draft.headline,
                    onValueChange = { value -> onDraft { it.copy(headline = value.take(180)) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Headline") },
                    supportingText = { Text("${state.draft.headline.length}/180") },
                )
                OutlinedTextField(
                    value = state.draft.excerpt,
                    onValueChange = { value -> onDraft { it.copy(excerpt = value.take(600)) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Short preview / excerpt") },
                    minLines = 2,
                    supportingText = { Text("${state.draft.excerpt.length}/600") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.draft.publicationType == DailyPostType.BREAKING, onCheckedChange = { enabled -> onDraft { it.copy(publicationType = if (enabled) DailyPostType.BREAKING else DailyPostType.NEWS) } })
                    Text("Breaking News treatment", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Story blocks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Reorder approved blocks or add new ones. This keeps every template responsive across Android devices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.draft.blocks, key = { it.id }) { block ->
            val index = state.draft.blocks.indexOfFirst { it.id == block.id }
            BlockEditorCard(
                block = block,
                index = index,
                count = state.draft.blocks.size,
                assignedMediaCount = block.mediaIds.size,
                onText = { text -> onDraft { draft -> draft.copy(blocks = draft.blocks.map { if (it.id == block.id) it.copy(text = text.take(12_000)) else it }) } },
                onMove = { direction -> onDraft { draft -> draft.copy(blocks = moveBlock(draft.blocks, index, index + direction)) } },
                onDelete = { onDraft { draft -> draft.copy(blocks = draft.blocks.filterNot { it.id == block.id }) } },
                onAddMedia = { pendingMediaBlockId = block.id; launcher.launch(if (block.type == DailyPostBlockType.VIDEO) "video/*" else if (block.type == DailyPostBlockType.IMAGE) "image/*" else "*/*") },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add block", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        DailyPostBlockType.PARAGRAPH,
                        DailyPostBlockType.SUBHEADING,
                        DailyPostBlockType.IMAGE,
                        DailyPostBlockType.VIDEO,
                        DailyPostBlockType.MEDIA_GALLERY,
                        DailyPostBlockType.PULL_QUOTE,
                        DailyPostBlockType.INFO_CALLOUT,
                        DailyPostBlockType.DIVIDER,
                        DailyPostBlockType.CTA,
                    ).forEach { type ->
                        AssistChip(onClick = { onDraft { draft -> draft.copy(blocks = draft.blocks + DailyPostBlock(UUID.randomUUID().toString(), type)) } }, label = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)) }, leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) })
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Media library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.media.isEmpty()) Text("No media uploaded to this publication yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.media.forEach { media ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (media.mediaType == "VIDEO") Icons.Default.VideoLibrary else Icons.Default.Image, null)
                        Spacer(Modifier.size(8.dp))
                        Text(media.altText.ifBlank { media.storagePath.substringAfterLast('/') }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onDeleteMedia(media.id) }) { Icon(Icons.Default.Delete, contentDescription = "Remove media") }
                    }
                }
                OutlinedButton(onClick = { pendingMediaBlockId = null; launcher.launch("*/*") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.size(6.dp)); Text("Upload media") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quote an RTC publication", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Select a published Daily Post to quote it inside this story.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.publications.filter { it.state == DailyPostState.PUBLISHED && it.id != state.draft.id }.take(12).forEach { quoted ->
                    AssistChip(
                        onClick = {
                            onDraft { draft ->
                                val quoteBlock = DailyPostBlock(UUID.randomUUID().toString(), DailyPostBlockType.QUOTED_PUBLICATION, text = quoted.headline, quotedPostId = quoted.id)
                                draft.copy(quotedPostId = quoted.id, blocks = draft.blocks + quoteBlock)
                            }
                        },
                        label = { Text(quoted.headline, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.FormatQuote, null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Distribution", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.draft.pushEnabled, onCheckedChange = { enabled -> onDraft { it.copy(pushEnabled = enabled) } })
                    Spacer(Modifier.size(10.dp))
                    Column { Text("Send push notification", fontWeight = FontWeight.SemiBold); Text("Notify residents when this publication goes live.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.draft.previewPopupEnabled, onCheckedChange = { enabled -> onDraft { it.copy(previewPopupEnabled = enabled) } })
                    Spacer(Modifier.size(10.dp))
                    Column { Text("One-time in-app preview", fontWeight = FontWeight.SemiBold); Text("Show each resident this story automatically no more than once.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schedule broadcast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = scheduleText,
                    onValueChange = { scheduleText = it; scheduleError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Local date & time") },
                    placeholder = { Text("2026-09-15 18:30") },
                    supportingText = { Text(scheduleError ?: "Format: YYYY-MM-DD HH:mm · ${ZoneId.systemDefault().id}") },
                    isError = scheduleError != null,
                )
                OutlinedButton(
                    enabled = !state.working,
                    onClick = {
                        val instant = runCatching { LocalDateTime.parse(scheduleText.trim(), SCHEDULE_FORMAT).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
                        if (instant == null || !instant.isAfter(java.time.Instant.now())) scheduleError = "Enter a valid future date and time."
                        else onSchedule(instant)
                    },
                ) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.size(6.dp)); Text("Schedule") }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f), enabled = !state.working) { Icon(Icons.Default.Preview, null); Spacer(Modifier.size(4.dp)); Text("Preview") }
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f), enabled = !state.working) { Text("Save draft") }
                Button(onClick = onPublish, modifier = Modifier.weight(1f), enabled = !state.working && state.draft.headline.isNotBlank() && state.draft.blocks.isNotEmpty()) { Icon(Icons.Default.Send, null); Spacer(Modifier.size(4.dp)); Text("Publish") }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun BlockEditorCard(
    block: DailyPostBlock,
    index: Int,
    count: Int,
    assignedMediaCount: Int,
    onText: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onAddMedia: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.type.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(enabled = index > 0, onClick = { onMove(-1) }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                IconButton(enabled = index < count - 1, onClick = { onMove(1) }) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove block") }
            }
            if (block.type in TEXT_BLOCKS) {
                OutlinedTextField(value = block.text, onValueChange = onText, modifier = Modifier.fillMaxWidth(), minLines = if (block.type == DailyPostBlockType.PARAGRAPH) 3 else 1, label = { Text("Content") })
            }
            if (block.type in MEDIA_BLOCKS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$assignedMediaCount media item${if (assignedMediaCount == 1) "" else "s"} assigned", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onAddMedia) { Text("Add media") }
                }
            }
        }
    }
}

@Composable
private fun EditorialPreviewDialog(draft: DailyPostDraft, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resident preview") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    if (draft.publicationType == DailyPostType.BREAKING) Text("BREAKING NEWS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                    Text(draft.headline.ifBlank { "Untitled publication" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    if (draft.excerpt.isNotBlank()) Text(draft.excerpt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(draft.blocks, key = { it.id }) { block ->
                    when (block.type) {
                        DailyPostBlockType.PARAGRAPH -> Text(block.text)
                        DailyPostBlockType.SUBHEADING, DailyPostBlockType.HEADLINE -> Text(block.text, fontWeight = FontWeight.Bold)
                        DailyPostBlockType.PULL_QUOTE -> Text("“${block.text}”", fontWeight = FontWeight.SemiBold)
                        DailyPostBlockType.INFO_CALLOUT -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) { Text(block.text, Modifier.padding(10.dp)) }
                        DailyPostBlockType.IMAGE, DailyPostBlockType.VIDEO, DailyPostBlockType.MEDIA_GALLERY -> Text("${block.type.name.replace('_', ' ')} · ${block.mediaIds.size} assigned", color = MaterialTheme.colorScheme.primary)
                        DailyPostBlockType.QUOTED_PUBLICATION -> Text("Quoted: ${block.text}", fontWeight = FontWeight.SemiBold)
                        DailyPostBlockType.DIVIDER -> Divider()
                        DailyPostBlockType.CTA -> Text(block.actionLabel ?: "Call to action", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Back to editor") } },
    )
}

private fun moveBlock(blocks: List<DailyPostBlock>, from: Int, to: Int): List<DailyPostBlock> {
    if (from !in blocks.indices || to !in blocks.indices || from == to) return blocks
    return blocks.toMutableList().apply { add(to, removeAt(from)) }
}

private fun readUpload(context: Context, uri: Uri): DailyPostMediaUpload? = runCatching {
    val mime = context.contentResolver.getType(uri) ?: return@runCatching null
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val max = 52_428_800 + 1
        val buffer = ByteArray(8192)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= max) { "Selected media exceeds 50 MB." }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: return@runCatching null
    val ext = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        else -> return@runCatching null
    }
    DailyPostMediaUpload(bytes = bytes, mimeType = mime, fileExtension = ext)
}.getOrNull()

private val SCHEDULE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val TEXT_BLOCKS = setOf(DailyPostBlockType.HEADLINE, DailyPostBlockType.PARAGRAPH, DailyPostBlockType.SUBHEADING, DailyPostBlockType.PULL_QUOTE, DailyPostBlockType.INFO_CALLOUT)
private val MEDIA_BLOCKS = setOf(DailyPostBlockType.IMAGE, DailyPostBlockType.VIDEO, DailyPostBlockType.MEDIA_GALLERY)

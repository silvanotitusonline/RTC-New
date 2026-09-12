package za.org.rtc.community.feature.dailypost.presentation

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import za.org.rtc.community.feature.dailypost.domain.DailyPost
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlock
import za.org.rtc.community.feature.dailypost.domain.DailyPostBlockType
import za.org.rtc.community.feature.dailypost.domain.DailyPostComment
import za.org.rtc.community.feature.dailypost.domain.DailyPostMedia
import za.org.rtc.community.feature.dailypost.domain.DailyPostTranslation
import za.org.rtc.community.feature.dailypost.domain.DailyPostType
import za.org.rtc.community.ui.media.RtcMedia3VideoPlayer
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun DailyPostScreen(
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
    initialPostId: String? = null,
    viewModel: DailyPostViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var translationPickerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialPostId) {
        if (!initialPostId.isNullOrBlank()) viewModel.open(initialPostId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.selected != null -> DailyPostDetail(
                post = requireNotNull(state.selected),
                translation = state.translation,
                media = state.media,
                comments = state.comments,
                currentUserId = currentUserId,
                narrationUrl = state.narrationUrl,
                translating = state.translating,
                narrating = state.narrating,
                onBack = viewModel::closeDetail,
                onTranslate = { translationPickerOpen = true },
                onClearTranslation = viewModel::clearTranslation,
                onNarrate = { viewModel.narrate() },
                onComment = viewModel::addComment,
                onUpdateComment = viewModel::updateComment,
                onDeleteComment = viewModel::deleteComment,
            )
            else -> DailyPostFeed(
                items = state.items,
                loading = state.loading,
                loadingMore = state.loadingMore,
                canLoadMore = state.cursor != null,
                message = state.message,
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onOpen = viewModel::open,
                onDismissMessage = viewModel::dismissMessage,
            )
        }
    }

    state.preview?.let { preview ->
        DailyPostPreviewDialog(
            post = preview,
            onDismiss = { viewModel.dismissPreview(open = false) },
            onOpen = { viewModel.dismissPreview(open = true) },
        )
    }

    if (translationPickerOpen) {
        DailyPostLanguageDialog(
            onDismiss = { translationPickerOpen = false },
            onSelect = { code ->
                translationPickerOpen = false
                viewModel.translate(code)
            },
        )
    }
}

@Composable
private fun DailyPostFeed(
    items: List<DailyPost>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("THE DAILY POST", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text("Community news & breaking updates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Official RTC publications, multimedia stories and community conversation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        message?.let { text ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = onDismissMessage) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
                    }
                }
            }
        }
        if (loading && items.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (items.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.primary)
                        Text("No Daily Post publications yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Published community stories will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                    }
                }
            }
        }
        items(items, key = { it.id }) { post -> DailyPostFeedCard(post, onOpen) }
        if (canLoadMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (loadingMore) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    else OutlinedButton(onClick = onLoadMore) { Text("Load more") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DailyPostFeedCard(post: DailyPost, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpen(post.id) },
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = if (post.publicationType == DailyPostType.BREAKING) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        if (post.publicationType == DailyPostType.BREAKING) "BREAKING" else "DAILY POST",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (post.publicationType == DailyPostType.BREAKING) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(post.publishedAt?.let(::formatPublicationTime) ?: "Published", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(post.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (post.excerpt.isNotBlank()) Text(post.excerpt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Comment, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${post.commentCount} comments", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DailyPostDetail(
    post: DailyPost,
    translation: DailyPostTranslation?,
    media: List<DailyPostMedia>,
    comments: List<DailyPostComment>,
    currentUserId: String?,
    narrationUrl: String?,
    translating: Boolean,
    narrating: Boolean,
    onBack: () -> Unit,
    onTranslate: () -> Unit,
    onClearTranslation: () -> Unit,
    onNarrate: () -> Unit,
    onComment: (String, String?) -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    val headline = translation?.headline ?: post.headline
    val excerpt = translation?.excerpt ?: post.excerpt
    val blocks = translation?.blocks ?: post.blocks
    var replyTo by rememberSaveable { mutableStateOf<String?>(null) }
    var commentBody by rememberSaveable { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("The Daily Post", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (post.publicationType == DailyPostType.BREAKING) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("BREAKING NEWS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                if (translation != null) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Translated to ${languageName(translation.targetLanguage)}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onClearTranslation) { Text("Original") }
                        }
                    }
                }
                Text(headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                if (excerpt.isNotBlank()) Text(excerpt, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(post.publishedAt?.let(::formatPublicationTime) ?: "Published", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !translating, onClick = onTranslate) {
                        if (translating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (translating) "Translating" else "Translate")
                    }
                    OutlinedButton(enabled = !narrating, onClick = onNarrate) {
                        if (narrating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (narrating) "Preparing voice" else "Listen")
                    }
                }
                narrationUrl?.let { NarrationPlayer(it) }
            }
        }
        items(blocks, key = { it.id }) { block ->
            DailyPostBlockRenderer(block = block, media = media, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            Divider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conversation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Join the discussion about this publication.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                replyTo?.let { parentId ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Reply, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Replying to a comment", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { replyTo = null }) { Text("Cancel") }
                    }
                }
                OutlinedTextField(
                    value = commentBody,
                    onValueChange = { commentBody = it.take(2_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    placeholder = { Text("Write a comment…") },
                    supportingText = { Text("${commentBody.length}/2000") },
                )
                Button(
                    onClick = { onComment(commentBody.trim(), replyTo); commentBody = ""; replyTo = null },
                    enabled = commentBody.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) { Text(if (replyTo == null) "Comment" else "Reply") }
            }
        }
        items(comments, key = { it.id }) { comment ->
            DailyPostCommentRow(
                comment = comment,
                isOwner = currentUserId != null && currentUserId == comment.authorId,
                onReply = { replyTo = comment.id },
                onUpdate = onUpdateComment,
                onDelete = onDeleteComment,
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DailyPostBlockRenderer(
    block: DailyPostBlock,
    media: List<DailyPostMedia>,
    modifier: Modifier = Modifier,
) {
    val blockMedia = block.mediaIds.mapNotNull { id -> media.firstOrNull { it.id == id } }
    when (block.type) {
        DailyPostBlockType.HEADLINE -> if (block.text.isNotBlank()) Text(block.text, modifier = modifier, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        DailyPostBlockType.SUBHEADING -> if (block.text.isNotBlank()) Text(block.text, modifier = modifier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DailyPostBlockType.PARAGRAPH -> if (block.text.isNotBlank()) Text(block.text, modifier = modifier, style = MaterialTheme.typography.bodyLarge)
        DailyPostBlockType.PULL_QUOTE -> if (block.text.isNotBlank()) Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
            Text("“${block.text}”", modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
        }
        DailyPostBlockType.INFO_CALLOUT -> if (block.text.isNotBlank()) Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
            Text(block.text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        DailyPostBlockType.DIVIDER -> Divider(modifier = modifier.padding(vertical = 8.dp))
        DailyPostBlockType.IMAGE -> blockMedia.firstOrNull()?.let { DailyPostMediaSurface(it, modifier.fillMaxWidth()) }
        DailyPostBlockType.VIDEO -> blockMedia.firstOrNull()?.let { DailyPostMediaSurface(it, modifier.fillMaxWidth()) }
        DailyPostBlockType.MEDIA_GALLERY -> LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(blockMedia, key = { it.id }) { item -> DailyPostMediaSurface(item, Modifier.size(width = 260.dp, height = 180.dp)) }
        }
        DailyPostBlockType.QUOTED_PUBLICATION -> Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp) {
            Column(Modifier.padding(14.dp)) {
                Text("QUOTED FROM THE DAILY POST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (block.text.isNotBlank()) Text(block.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        DailyPostBlockType.CTA -> if (block.actionLabel?.isNotBlank() == true) Button(onClick = {}, modifier = modifier) { Text(block.actionLabel) }
    }
}

@Composable
private fun DailyPostMediaSurface(media: DailyPostMedia, modifier: Modifier) {
    val url = media.signedUrl ?: return
    if (media.mediaType == "VIDEO") {
        RtcMedia3VideoPlayer(videoUrl = url, contentTitle = media.altText.ifBlank { "Daily Post video" }, autoPlay = false, modifier = modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
    } else {
        AsyncImage(
            model = url,
            contentDescription = media.altText.ifBlank { "Daily Post image" },
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun DailyPostCommentRow(
    comment: DailyPostComment,
    isOwner: Boolean,
    onReply: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by rememberSaveable(comment.id) { mutableStateOf(false) }
    var draft by rememberSaveable(comment.id) { mutableStateOf(comment.body) }
    Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp + (comment.depth * 18).dp, end = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(comment.authorDisplayName.take(1).uppercase(), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(comment.authorDisplayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(formatPublicationTime(comment.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (editing) {
                OutlinedTextField(draft, { draft = it.take(2_000) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onUpdate(comment.id, draft.trim()); editing = false }, enabled = draft.isNotBlank()) { Text("Save") }
                    TextButton(onClick = { draft = comment.body; editing = false }) { Text("Cancel") }
                }
            } else Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReply) { Icon(Icons.Default.Reply, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reply") }
                if (isOwner) {
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = { onDelete(comment.id) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun DailyPostPreviewDialog(post: DailyPost, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (post.publicationType == DailyPostType.BREAKING) Icons.Default.Campaign else Icons.Default.Article, null) },
        title = { Text(if (post.publicationType == DailyPostType.BREAKING) "Breaking from The Daily Post" else "New from The Daily Post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(post.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (post.excerpt.isNotBlank()) Text(post.excerpt, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("This preview is shown once. The story remains available in Explore → The Daily Post.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text("Read full story") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun DailyPostLanguageDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val languages = listOf(
        "en" to "English",
        "af" to "Afrikaans",
        "zu" to "isiZulu",
        "xh" to "isiXhosa",
        "st" to "Sesotho",
        "tn" to "Setswana",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Translate publication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Choose the language you want to read or hear.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                languages.forEach { (code, label) ->
                    AssistChip(onClick = { onSelect(code) }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp)) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NarrationPlayer(url: String) {
    var playing by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        val instance = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { prepared = true }
            setOnCompletionListener { playing = false }
            prepareAsync()
        }
        player = instance
        onDispose {
            runCatching { instance.stop() }
            instance.release()
            player = null
        }
    }

    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.RecordVoiceOver, null)
            Spacer(Modifier.width(8.dp))
            Text("AI voice narration", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            IconButton(enabled = prepared, onClick = {
                val active = player ?: return@IconButton
                if (playing) active.pause() else active.start()
                playing = !playing
            }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "Pause narration" else "Play narration") }
        }
    }
}

private fun formatPublicationTime(instant: java.time.Instant): String = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
    .withZone(ZoneId.systemDefault()).format(instant)

private fun languageName(code: String): String = when (code) {
    "af" -> "Afrikaans"
    "zu" -> "isiZulu"
    "xh" -> "isiXhosa"
    "st" -> "Sesotho"
    "tn" -> "Setswana"
    else -> "English"
}

package za.org.rtc.remediation.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import java.io.File
import za.org.rtc.remediation.model.FeedPost
import za.org.rtc.remediation.model.LoadState
import za.org.rtc.remediation.model.OutboxItem
import za.org.rtc.remediation.model.PreparedImage
import za.org.rtc.remediation.model.Provider
import za.org.rtc.remediation.model.Report
import za.org.rtc.remediation.presentation.DashboardSummary
import za.org.rtc.remediation.presentation.PostComposerState
import za.org.rtc.remediation.presentation.PostComposerViewModel
import za.org.rtc.remediation.presentation.PresentationRules

@Composable
fun FeedPostCard(post: FeedPost, imageLoader: ImageLoader, onOpen: () -> Unit, onVote: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        // Only the content opens details. The vote target is its sibling, with no parent click handler.
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(post.body, style = MaterialTheme.typography.bodyLarge)
            val ratio = if ((post.imageWidth ?: 0) > 0 && (post.imageHeight ?: 0) > 0)
                post.imageWidth!!.toFloat() / post.imageHeight!! else 1.5f
            RemotePhoto(post.imageUrl, imageLoader, "Photo attached to community post", aspectRatio = ratio)
            Text(PresentationRules.timestamp(post.createdAt), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = { onVote(!post.votedByMe) },
            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)
                .semantics { contentDescription = "${if (post.votedByMe) "Remove upvote" else "Upvote"}, ${post.voteCount} votes" },
        ) { Text("${if (post.votedByMe) "✓ Upvoted" else "↑ Upvote"} · ${post.voteCount}") }
    }
}

@Composable
private fun PostComposer(
    state: PostComposerState,
    viewModel: PostComposerViewModel,
    imageLoader: ImageLoader,
    prepareImage: suspend (Uri) -> PreparedImage,
    createCameraUri: (() -> Uri)?,
) {
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.prepare(uri, prepareImage)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) cameraUri?.let { viewModel.prepare(Uri.parse(it), prepareImage) }
        cameraUri = null
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Share a community update", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.body, onValueChange = viewModel::setBody, label = { Text("Your update") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !state.submitting,
                isError = PresentationRules.characterCount(state.body.trim()) > 4_000,
                supportingText = { Text("${PresentationRules.characterCount(state.body.trim())}/4,000") },
            )
            state.image?.let { image ->
                AsyncImage(
                    model = File(image.path), imageLoader = imageLoader,
                    contentDescription = "Selected photo", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                )
                TextButton(onClick = viewModel::clearImage, enabled = !state.submitting && !state.preparing) { Text("Remove photo") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.preparing && !state.submitting) {
                    Text(if (state.preparing) "Preparing…" else "Add photo")
                }
                if (createCameraUri != null) OutlinedButton(
                    onClick = {
                        try {
                            val uri = createCameraUri()
                            cameraUri = uri.toString()
                            cameraError = null
                            camera.launch(uri)
                        } catch (_: Exception) { cameraError = "Camera unavailable. Choose an existing photo." }
                    }, enabled = !state.preparing && !state.submitting,
                ) { Text("Camera") }
            }
            (state.error ?: cameraError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.submitting) "Saving update…" else "Post update")
            }
        }
    }
}

@Composable
fun OutboxCard(item: OutboxItem, imageLoader: ImageLoader, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = when (item.state) {
                "SENDING", "IN_FLIGHT", "SYNCING" -> "Sending"
                "FAILED", "TERMINAL", "FAILED_PERMANENT" -> "Needs attention"
                "NEEDS_AUTH", "AUTH_REQUIRED", "WAITING_AUTH" -> "Sign in to send"
                else -> "Queued locally"
            }
            Text("${if (item.kind == "REPORT") "Report" else "Update"} · $status", style = MaterialTheme.typography.titleSmall)
            if (item.preview.isNotBlank()) Text(item.preview)
            item.imagePath?.let { path ->
                AsyncImage(model = File(path), imageLoader = imageLoader, contentDescription = "Queued photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
            }
            if (item.createdAt.isNotBlank()) Text("Saved on this device · ${PresentationRules.timestamp(item.createdAt)}", style = MaterialTheme.typography.labelSmall)
            item.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (item.state == "FAILED") TextButton(onClick = onRetry) { Text("Retry delivery") }
        }
    }
}

@Composable
fun FeedScreen(
    feed: LoadState<List<FeedPost>>, outbox: List<OutboxItem>,
    composerState: PostComposerState, composer: PostComposerViewModel,
    imageLoader: ImageLoader, prepareImage: suspend (Uri) -> PreparedImage,
    createCameraUri: (() -> Uri)?, onRefresh: () -> Unit,
    onOpen: (FeedPost) -> Unit, onVote: (String, Boolean) -> Unit, onRetryOutbox: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("composer") { PostComposer(composerState, composer, imageLoader, prepareImage, createCameraUri) }
        items(outbox.filter { it.kind == "POST" }, key = { "queued-${it.id}" }) { item ->
            OutboxCard(item, imageLoader) { onRetryOutbox(item.id) }
        }
        when (feed) {
            LoadState.Loading -> item { LoadingCards() }
            is LoadState.Failure -> item { ErrorState(feed.message, onRefresh) }
            is LoadState.Content -> if (feed.value.isEmpty()) item {
                EmptyState("No community updates yet", "Share an update to start the conversation.")
            } else items(feed.value, key = { it.id }) { post -> FeedPostCard(post, imageLoader, { onOpen(post) }, { onVote(post.id, it) }) }
        }
    }
}

@Composable
fun ProviderCard(provider: Provider, distanceMeters: Long?, imageLoader: ImageLoader, onBook: () -> Unit, onDirections: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The image alone is clipped. The badge is a sibling and has dedicated overflow space.
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                if (provider.imageUrl != null) RemotePhoto(provider.imageUrl, imageLoader, "${provider.name} provider photo", contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().aspectRatio(1.5f), contentAlignment = Alignment.Center) { Text("Provider photo unavailable") }
                Surface(
                    Modifier.align(Alignment.BottomEnd).offset(y = 12.dp),
                    shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
                ) { Text(PresentationRules.price(provider.rateCents), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(provider.name, style = MaterialTheme.typography.titleMedium)
            Text(PresentationRules.distance(distanceMeters), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBook) { Text("Book service") }
                TextButton(onClick = onDirections, enabled = provider.latitude != null && provider.longitude != null) { Text("Directions") }
            }
        }
    }
}

@Composable
fun MarketplaceScreen(
    state: LoadState<List<Provider>>, distances: Map<String, Long?>, imageLoader: ImageLoader,
    onRefresh: () -> Unit, onBook: (Provider) -> Unit, onDirections: (Provider) -> Unit, modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state) {
            LoadState.Loading -> item { LoadingCards() }
            is LoadState.Failure -> item { ErrorState(state.message, onRefresh) }
            is LoadState.Content -> if (state.value.isEmpty()) item { EmptyState("No providers available", "Service providers will appear here when they are listed.") }
            else items(state.value, key = { it.id }) { provider -> ProviderCard(provider, distances[provider.id], imageLoader, { onBook(provider) }, { onDirections(provider) }) }
        }
    }
}

@Composable
fun ReportsScreen(
    reports: LoadState<List<Report>>, dashboard: LoadState<DashboardSummary>, outbox: List<OutboxItem>,
    imageLoader: ImageLoader, onRefresh: () -> Unit, onRetryOutbox: (String) -> Unit, modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("summary") { when (dashboard) {
            is LoadState.Content -> DashboardCard(dashboard.value)
            is LoadState.Failure -> ErrorState(dashboard.message, onRefresh)
            LoadState.Loading -> LoadingCards()
        } }
        items(outbox.filter { it.kind == "REPORT" }, key = { "queued-${it.id}" }) { item -> OutboxCard(item, imageLoader) { onRetryOutbox(item.id) } }
        when (reports) {
            LoadState.Loading -> Unit
            is LoadState.Failure -> Unit // The same repository failure is already shown above.
            is LoadState.Content -> if (reports.value.isEmpty()) item { EmptyState("No submitted reports", "Your submitted reports and their current status will appear here.") }
            else items(reports.value, key = { it.id }) { report ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(PresentationRules.status(report.status), style = MaterialTheme.typography.titleMedium)
                        Text(report.body)
                        Text("Submitted ${PresentationRules.timestamp(report.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        Text(if (report.category == "APP_SUPPORT") "App Support · IT team" else "Infrastructure · Municipal queue")
                    }
                }
            }
        }
    }
}

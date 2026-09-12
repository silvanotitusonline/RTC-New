package za.org.rtc.community.feature.dailypost.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import za.org.rtc.community.feature.dailypost.domain.DailyPostType

/**
 * Activity-level Daily Post coordinator.
 *
 * It deliberately lives above the resident navigation graph so a publication preview can be
 * evaluated whenever an authenticated activity returns to the foreground, even when Explore has
 * not been recreated. Server receipts remain the source of truth for whether a preview was shown.
 */
@Composable
fun DailyPostGlobalHost(
    currentUserId: String?,
    requestedPostId: String?,
    onRequestedPostConsumed: () -> Unit,
    viewModel: DailyPostViewModel = hiltViewModel(key = "daily-post-global-host"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var openPostId by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.checkPreview()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(requestedPostId) {
        val safePostId = requestedPostId?.takeIf(::isSafeDailyPostId)
        if (safePostId != null) {
            openPostId = safePostId
            viewModel.open(safePostId)
            onRequestedPostConsumed()
        } else if (requestedPostId != null) {
            onRequestedPostConsumed()
        }
    }

    if (openPostId == null) {
        state.preview?.let { preview ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPreview(open = false) },
                icon = {
                    Icon(
                        if (preview.publicationType == DailyPostType.BREAKING) Icons.Default.Campaign else Icons.Default.Article,
                        contentDescription = null,
                    )
                },
                title = {
                    Text(
                        if (preview.publicationType == DailyPostType.BREAKING) "Breaking from The Daily Post"
                        else "New from The Daily Post",
                    )
                },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(preview.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        if (preview.excerpt.isNotBlank()) {
                            Text(
                                preview.excerpt,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text(
                            "This preview appears once. The publication stays available in Explore → The Daily Post.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val postId = preview.id
                        viewModel.dismissPreview(open = false)
                        openPostId = postId
                        viewModel.open(postId)
                    }) { Text("Read full story") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPreview(open = false) }) { Text("Not now") }
                },
            )
        }
    }

    openPostId?.let { postId ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                DailyPostScreen(
                    modifier = Modifier.fillMaxSize(),
                    currentUserId = currentUserId,
                    initialPostId = postId,
                    viewModel = viewModel,
                )
                // Covers the nested detail back affordance for external opens so the action closes
                // the overlay rather than falling through to an embedded Daily Post feed.
                IconButton(
                    onClick = {
                        openPostId = null
                        viewModel.closeDetail()
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Daily Post")
                }
            }
        }
    }
}

private fun isSafeDailyPostId(value: String): Boolean =
    value.length in 1..128 && value.all { it.isLetterOrDigit() || it in "-_" }

package za.org.rtc.community.feature.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.time.Instant
import kotlinx.coroutines.launch
import za.org.rtc.community.ui.media.RtcMedia3VideoPlayer
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.ui.theme.RtcMath
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun CommunityMediaPreview(
    media: List<MediaItem>,
    onOpen: () -> Unit,
    onRefreshMediaUrl: suspend (String) -> String? = { null },
) {
    val first = media.minByOrNull { it.position } ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(RtcMath.Phi)
            .clickable(
                role = Role.Button,
                onClickLabel = "Open Community media",
                onClick = onOpen,
            )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                first.signedUrl == null -> {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = "Community media unavailable", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                first.kind == MediaKind.IMAGE -> {
                    RecoverableSignedImage(
                        mediaId = first.id,
                        initialUrl = first.signedUrl,
                        contentDescription = first.caption ?: "Community image attachment",
                        contentScale = ContentScale.Crop,
                        onRefreshUrl = onRefreshMediaUrl,
                    )
                }
                else -> {
                    CommunityVideoPoster(
                        url = first.signedUrl,
                        contentDescription = first.caption ?: "Community video attachment",
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Open Community video",
                            modifier = Modifier.padding(RtcSpacing.compact).size(RtcSize.mediaAction),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (media.size > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(RtcSpacing.compact),
                ) {
                    Text(
                        "${media.size} items",
                        modifier = Modifier.padding(horizontal = RtcSpacing.compact, vertical = RtcSpacing.relatedText),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityVideoPoster(url: String, contentDescription: String) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .videoFrameMillis(1_000)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun RecoverableSignedImage(
    mediaId: String,
    initialUrl: String,
    contentDescription: String,
    contentScale: ContentScale,
    onRefreshUrl: suspend (String) -> String?,
) {
    val scope = rememberCoroutineScope()
    var currentUrl by remember(mediaId, initialUrl) { mutableStateOf(initialUrl) }
    var refreshAttempted by remember(mediaId, initialUrl) { mutableStateOf(false) }
    var refreshing by remember(mediaId) { mutableStateOf(false) }
    var failed by remember(mediaId, initialUrl) { mutableStateOf(false) }

    fun refresh(explicit: Boolean) {
        if (refreshing || (!explicit && refreshAttempted)) return
        refreshAttempted = true
        refreshing = true
        failed = false
        scope.launch {
            val refreshed = onRefreshUrl(mediaId)
            refreshing = false
            if (refreshed.isNullOrBlank()) {
                failed = true
            } else {
                currentUrl = refreshed
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = currentUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onSuccess = { failed = false },
            onError = {
                if (!refreshAttempted) refresh(explicit = false) else failed = true
            },
        )
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(RtcSize.loadingIndicator)
                    .semantics { this.contentDescription = "Refreshing Community image" },
            )
        }
        if (failed && !refreshing) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = MaterialTheme.shapes.medium) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                ) {
                    Text("Image could not be loaded.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { refresh(explicit = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullScreenMediaGallery(
    media: List<MediaItem>,
    onRefreshMediaUrl: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    if (media.isEmpty()) return
    val ordered = remember(media) { media.sortedBy { it.position } }
    val pagerState = rememberPagerState(pageCount = { ordered.size })
    val selected = ordered.getOrElse(pagerState.currentPage) { ordered.first() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Media ${pagerState.currentPage + 1} of ${ordered.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                HorizontalPager(
                    state = pagerState,
                    key = { ordered[it].id },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    val item = ordered[page]
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            item.signedUrl == null -> Text(
                                "This media item is no longer available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            item.kind == MediaKind.IMAGE -> RecoverableSignedImage(
                                mediaId = item.id,
                                initialUrl = item.signedUrl,
                                contentDescription = item.caption ?: "Community image attachment",
                                contentScale = ContentScale.Fit,
                                onRefreshUrl = onRefreshMediaUrl,
                            )
                            else -> SignedVideoPlayer(
                                mediaId = item.id,
                                initialUrl = item.signedUrl,
                                onRefreshUrl = onRefreshMediaUrl,
                            )
                        }
                    }
                }
                selected.caption?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SignedVideoPlayer(
    mediaId: String,
    initialUrl: String,
    onRefreshUrl: suspend (String) -> String?,
) {
    RtcMedia3VideoPlayer(
        videoUrl = initialUrl,
        modifier = Modifier.fillMaxSize(),
        onRefreshUrl = { onRefreshUrl(mediaId) },
    )
}

@Composable
internal fun CommunityAvatar(url: String?, name: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.clip(MaterialTheme.shapes.extraLarge)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "$name's profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}

internal fun isCommentWithinEditWindow(createdAt: String): Boolean = runCatching {
    Instant.parse(createdAt).plusSeconds(60 * 60).isAfter(Instant.now())
}.getOrDefault(false)

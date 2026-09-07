package za.org.rtc.community.feature.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
private fun ThumbnailItem(
    item: MediaItem,
    onClick: () -> Unit,
    onRefreshMediaUrl: suspend (String) -> String?,
    modifier: Modifier = Modifier,
    overlayText: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Image,
                onClickLabel = "View image full screen",
                onClick = onClick,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                item.signedUrl == null -> {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = "Media unavailable",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.kind == MediaKind.IMAGE -> {
                    RecoverableSignedImage(
                        mediaId = item.id,
                        initialUrl = item.signedUrl,
                        contentDescription = item.caption ?: "Image attachment",
                        contentScale = ContentScale.Crop,
                        onRefreshUrl = onRefreshMediaUrl,
                    )
                }
                else -> {
                    CommunityVideoPoster(
                        url = item.signedUrl,
                        contentDescription = item.caption ?: "Video attachment",
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
            if (overlayText != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = overlayText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommunityMediaPreview(
    media: List<MediaItem>,
    onOpen: () -> Unit = {},
    onMediaClick: ((Int) -> Unit)? = null,
    onRefreshMediaUrl: suspend (String) -> String? = { null },
) {
    if (media.isEmpty()) return
    val ordered = remember(media) { media.sortedBy { it.position } }

    val handleClick: (Int) -> Unit = { index ->
        if (onMediaClick != null) {
            onMediaClick(index)
        } else {
            onOpen()
        }
    }

    when (ordered.size) {
        1 -> {
            ThumbnailItem(
                item = ordered[0],
                onClick = { handleClick(0) },
                onRefreshMediaUrl = onRefreshMediaUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(RtcMath.Phi),
            )
        }
        2 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ThumbnailItem(
                    item = ordered[0],
                    onClick = { handleClick(0) },
                    onRefreshMediaUrl = onRefreshMediaUrl,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                ThumbnailItem(
                    item = ordered[1],
                    onClick = { handleClick(1) },
                    onRefreshMediaUrl = onRefreshMediaUrl,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        3 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ThumbnailItem(
                    item = ordered[0],
                    onClick = { handleClick(0) },
                    onRefreshMediaUrl = onRefreshMediaUrl,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ThumbnailItem(
                        item = ordered[1],
                        onClick = { handleClick(1) },
                        onRefreshMediaUrl = onRefreshMediaUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    ThumbnailItem(
                        item = ordered[2],
                        onClick = { handleClick(2) },
                        onRefreshMediaUrl = onRefreshMediaUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
        else -> {
            val extraCount = ordered.size - 4
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ThumbnailItem(
                        item = ordered[0],
                        onClick = { handleClick(0) },
                        onRefreshMediaUrl = onRefreshMediaUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    ThumbnailItem(
                        item = ordered[1],
                        onClick = { handleClick(1) },
                        onRefreshMediaUrl = onRefreshMediaUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ThumbnailItem(
                        item = ordered[2],
                        onClick = { handleClick(2) },
                        onRefreshMediaUrl = onRefreshMediaUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    ThumbnailItem(
                        item = ordered[3],
                        onClick = { handleClick(3) },
                        onRefreshMediaUrl = onRefreshMediaUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        overlayText = if (extraCount > 0) "+$extraCount" else null,
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
    initialIndex: Int = 0,
    onRefreshMediaUrl: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    if (media.isEmpty()) return
    val ordered = remember(media) { media.sortedBy { it.position } }
    val safeInitialIndex = initialIndex.coerceIn(0, (ordered.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialIndex, pageCount = { ordered.size })
    val selected = ordered.getOrElse(pagerState.currentPage) { ordered.first() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    key = { ordered[it].id },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val item = ordered[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            item.signedUrl == null -> Text(
                                "This media item is no longer available.",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            item.kind == MediaKind.IMAGE -> RecoverableSignedImage(
                                mediaId = item.id,
                                initialUrl = item.signedUrl,
                                contentDescription = item.caption ?: "Full-screen image attachment",
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

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.semantics { contentDescription = "Close full-screen image viewer" },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${pagerState.currentPage + 1} of ${ordered.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }

                selected.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
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
    var isMuted by rememberSaveable(mediaId) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(mediaId, initialUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = if (isMuted) 0f else 1f
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    val muteDescription = if (isMuted) "Unmute video" else "Mute video"
    player.volume = if (isMuted) 0f else 1f

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

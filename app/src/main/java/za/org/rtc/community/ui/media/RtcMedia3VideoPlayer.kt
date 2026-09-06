package za.org.rtc.community.ui.media

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Format milliseconds into standard mm:ss or hh:mm:ss format.
 */
fun formatMediaTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Custom Media3 Video Player with built-in custom controller UI supporting:
 * - Seeking (interactive scrubber slider, ±10s fast forward and rewind)
 * - Volume adjustment (interactive slider, mute toggle, adaptive icon)
 * - Full-screen toggle (edge-to-edge fullscreen dialog modal with orientation support)
 * - Playback speed adjustments
 * - Auto-hiding controls with touch activation
 */
@Composable
fun RtcMedia3VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    contentTitle: String? = null,
    initialPositionMs: Long = 0L,
    autoPlay: Boolean = true,
    onRefreshUrl: (suspend () -> String?)? = null,
    onClose: (() -> Unit)? = null,
) {
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                VideoPlayerSurface(
                    videoUrl = videoUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentTitle = contentTitle,
                    initialPositionMs = initialPositionMs,
                    autoPlay = autoPlay,
                    isFullScreen = true,
                    onToggleFullScreen = { isFullScreen = false },
                    onRefreshUrl = onRefreshUrl,
                    onClose = { isFullScreen = false },
                )
            }
        }
    } else {
        VideoPlayerSurface(
            videoUrl = videoUrl,
            modifier = modifier,
            contentTitle = contentTitle,
            initialPositionMs = initialPositionMs,
            autoPlay = autoPlay,
            isFullScreen = false,
            onToggleFullScreen = { isFullScreen = true },
            onRefreshUrl = onRefreshUrl,
            onClose = onClose,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerSurface(
    videoUrl: String,
    modifier: Modifier = Modifier,
    contentTitle: String? = null,
    initialPositionMs: Long = 0L,
    autoPlay: Boolean = true,
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit,
    onRefreshUrl: (suspend () -> String?)? = null,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeUrl by remember(videoUrl) { mutableStateOf(videoUrl) }
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var volume by rememberSaveable { mutableFloatStateOf(1f) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = autoPlay
            this.volume = if (isMuted) 0f else volume
            playbackParameters = PlaybackParameters(playbackSpeed)
        }
    }

    // Connect Media item to Player
    LaunchedEffect(activeUrl) {
        if (activeUrl.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(activeUrl))
            player.prepare()
            if (initialPositionMs > 0L) {
                player.seekTo(initialPositionMs)
            }
        }
    }

    // Update volume
    LaunchedEffect(volume, isMuted) {
        player.volume = if (isMuted) 0f else volume
    }

    // Update playback speed
    LaunchedEffect(playbackSpeed) {
        player.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Polling player state and progress
    LaunchedEffect(player) {
        while (isActive) {
            if (!isSeeking && player.isPlaying) {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }
            durationMs = player.duration.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            isEnded = player.playbackState == Player.STATE_ENDED
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            delay(200)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(isControlsVisible, isPlaying, isSeeking, showVolumeSlider, showSpeedMenu) {
        if (isControlsVisible && isPlaying && !isSeeking && !showVolumeSlider && !showSpeedMenu) {
            delay(3500)
            isControlsVisible = false
        }
    }

    // Player lifecycle listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                    errorMessage = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Video playback interrupted."
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                isControlsVisible = !isControlsVisible
                if (!isControlsVisible) {
                    showVolumeSlider = false
                    showSpeedMenu = false
                }
            }
            .testTag("rtc_media3_video_player"),
        contentAlignment = Alignment.Center,
    ) {
        // AndroidView rendering Media3 ExoPlayer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // Use our rich custom Jetpack Compose controller UI!
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering Indicator
        if (isBuffering && !isEnded && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Buffering video" },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }

        // Error State Overlay
        if (errorMessage != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(RtcSpacing.standard),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = errorMessage ?: "Unable to play video",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                errorMessage = null
                                player.prepare()
                                player.play()
                            },
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                        if (onRefreshUrl != null) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isRefreshing = true
                                        val newUrl = onRefreshUrl()
                                        isRefreshing = false
                                        if (!newUrl.isNullOrBlank()) {
                                            activeUrl = newUrl
                                            errorMessage = null
                                        }
                                    }
                                },
                                enabled = !isRefreshing,
                            ) {
                                Text(if (isRefreshing) "Refreshing…" else "Refresh Link")
                            }
                        }
                    }
                }
            }
        }

        // Custom Overlay Controller UI
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        if (onClose != null || isFullScreen) {
                            IconButton(
                                onClick = {
                                    if (isFullScreen) onToggleFullScreen() else onClose?.invoke()
                                },
                                modifier = Modifier.testTag("video_close_button"),
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Filled.CloseFullscreen else Icons.Filled.ArrowBack,
                                    contentDescription = if (isFullScreen) "Exit full-screen" else "Close video",
                                    tint = Color.White,
                                )
                            }
                        }
                        contentTitle?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Playback Speed Selector Button
                        Box {
                            TextButton(
                                onClick = { showSpeedMenu = !showSpeedMenu },
                                modifier = Modifier.testTag("video_speed_button"),
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false },
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x ${if (speed == 1.0f) "(Normal)" else ""}") },
                                        onClick = {
                                            playbackSpeed = speed
                                            showSpeedMenu = false
                                        },
                                        leadingIcon = {
                                            if (playbackSpeed == speed) {
                                                Icon(Icons.Filled.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Full-screen Toggle Button
                        IconButton(
                            onClick = onToggleFullScreen,
                            modifier = Modifier.testTag("video_fullscreen_toggle"),
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullScreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = Color.White,
                            )
                        }
                    }
                }

                // Center Play / Rewind / Fast-Forward Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            val newPos = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            currentPositionMs = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .testTag("video_rewind_10s"),
                    ) {
                        Icon(
                            Icons.Filled.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    // Play / Pause / Replay Button
                    IconButton(
                        onClick = {
                            if (isEnded) {
                                player.seekTo(0)
                                player.play()
                            } else if (isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("video_play_pause_button"),
                    ) {
                        Icon(
                            imageVector = when {
                                isEnded -> Icons.Filled.Replay
                                isPlaying -> Icons.Filled.Pause
                                else -> Icons.Filled.PlayArrow
                            },
                            contentDescription = when {
                                isEnded -> "Replay video"
                                isPlaying -> "Pause video"
                                else -> "Play video"
                            },
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            val newPos = (player.currentPosition + 10_000L).coerceAtMost(durationMs)
                            player.seekTo(newPos)
                            currentPositionMs = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .testTag("video_forward_10s"),
                    ) {
                        Icon(
                            Icons.Filled.Forward10,
                            contentDescription = "Fast forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Bottom Controls: Scrubber Slider, Time Display & Volume Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Expanded Volume Slider popup if toggled
                    AnimatedVisibility(
                        visible = showVolumeSlider,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                                Slider(
                                    value = if (isMuted) 0f else volume,
                                    onValueChange = { newVol ->
                                        volume = newVol
                                        if (isMuted && newVol > 0f) isMuted = false
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("video_volume_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                    ),
                                )
                                Text(
                                    text = "${((if (isMuted) 0f else volume) * 100).toInt()}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(36.dp),
                                )
                            }
                        }
                    }

                    // Scrubber / Seeking Slider
                    val displayedPosition = if (isSeeking) seekPositionMs else currentPositionMs
                    val safeDuration = durationMs.coerceAtLeast(1L)
                    val progressRatio = (displayedPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                    Slider(
                        value = progressRatio,
                        onValueChange = { fraction ->
                            isSeeking = true
                            seekPositionMs = (fraction * safeDuration).toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(seekPositionMs)
                            currentPositionMs = seekPositionMs
                            isSeeking = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("video_seek_scrubber"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                    )

                    // Bottom Row: Time and Volume Toggle Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Current time / Total duration
                        Text(
                            text = "${formatMediaTime(displayedPosition)} / ${formatMediaTime(durationMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Quick Mute / Unmute Button
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("video_mute_toggle"),
                            ) {
                                Icon(
                                    imageVector = when {
                                        isMuted || volume == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                                        volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                        else -> Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Expand Volume Slider Button
                            IconButton(
                                onClick = { showVolumeSlider = !showVolumeSlider },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("video_volume_expand_button"),
                            ) {
                                Icon(
                                    imageVector = if (showVolumeSlider) Icons.Filled.Tune else Icons.Filled.VolumeUp,
                                    contentDescription = "Adjust volume slider",
                                    tint = if (showVolumeSlider) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

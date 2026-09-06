package za.org.rtc.community.feature.marketplace.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceRating
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.ui.theme.RtcRadius
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

@Composable
fun Modifier.shimmerLoading(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    return this.background(MaterialTheme.colorScheme.onSurface.copy(alpha = alphaAnim * 0.15f))
}

@Composable
fun ShimmerBusinessCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerLoading(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.height(18.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).shimmerLoading())
                    Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).shimmerLoading())
                }
            }
            Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(4.dp)).shimmerLoading())
            Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).shimmerLoading())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.height(28.dp).width(80.dp).clip(RoundedCornerShape(14.dp)).shimmerLoading())
                Box(modifier = Modifier.height(28.dp).width(100.dp).clip(RoundedCornerShape(14.dp)).shimmerLoading())
            }
        }
    }
}

@Composable
fun ShimmerMarketplaceFeedSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Banner Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .shimmerLoading(),
        )
        // Category Pills Skeleton
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(90.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .shimmerLoading(),
                )
            }
        }
        // Business Cards Skeleton
        repeat(3) {
            ShimmerBusinessCardSkeleton()
        }
    }
}

@Composable
internal fun <T> MarketplaceLoadContainer(
    state: MarketplaceLoadState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        MarketplaceLoadState.Idle, MarketplaceLoadState.Loading -> ShimmerMarketplaceFeedSkeleton()
        is MarketplaceLoadState.Failure -> Column(
            Modifier.fillMaxWidth().padding(RtcSpacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Try again") }
        }
        is MarketplaceLoadState.Data -> content(state.value)
    }
}

/**
 * App Store-inspired Squircle Icon for business cards and headers.
 */
@Composable
fun AppStoreSquircleLogo(
    displayName: String = "",
    name: String = displayName,
    category: String = "",
    logoUrl: String? = null,
    size: Dp = 60.dp,
    modifier: Modifier = Modifier.size(size),
) {
    val titleText = displayName.ifBlank { name }
    val gradientBrush = getCategoryGradient(category)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(size * 0.26f))
            .background(gradientBrush),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) {
            MarketplaceProgressiveImage(
                url = logoUrl,
                contentDescription = "$titleText logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val initial = titleText.trim().take(1).uppercase().ifBlank { "M" }
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * Large App Store "Today / Spotlight" Hero Card with cover artwork, punchy headlines and action pill.
 */
@Composable
fun AppStoreSpotlightHero(
    business: MarketplaceBusinessCard,
    heroImageUrl: String? = null,
    badgeText: String = "FEATURED SPOTLIGHT",
    onOpen: () -> Unit = {},
    onClick: () -> Unit = onOpen,
    onGetClick: (() -> Unit)? = null,
    onSaveToggle: (() -> Unit)? = null,
    isSaved: Boolean = false,
) {
    val effectiveOpen = onClick
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heroScale",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = effectiveOpen,
            )
            .testTag("appstore_hero_${business.slug}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            // Visual Artwork Banner with gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.95f),
                            )
                        )
                    )
            ) {
                if (!heroImageUrl.isNullOrBlank()) {
                    MarketplaceProgressiveImage(
                        url = heroImageUrl,
                        contentDescription = business.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Gradient scrim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.75f),
                                )
                            )
                        )
                )

                // Top Tag & Save Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.25f),
                    ) {
                        Text(
                            text = badgeText.uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (onSaveToggle != null) {
                        IconButton(
                            onClick = onSaveToggle,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.35f), shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isSaved) "Saved" else "Save",
                                tint = if (isSaved) MaterialTheme.colorScheme.primaryContainer else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Bottom Overlay Text on Artwork
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        text = business.category.uppercase(),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = business.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bottom Footer Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    AppStoreSquircleLogo(
                        displayName = business.displayName,
                        category = business.category,
                        logoUrl = business.logoPath,
                        modifier = Modifier.size(46.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = business.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (business.verified) {
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = business.tagline.ifBlank { "${business.category} · ${business.locality}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "%.1f".format(business.ratingAverage),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "(${business.reviewCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // AppStore "VIEW / GET" Pill
                AppStoreGetButton(
                    label = "VIEW",
                    onClick = onGetClick ?: effectiveOpen,
                )
            }
        }
    }
}

/**
 * App Store-style horizontal row card for listings (e.g., Trending, Near Me, New).
 */
@Composable
fun AppStoreBusinessRowCard(
    business: MarketplaceBusinessCard,
    rankingNumber: Int? = null,
    rank: Int? = rankingNumber,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onOpen: () -> Unit = {},
    onClick: () -> Unit = onOpen,
    onGetClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val effectiveRank = rank ?: rankingNumber
    val effectiveOpenAction = onClick
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "rowCardScale",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = effectiveOpenAction,
            )
            .testTag("business_row_${business.slug}"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Optional App Store rank (#1, #2, #3...)
            if (effectiveRank != null) {
                Text(
                    text = "$effectiveRank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(22.dp),
                )
            }

            // Squircle Logo
            AppStoreSquircleLogo(
                displayName = business.displayName,
                category = business.category,
                logoUrl = business.logoPath,
                modifier = Modifier.size(54.dp),
            )

            // Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = business.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (business.verified) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "Verified Provider",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                Text(
                    text = business.tagline.ifBlank { business.category },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Rating and Metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "%.1f".format(business.ratingAverage),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = business.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (business.distanceMetres != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        val distStr = if (business.distanceMetres < 1000) "${business.distanceMetres}m" else "%.1f km".format(business.distanceMetres / 1000.0)
                        Text(
                            text = distStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Action Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onToggleSave != null) {
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isSaved) "Saved" else "Save",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                AppStoreGetButton(
                    label = "OPEN",
                    onClick = onGetClick ?: effectiveOpenAction,
                )
            }
        }
    }
}

/**
 * App Store Feature Card for horizontal carousels (Card size ~240dp wide).
 */
@Composable
fun AppStoreMediumCard(
    business: MarketplaceBusinessCard,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onOpen: () -> Unit = {},
    onClick: () -> Unit = onOpen,
    onGetClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.width(260.dp),
) {
    val effectiveOpen = onClick
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mediumCardScale",
    )
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = effectiveOpen,
            )
            .testTag("appstore_card_${business.slug}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppStoreSquircleLogo(
                    displayName = business.displayName,
                    category = business.category,
                    logoUrl = business.logoPath,
                    modifier = Modifier.size(48.dp),
                )
                if (onToggleSave != null) {
                    IconButton(onClick = onToggleSave, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isSaved) "Saved" else "Save",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = business.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (business.verified) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Text(
                text = business.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = business.tagline.ifBlank { "Local verified community partner in ${business.locality}." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(36.dp),
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
                    Text(
                        text = "%.1f".format(business.ratingAverage),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "(${business.reviewCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AppStoreGetButton(
                    label = "GET",
                    onClick = onGetClick ?: effectiveOpen,
                )
            }
        }
    }
}

/**
 * App Store style Section Header with Title, Subtitle, and "See All ›" link.
 */
@Composable
fun AppStoreSectionHeader(
    title: String,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RtcSpacing.pageGutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onSeeAll != null) {
            TextButton(
                onClick = onSeeAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * iOS App Store Style Rounded Pill Button ("GET", "VIEW", "BOOK").
 */
@Composable
fun AppStoreGetButton(
    label: String = "GET",
    text: String = label,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val buttonText = text.ifBlank { label }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = modifier.height(30.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buttonText.uppercase(),
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * Interactive Star Rating Bar (1 to 5 stars) for writing reviews.
 */
@Composable
fun InteractiveStarRatingBar(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..5).forEach { starIndex ->
            IconButton(
                onClick = { onRatingChanged(starIndex) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (starIndex <= rating) Icons.Filled.Star else Icons.Filled.StarOutline,
                    contentDescription = "$starIndex stars",
                    tint = if (starIndex <= rating) Color(0xFFFFB800) else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Interactive Write Review Dialog.
 */
@Composable
fun WriteReviewDialog(
    businessName: String,
    initialRating: Int = 5,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, title: String, body: String, photos: List<String>) -> Unit,
) {
    var rating by remember { mutableIntStateOf(initialRating) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachedPhotos = (attachedPhotos + uris.map { it.toString() }).distinct()
        }
    }

    val sampleReviewPhotos = remember {
        listOf(
            "https://images.unsplash.com/photo-1581092918056-0c4c3acd3789?w=600&q=80" to "Work Result",
            "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600&q=80" to "Food/Service",
            "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=600&q=80" to "Storefront",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Write a Review", fontWeight = FontWeight.Bold)
                Text(
                    "Share your experience & photos with $businessName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Interactive Stars
                InteractiveStarRatingBar(
                    rating = rating,
                    onRatingChanged = { rating = it },
                )
                Text(
                    text = when (rating) {
                        5 -> "Excellent ★★★★★"
                        4 -> "Great ★★★★☆"
                        3 -> "Good ★★★☆☆"
                        2 -> "Fair ★★☆☆☆"
                        else -> "Needs Improvement ★☆☆☆☆"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g., Fast & Professional Service)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Your Review Details") },
                    placeholder = { Text("Describe the quality, pricing, and communication...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )

                // Photo Attachments Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Attach Review Photos (${attachedPhotos.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Upload", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Preset Quick Sample Photos
                    if (attachedPhotos.isEmpty()) {
                        Text(
                            text = "Quick Attach Sample Photos:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            sampleReviewPhotos.forEach { (url, label) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        if (!attachedPhotos.contains(url)) {
                                            attachedPhotos = attachedPhotos + url
                                        }
                                    },
                                    label = { Text("+ $label", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }

                    // Attached Photo Thumbnails Row
                    if (attachedPhotos.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(attachedPhotos, key = { it }) { photoUrl ->
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                ) {
                                    MarketplaceProgressiveImage(
                                        url = photoUrl,
                                        contentDescription = "Attached review photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    IconButton(
                                        onClick = {
                                            attachedPhotos = attachedPhotos.filter { it != photoUrl }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f)),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, title, body, attachedPhotos) },
                enabled = rating in 1..5,
                shape = CircleShape,
            ) {
                Text("Submit Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * App Store-inspired Star Distribution Chart.
 */
@Composable
fun AppStoreRatingSummary(
    rating: MarketplaceRating? = null,
    average: Double = rating?.average ?: 5.0,
    count: Int = rating?.count ?: 0,
    distribution: Map<String, Int> = rating?.distribution.orEmpty(),
    modifier: Modifier = Modifier,
) {
    val displayAvg = rating?.average ?: average
    val displayCount = rating?.count ?: count
    val displayDist = rating?.distribution ?: distribution

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Big Score
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "%.1f".format(displayAvg),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= displayAvg.toInt()) Icons.Filled.Star else if (star - displayAvg < 0.7) Icons.Filled.StarHalf else Icons.Filled.StarOutline,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = "$displayCount ratings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 5 Bar Distribution
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (5 downTo 1).forEach { starCount ->
                    val distCount = displayDist[starCount.toString()] ?: 0
                    val fraction = if (displayCount > 0) distCount.toFloat() / displayCount.toFloat() else 0f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "$starCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(10.dp),
                        )
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFFFB800),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Generates dynamic vibrant gradients based on category keyword.
 */
internal fun getCategoryGradient(category: String): Brush {
    val cat = category.lowercase()
    return when {
        cat.contains("plumb") || cat.contains("water") || cat.contains("tech") || cat.contains("it") -> Brush.linearGradient(
            listOf(Color(0xFF007AFF), Color(0xFF5856D6))
        )
        cat.contains("food") || cat.contains("bake") || cat.contains("cafe") || cat.contains("cater") -> Brush.linearGradient(
            listOf(Color(0xFFFF9500), Color(0xFFFF2D55))
        )
        cat.contains("electric") || cat.contains("solar") || cat.contains("power") -> Brush.linearGradient(
            listOf(Color(0xFFFFCC00), Color(0xFFFF9500))
        )
        cat.contains("clean") || cat.contains("garden") || cat.contains("landscap") || cat.contains("green") -> Brush.linearGradient(
            listOf(Color(0xFF34C759), Color(0xFF30B0C7))
        )
        cat.contains("auto") || cat.contains("mechanic") || cat.contains("car") -> Brush.linearGradient(
            listOf(Color(0xFF5856D6), Color(0xFFAF52DE))
        )
        cat.contains("health") || cat.contains("physio") || cat.contains("wellness") -> Brush.linearGradient(
            listOf(Color(0xFFFF2D55), Color(0xFFAF52DE))
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF007AFF), Color(0xFF34C759))
        )
    }
}

@Composable
internal fun MarketplaceBusinessCardView(
    business: MarketplaceBusinessCard,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    AppStoreBusinessRowCard(
        business = business,
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onOpen = onOpen,
    )
}

@Composable
internal fun MarketplaceDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun MarketplaceNotice(message: String?, onDismiss: (() -> Unit)? = null) {
    if (message.isNullOrBlank()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(RtcSpacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            onDismiss?.let { TextButton(onClick = it) { Text("Dismiss") } }
        }
    }
}

@Composable
internal fun ConfirmMarketplaceActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    feedbackLabel: String? = null,
    feedback: String = "",
    onFeedbackChange: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text(message)
                feedbackLabel?.let {
                    OutlinedTextField(
                        value = feedback,
                        onValueChange = onFeedbackChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(it) },
                        minLines = 3,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = feedbackLabel == null || feedback.isNotBlank(),
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun businessMetadata(business: MarketplaceBusinessCard): String = buildString {
    append("%.1f".format(business.ratingAverage))
    append(" · ${business.reviewCount} review")
    if (business.reviewCount != 1) append("s")
    business.distanceMetres?.let { distance ->
        append(if (distance < 1000) " · ${distance} m" else " · %.1f km".format(distance / 1000.0))
    }
    business.locality.takeIf(String::isNotBlank)?.let { append(" · $it") }
}

internal fun JsonObject.marketplaceString(name: String): String = marketplaceStringOrNull(name).orEmpty()
internal fun JsonObject.marketplaceStringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
internal fun JsonObject.marketplaceArray(name: String): JsonArray = runCatching { this[name]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())

/**
 * Interactive Google Maps-styled visual map component for the "Businesses Near Me" section
 * and dedicated Map Discovery screen.
 */
@Composable
fun MarketplaceNearMeMapView(
    businesses: List<MarketplaceBusinessCard>,
    locality: String? = null,
    modifier: Modifier = Modifier,
    isSaved: (String) -> Boolean = { false },
    onToggleSave: ((String) -> Unit)? = null,
    onBusinessClick: (MarketplaceBusinessCard) -> Unit,
) {
    val context = LocalContext.current
    var selectedBusinessId by rememberSaveable { mutableStateOf(businesses.firstOrNull()?.id) }
    var zoomLevel by remember { mutableStateOf(1f) }
    var panOffsetX by remember { mutableStateOf(0f) }
    var panOffsetY by remember { mutableStateOf(0f) }

    val selectedBusiness = remember(selectedBusinessId, businesses) {
        businesses.firstOrNull { it.id == selectedBusinessId } ?: businesses.firstOrNull()
    }

    // Launch external Google Maps intent
    val openGoogleMapsForBusiness: (MarketplaceBusinessCard) -> Unit = { biz ->
        val query = Uri.encode("${biz.displayName}, ${biz.locality}")
        val geoUri = Uri.parse("geo:0,0?q=$query")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$query"))
            context.startActivity(webIntent)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF2EFE9))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomLevel = (zoomLevel * zoom).coerceIn(0.7f, 2.5f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
    ) {
        // Map Canvas Graphic
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val centerX = (canvasW / 2f) + panOffsetX
            val centerY = (canvasH / 2f) + panOffsetY

            // Background Terrain & Parks
            drawRect(Color(0xFFF5F3ED))

            // Decorative green park areas
            val parkPath1 = Path().apply {
                moveTo(centerX - 180f * zoomLevel, centerY - 140f * zoomLevel)
                lineTo(centerX - 60f * zoomLevel, centerY - 180f * zoomLevel)
                lineTo(centerX - 20f * zoomLevel, centerY - 90f * zoomLevel)
                lineTo(centerX - 150f * zoomLevel, centerY - 50f * zoomLevel)
                close()
            }
            drawPath(parkPath1, Color(0xFFDCEFD9))

            val parkPath2 = Path().apply {
                moveTo(centerX + 80f * zoomLevel, centerY + 60f * zoomLevel)
                lineTo(centerX + 220f * zoomLevel, centerY + 30f * zoomLevel)
                lineTo(centerX + 260f * zoomLevel, centerY + 160f * zoomLevel)
                lineTo(centerX + 120f * zoomLevel, centerY + 180f * zoomLevel)
                close()
            }
            drawPath(parkPath2, Color(0xFFDCEFD9))

            // River / Waterway
            val riverPath = Path().apply {
                moveTo(0f, centerY + 120f * zoomLevel)
                cubicTo(
                    centerX - 100f * zoomLevel, centerY + 100f * zoomLevel,
                    centerX + 100f * zoomLevel, centerY + 240f * zoomLevel,
                    canvasW, centerY + 200f * zoomLevel
                )
            }
            drawPath(
                path = riverPath,
                color = Color(0xFFCBE6F6),
                style = Stroke(width = 24f * zoomLevel, cap = StrokeCap.Round)
            )

            // Street & Avenue Grid Lines
            val roadColor = Color(0xFFFFFFFF)
            val roadBorder = Color(0xFFE4DFD7)
            val strokeWidthMajor = 14f * zoomLevel
            val strokeWidthMinor = 8f * zoomLevel

            // Main Avenues
            val mainRoads = listOf(
                Pair(Offset(0f, centerY - 40f * zoomLevel), Offset(canvasW, centerY - 40f * zoomLevel)),
                Pair(Offset(0f, centerY + 80f * zoomLevel), Offset(canvasW, centerY + 80f * zoomLevel)),
                Pair(Offset(centerX - 100f * zoomLevel, 0f), Offset(centerX - 100f * zoomLevel, canvasH)),
                Pair(Offset(centerX + 110f * zoomLevel, 0f), Offset(centerX + 110f * zoomLevel, canvasH)),
                Pair(Offset(0f, centerY - 160f * zoomLevel), Offset(canvasW, centerY + 40f * zoomLevel)),
            )

            mainRoads.forEach { (start, end) ->
                drawLine(roadBorder, start, end, strokeWidth = strokeWidthMajor + 4f)
                drawLine(roadColor, start, end, strokeWidth = strokeWidthMajor, cap = StrokeCap.Round)
            }

            // Secondary Streets
            val secondaryRoads = listOf(
                Pair(Offset(0f, centerY + 20f * zoomLevel), Offset(canvasW, centerY + 20f * zoomLevel)),
                Pair(Offset(0f, centerY - 110f * zoomLevel), Offset(canvasW, centerY - 110f * zoomLevel)),
                Pair(Offset(centerX + 20f * zoomLevel, 0f), Offset(centerX + 20f * zoomLevel, canvasH)),
                Pair(Offset(centerX - 200f * zoomLevel, 0f), Offset(centerX - 200f * zoomLevel, canvasH)),
                Pair(Offset(centerX + 210f * zoomLevel, 0f), Offset(centerX + 210f * zoomLevel, canvasH)),
            )

            secondaryRoads.forEach { (start, end) ->
                drawLine(roadBorder, start, end, strokeWidth = strokeWidthMinor + 2f)
                drawLine(roadColor, start, end, strokeWidth = strokeWidthMinor, cap = StrokeCap.Round)
            }

            // Distance Radar Circles
            val dashedStroke = Stroke(
                width = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
            drawCircle(
                color = Color(0xFF007AFF).copy(alpha = 0.25f),
                radius = 110f * zoomLevel,
                center = Offset(centerX, centerY),
                style = dashedStroke
            )
            drawCircle(
                color = Color(0xFF007AFF).copy(alpha = 0.18f),
                radius = 220f * zoomLevel,
                center = Offset(centerX, centerY),
                style = dashedStroke
            )

            // User Location Pin (Blue pulsing beacon)
            drawCircle(
                color = Color(0xFF007AFF).copy(alpha = 0.2f),
                radius = 26f * zoomLevel,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 12f * zoomLevel,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color(0xFF007AFF),
                radius = 9f * zoomLevel,
                center = Offset(centerX, centerY)
            )
        }

        // Business Pins Overlay
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val centerX = (boxW / 2f) + panOffsetX
            val centerY = (boxH / 2f) + panOffsetY

            // Compute positions for each business in a radial distribution around the center
            businesses.forEachIndexed { index, business ->
                val angle = (index * (360f / maxOf(businesses.size, 1)) + 25f) * (Math.PI / 180.0)
                val distFactor = ((business.distanceMetres ?: ((index + 1) * 450)) / 1500f).coerceIn(0.4f, 1.6f)
                val radius = (120f * distFactor * zoomLevel)

                val pinX = (centerX + (Math.cos(angle) * radius).toFloat()).coerceIn(40f, boxW - 140f)
                val pinY = (centerY + (Math.sin(angle) * radius).toFloat()).coerceIn(40f, boxH - 180f)

                val isSelected = business.id == selectedBusinessId

                Box(
                    modifier = Modifier
                        .offset(
                            x = (pinX / LocalContext.current.resources.displayMetrics.density).dp,
                            y = (pinY / LocalContext.current.resources.displayMetrics.density).dp
                        )
                        .clickable {
                            selectedBusinessId = business.id
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Business Pin Head
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shadowElevation = if (isSelected) 8.dp else 3.dp,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) Color.White else MaterialTheme.colorScheme.outlineVariant
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = when {
                                        business.category.contains("Food", true) || business.category.contains("Bake", true) -> Icons.Filled.Restaurant
                                        business.category.contains("Tech", true) || business.category.contains("Repair", true) -> Icons.Filled.Devices
                                        business.category.contains("Auto", true) || business.category.contains("Mechanic", true) -> Icons.Filled.DirectionsCar
                                        business.category.contains("Health", true) -> Icons.Filled.Spa
                                        business.category.contains("Retail", true) -> Icons.Filled.ShoppingBag
                                        else -> Icons.Filled.Storefront
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = business.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 90.dp),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "%.1f".format(business.ratingAverage),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) Color.White else Color(0xFFFFB300),
                                    )
                                }
                            }
                        }

                        // Pin Needle
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                        )
                    }
                }
            }
        }

        // Top Header Badge & GPS Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (!locality.isNullOrBlank()) "Map · $locality" else "Nearby Community Map",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            // Map Controls (Recenter + Google Maps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {
                        panOffsetX = 0f
                        panOffsetY = 0f
                        zoomLevel = 1f
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Recenter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Surface(
                    onClick = {
                        selectedBusiness?.let(openGoogleMapsForBusiness)
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Directions,
                            contentDescription = "Open in Google Maps",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Bottom Selected Business Card Floating Sheet
        selectedBusiness?.let { business ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppStoreSquircleLogo(
                            name = business.displayName,
                            category = business.category,
                            size = 50.dp,
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = business.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (business.verified) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verified",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }

                            Text(
                                text = "${business.category} · ${business.locality}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = " %.1f".format(business.ratingAverage),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                                Text(
                                    text = "· ${((business.distanceMetres ?: 850) / 1000.0).let { "%.1f".format(it) }} km away",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppStoreGetButton(
                                text = "VIEW",
                                onClick = { onBusinessClick(business) },
                            )

                            onToggleSave?.let { toggle ->
                                val saved = isSaved(business.id)
                                IconButton(
                                    onClick = { toggle(business.id) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = if (saved) "Unsave" else "Save",
                                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun MarketplaceProgressiveImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailWidth: Int = 200
) {
    val context = LocalContext.current
    val app = context.applicationContext as? za.org.rtc.community.RtcCommunityApplication
    val loader = app?.marketplaceImageLoader ?: coil.Coil.imageLoader(context)
    val thumbnailUrl = remember(url, thumbnailWidth) {
        url?.replace("/object/sign/", "/render/image/sign/")
            ?.replace("/object/public/", "/render/image/public/")
            ?.let {
                if (it.contains("?")) "$it&width=$thumbnailWidth&quality=50"
                else "$it?width=$thumbnailWidth&quality=50"
            } ?: url
    }

    SubcomposeAsyncImage(
                imageLoader = loader,
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            AsyncImage(
                imageLoader = loader,
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        },
        error = {
            // fallback gracefully
        }
    )
}

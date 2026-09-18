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
                        text = if (!locality.isNullOrBlank()) "Map · $locality" else "Nearby business map",
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

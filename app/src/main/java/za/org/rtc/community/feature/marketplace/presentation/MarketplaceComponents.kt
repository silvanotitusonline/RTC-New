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

@Composable
fun <T> MarketplaceLoadContainer(
    state: MarketplaceLoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is MarketplaceLoadState.Idle,
        is MarketplaceLoadState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is MarketplaceLoadState.Failure -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    modifier = Modifier.padding(RtcSpacing.pageGutter)
                ) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        is MarketplaceLoadState.Data -> {
            Box(modifier = modifier) {
                content(state.value)
            }
        }
    }
}

/**
 * Interactive Google Maps-styled visual map component for the "Businesses Near Me" section
 * and dedicated Map Discovery screen.
 */

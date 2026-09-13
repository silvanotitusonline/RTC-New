package za.org.rtc.remediation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import za.org.rtc.remediation.presentation.DashboardSummary

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** Fixed skeleton geometry avoids feed movement while loading. Native Compose animation
 * follows the system animation duration scale; no archived shimmer dependency is required. */
@Composable
fun LoadingCards(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Loading shimmer")
    val position by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "Shimmer position",
    )
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Loading community content" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(3) {
            Card(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(196.dp)) {
                    val x = size.width * position
                    drawRect(Brush.linearGradient(
                        listOf(surface, highlight, surface),
                        start = Offset(x - size.width, 0f), end = Offset(x, size.height),
                    ))
                }
            }
        }
    }
}

@Composable
fun RemotePhoto(
    imageUrl: String?,
    imageLoader: ImageLoader,
    description: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.5f,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (imageUrl.isNullOrBlank()) return
    var retry by remember(imageUrl) { mutableIntStateOf(0) }
    var loading by remember(imageUrl, retry) { mutableStateOf(true) }
    var failed by remember(imageUrl, retry) { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        key(imageUrl, retry) {
            AsyncImage(
                model = imageUrl, imageLoader = imageLoader, contentDescription = description,
                modifier = Modifier.fillMaxSize(), contentScale = contentScale,
                onLoading = { loading = true; failed = false },
                onSuccess = { loading = false; failed = false },
                onError = { loading = false; failed = true },
            )
        }
        if (loading) CircularProgressIndicator(Modifier.size(28.dp))
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Image unavailable")
                TextButton(onClick = { retry++ }) { Text("Retry image") }
            }
        }
    }
}

/** Both the ring and rows receive this single repository-derived summary instance. */
@Composable
fun DashboardCard(summary: DashboardSummary, modifier: Modifier = Modifier) {
    val pending = MaterialTheme.colorScheme.tertiary
    val working = MaterialTheme.colorScheme.primary
    val resolved = androidx.compose.ui.graphics.Color(0xFF237B4B)
    val neutral = MaterialTheme.colorScheme.outlineVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("My reports", style = MaterialTheme.typography.titleLarge)
            Box(
                Modifier.size(160.dp).align(Alignment.CenterHorizontally)
                    .semantics { contentDescription = "${summary.total} reports: ${summary.pending} pending, ${summary.working} in progress, ${summary.resolved} resolved" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt)
                    drawArc(neutral, -90f, 360f, false, style = stroke)
                    if (summary.total > 0) {
                        var start = -90f
                        listOf(summary.pending to pending, summary.working to working, summary.resolved to resolved)
                            .forEach { (count, color) ->
                                val sweep = count.toFloat() / summary.total * 360f
                                if (count > 0) drawArc(color, start, sweep, false, style = stroke)
                                start += sweep
                            }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(summary.total.toString(), style = MaterialTheme.typography.headlineLarge)
                    Text(if (summary.total == 0) "No reports" else "Total")
                }
            }
            listOf("Pending" to summary.pending, "In progress" to summary.working, "Resolved" to summary.resolved)
                .forEach { (label, count) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label)
                        Text(count.toString())
                    }
                }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleSmall)
                Text(summary.total.toString(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import za.org.rtc.community.core.maps.LiveMapPanel
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard

@Composable
internal fun MarketplaceNearMeMapView(
    data: MarketplaceMapData,
    modifier: Modifier = Modifier,
    isSaved: (String) -> Boolean = { false },
    onToggleSave: ((String) -> Unit)? = null,
    onBusinessClick: (MarketplaceBusinessCard) -> Unit,
) {
    val businesses = remember(data.listings) { data.listings.map { it.business }.distinctBy { it.id } }
    Column(modifier) {
        LiveMapPanel(
            markers = data.listings.map { it.marker },
            onOpenMarker = { markerId ->
                data.listings.firstOrNull { it.marker.id == markerId }?.business?.let(onBusinessClick)
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        if (data.missingLocationCount > 0) {
            Text("${data.missingLocationCount} businesses have no published map location.", style = MaterialTheme.typography.bodySmall)
        }
        if (data.omittedCount > 0) {
            Text("Showing locations for the first 60 results. Refine your area to find more.", style = MaterialTheme.typography.bodySmall)
        }
        if (businesses.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(businesses, key = { it.id }) { business ->
                    Card(modifier = Modifier.width(240.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(business.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onBusinessClick(business) }) { Text("Open business") }
                                onToggleSave?.let { toggle ->
                                    IconButton(onClick = { toggle(business.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            if (isSaved(business.id)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                            contentDescription = if (isSaved(business.id)) "Unsave business" else "Save business",
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

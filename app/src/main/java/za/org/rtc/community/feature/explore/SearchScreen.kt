package za.org.rtc.community.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.PublicSearchResult
import za.org.rtc.community.ui.components.PurposefulEmptyState
import za.org.rtc.community.ui.components.RtcDirectoryCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.components.RtcNoResultsFound
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSearchField
import za.org.rtc.community.ui.components.parallaxHeader
import za.org.rtc.community.ui.components.parallaxScrollItem
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun SearchScreen(
    viewModel: RtcViewModel,
    onOpenResult: (PublicSearchResult) -> Unit,
) {
    val results by viewModel.publicSearchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLiveContentLoading.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.trim().length < 2) viewModel.clearPublicSearch()
        else { delay(300); viewModel.searchPublicContent(query) }
    }
    RtcScreenScaffold {
        item {
            Box(modifier = Modifier.parallaxHeader(rate = 0.35f)) {
                RtcSectionHeader("Search")
            }
        }
        item {
            RtcSearchField(value = query, onValueChange = { query = it }, placeholder = "Search projects, centres, opportunities, notices and Community")
        }
        if (isLoading && query.trim().length >= 2) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (query.trim().length >= 2 && !isLoading && results.isEmpty()) item {
            RtcNoResultsFound(
                searchQuery = query,
                actionLabel = "Clear search",
                onAction = { query = "" }
            )
        }
        results.groupBy { it.resultType }.forEach { (type, grouped) ->
            item { RtcSectionHeader(searchTypeLabel(type)) }
            itemsIndexed(grouped, key = { _, it -> it.resultId }) { index, result ->
                RtcDirectoryCard(
                    title = result.title,
                    description = result.summary,
                    modifier = Modifier.parallaxScrollItem(index = index + 3, rate = 0.05f),
                ) { onOpenResult(result) }
            }
        }
    }
}

internal fun searchTypeLabel(type: String): String = when (type) {
    "PROJECT" -> "Projects"
    "CENTRE" -> "Centres"
    "OPPORTUNITY" -> "Opportunities"
    "NOTICE" -> "Notices"
    "HELP" -> "Help Centre"
    "COMMUNITY" -> "Community"
    else -> "Published content"
}

@Composable
internal fun SearchResultDetailScreen(result: PublicSearchResult?, onBack: () -> Unit) {
    if (result == null) {
        PurposefulEmptyState("This published result is no longer available.", "Back to search", onBack)
        return
    }
    LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
        item {
            Text(searchTypeLabel(result.resultType), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        item { Text(result.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(result.summary, style = MaterialTheme.typography.bodyLarge) }
        result.publishedAt?.let { publishedAt -> item { Text("Published or updated: ${publishedAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to search") } }
    }
}

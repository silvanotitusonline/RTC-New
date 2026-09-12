package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.feature.marketplace.domain.MarketplaceSearchFilters
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun MarketplaceSearchRoute(
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val origin by viewModel.origin.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(searchState.filters.query) }
    var verified by rememberSaveable { mutableStateOf(searchState.filters.verifiedOnly) }
    var selectedSort by rememberSaveable { mutableStateOf(searchState.filters.sort) }
    var categoryId by rememberSaveable { mutableStateOf(searchState.filters.categoryId) }
    var minimumRating by rememberSaveable { mutableStateOf(searchState.filters.minimumRating) }
    var radius by rememberSaveable { mutableFloatStateOf(searchState.filters.radiusMetres.toFloat()) }

    LaunchedEffect(Unit) { viewModel.loadHome() }
    LaunchedEffect(query, verified, selectedSort, categoryId, minimumRating, radius) {
        viewModel.updateSearchFilters(
            MarketplaceSearchFilters(
                query = query,
                categoryId = categoryId,
                minimumRating = minimumRating,
                verifiedOnly = verified,
                sort = selectedSort,
                radiusMetres = radius.toInt(),
            )
        )
    }

    val categories = ((homeState as? MarketplaceLoadState.Data<MarketplaceHome>)?.value?.categories).orEmpty()
    Column(
        Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
    ) {
        Text("Search Marketplace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Businesses, services or categories") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
        ) {
            FilterChip(selected = verified, onClick = { verified = !verified }, label = { Text("Verified only") })
            listOf<Double?>(null, 3.0, 4.0, 4.5).forEach { rating ->
                FilterChip(
                    selected = minimumRating == rating,
                    onClick = { minimumRating = rating },
                    label = { Text(rating?.let { "$it+ stars" } ?: "Any rating") },
                )
            }
        }
        if (categories.isNotEmpty()) {
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("All") })
                categories.forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                    )
                }
            }
        }
        Text("Sort", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
        ) {
            listOf("RECOMMENDED", "DISTANCE", "TOP_RATED", "NEWEST", "NAME").forEach { sort ->
                FilterChip(
                    selected = selectedSort == sort,
                    onClick = { selectedSort = sort },
                    label = { Text(sort.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        if (origin != null) {
            Text("Search radius · ${(radius / 1000).toInt()} km", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = radius,
                onValueChange = { radius = it.coerceIn(1_000f, 50_000f) },
                valueRange = 1_000f..50_000f,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Radius filtering becomes available after you enable location from Marketplace Home.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.listGap),
                contentPadding = PaddingValues(bottom = RtcSpacing.sectionGap),
            ) {
                if (searchState.isRefreshing && searchState.items.isEmpty()) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                searchState.errorMessage?.let { message ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                                Text(message, color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = viewModel::retrySearch, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Retry") }
                            }
                        }
                    }
                }
                if (!searchState.isRefreshing && searchState.items.isEmpty() && searchState.errorMessage == null) {
                    item { Text("No published businesses match these filters.") }
                }
                items(searchState.items, key = { it.id }) { business ->
                    MarketplaceBusinessCardView(business) { onNavigate("community/marketplace/business/${business.slug}") }
                }
                if (searchState.hasMore && searchState.items.isNotEmpty()) {
                    item {
                        Button(
                            onClick = { viewModel.loadMore() },
                            enabled = !searchState.isLoadingMore,
                            modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget),
                        ) { Text(if (searchState.isLoadingMore) "Loading…" else "Load more") }
                    }
                }
            }
        }
    }
}

package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun MarketplaceHomeRoute(
    onNavigate: (String) -> Unit,
    onSwitchToServices: () -> Unit,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val area by viewModel.area.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state is MarketplaceLoadState.Idle) viewModel.loadHome()
    }

    MarketplaceHomeScreen(
        state = state,
        area = area,
        onRetry = viewModel::loadHome,
        onSearch = { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
        onMap = { onNavigate(RtcRoute.MARKETPLACE_MAP) },
        onSwitchToServices = onSwitchToServices,
        onOpenBusiness = { businessId -> onNavigate(RtcRoute.marketplaceBusiness(businessId)) },
    )
}

@Composable
internal fun MarketplaceHomeScreen(
    state: MarketplaceLoadState<MarketplaceHome>,
    area: String?,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onMap: () -> Unit,
    onSwitchToServices: () -> Unit,
    onOpenBusiness: (String) -> Unit,
) {
    when (state) {
        MarketplaceLoadState.Idle,
        MarketplaceLoadState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading trusted local businesses…",
                modifier = Modifier.padding(top = RtcSpacing.small),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is MarketplaceLoadState.Failure -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(RtcSpacing.pageGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Marketplace could not be loaded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.message,
                modifier = Modifier.padding(top = RtcSpacing.compact, bottom = RtcSpacing.standard),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("Try again") }
        }

        is MarketplaceLoadState.Data -> MarketplaceHomeContent(
            home = state.value,
            area = area,
            onSearch = onSearch,
            onMap = onMap,
            onSwitchToServices = onSwitchToServices,
            onOpenBusiness = onOpenBusiness,
        )
    }
}

@Composable
private fun MarketplaceHomeContent(
    home: MarketplaceHome,
    area: String?,
    onSearch: () -> Unit,
    onMap: () -> Unit,
    onSwitchToServices: () -> Unit,
    onOpenBusiness: (String) -> Unit,
) {
    val featured = (home.featured + home.topRated + home.newest).distinctBy { it.id }.take(8)
    val nearby = home.nearby.distinctBy { it.id }.take(10)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = RtcSpacing.pageGutter,
            vertical = RtcSpacing.contentGroup,
        ),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                Text(
                    text = "Marketplace",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = area?.let { "Discover trusted businesses in $it" }
                        ?: "Discover trusted businesses in your community",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
            ) {
                Button(onClick = onSearch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text(" Search")
                }
                OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Text(" Map")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                onClick = onSwitchToServices,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(RtcSpacing.standard),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small),
                ) {
                    Icon(Icons.Filled.Storefront, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("Need to book a service?", fontWeight = FontWeight.Bold)
                        Text(
                            "Open Service Centre to request and manage local services.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (home.categories.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("Browse categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        items(home.categories, key = { it.id }) { category ->
                            AssistChip(
                                onClick = onSearch,
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        }

        if (featured.isNotEmpty()) {
            item { Text("Featured near you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            itemsIndexed(featured, key = { _, business -> business.id }) { index, business ->
                AppStoreBusinessRowCard(
                    business = business,
                    rankingNumber = index + 1,
                    onOpen = { onOpenBusiness(business.slug.ifBlank { business.id }) },
                )
            }
        }

        if (nearby.isNotEmpty()) {
            item { Text("Nearby", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(nearby, key = { it.id }) { business ->
                AppStoreBusinessRowCard(
                    business = business,
                    onOpen = { onOpenBusiness(business.slug.ifBlank { business.id }) },
                )
            }
        }

        if (featured.isEmpty() && nearby.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(RtcSpacing.standard),
                        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        Text("No businesses to show yet", fontWeight = FontWeight.Bold)
                        Text(
                            "Try search, change your area, or check again later.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onSearch) { Text("Search Marketplace") }
                    }
                }
            }
        }
    }
}

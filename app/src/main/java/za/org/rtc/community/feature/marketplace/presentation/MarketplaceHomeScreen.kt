package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun MarketplaceHomeScreen(
    onNavigate: (String) -> Unit,
    onSwitchToServices: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    MarketplaceHomeRoute(
        onNavigate = onNavigate,
        onSwitchToServices = onSwitchToServices,
        modifier = modifier,
        viewModel = viewModel,
    )
}

@Composable
fun MarketplaceHomeRoute(
    onNavigate: (String) -> Unit,
    onSwitchToServices: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val homeState by viewModel.home.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadHome() }

    MarketplaceLoadContainer(
        state = homeState,
        onRetry = viewModel::loadHome,
        modifier = modifier.testTag("marketplace_home_screen"),
    ) { home: MarketplaceHome ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = RtcSpacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RtcSpacing.pageGutter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Marketplace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Discover trusted local businesses & services", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (home.categories.isNotEmpty()) {
                item {
                    AppStoreSectionHeader(
                        title = "Categories",
                        onSeeAll = { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = RtcSpacing.pageGutter),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        items(home.categories, key = { it.id }) { category ->
                            FilterChip(
                                selected = false,
                                onClick = { onNavigate("${RtcRoute.MARKETPLACE_SEARCH}?category=${category.slug}") },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }

            if (home.featured.isNotEmpty()) {
                item {
                    AppStoreSectionHeader(
                        title = "Featured Businesses",
                        subtitle = "Hand-picked local experts",
                        onSeeAll = { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = RtcSpacing.pageGutter),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                    ) {
                        items(home.featured, key = { it.id }) { business ->
                            AppStoreMediumCard(
                                business = business,
                                onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                            )
                        }
                    }
                }
            }

            if (home.topRated.isNotEmpty()) {
                item {
                    AppStoreSectionHeader(
                        title = "Top Rated in Your Area",
                        onSeeAll = { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
                    )
                }
                items(home.topRated.take(5), key = { it.id }) { business ->
                    AppStoreBusinessRowCard(
                        business = business,
                        modifier = Modifier.padding(horizontal = RtcSpacing.pageGutter),
                        onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                    )
                }
            }
        }
    }
}

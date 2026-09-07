package za.org.rtc.community.feature.marketplace.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun MarketplaceHomeRoute(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwitchToServices: (() -> Unit)? = null,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val area by viewModel.area.collectAsStateWithLifecycle()
    val sessionState by viewModel.session.collectAsStateWithLifecycle()
    val savedState by viewModel.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        ) {
            viewModel.useMyLocation()
        }
    }
    val onUseMyLocation = {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (coarse || fine) {
            viewModel.useMyLocation()
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadHome()
        viewModel.loadSaved()
    }

    val savedBusinesses = remember(savedState) {
        (savedState as? MarketplaceLoadState.Data)?.value.orEmpty()
    }
    val savedIds = remember(savedBusinesses) { savedBusinesses.map { it.id }.toSet() }

    MarketplaceLoadContainer(state, viewModel::loadHome) { home ->
        MarketplaceHomeContent(
            home = home,
            area = area,
            declaredLocality = sessionState.declaredLocality,
            savedIds = savedIds,
            savedBusinesses = savedBusinesses,
            modifier = modifier,
            onToggleSave = { businessId ->
                val nextSaved = businessId !in savedIds
                viewModel.toggleSaved(businessId, nextSaved)
                viewModel.loadSaved()
            },
            onSetArea = viewModel::setArea,
            onUseMyLocation = onUseMyLocation,
            onNavigate = onNavigate,
            onSwitchToServices = onSwitchToServices,
        )
    }
}

@Composable
private fun MarketplaceHomeContent(
    home: MarketplaceHome,
    area: String?,
    declaredLocality: String?,
    savedIds: Set<String>,
    savedBusinesses: List<MarketplaceBusinessCard>,
    modifier: Modifier = Modifier,
    onToggleSave: (String) -> Unit,
    onSetArea: (String?) -> Unit,
    onUseMyLocation: () -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchToServices: (() -> Unit)?,
) {
    var activeTab by rememberSaveable { mutableStateOf("Discover") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var areaText by rememberSaveable(area) { mutableStateOf(area.orEmpty()) }

    val allBusinesses = remember(home) {
        (home.featured + home.nearby + home.topRated + home.newest).distinctBy { it.id }
    }
    val visibleBusinesses = remember(allBusinesses, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            allBusinesses
        } else {
            allBusinesses.filter { card ->
                card.displayName.contains(query, ignoreCase = true) ||
                    card.tagline.contains(query, ignoreCase = true) ||
                    card.category.contains(query, ignoreCase = true) ||
                    card.locality.contains(query, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = RtcSpacing.pageGutter,
            vertical = RtcSpacing.contentGroup,
        ),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                Text(
                    text = "Marketplace",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Discover trusted local businesses and service providers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                ) {
                    AssistChip(
                        onClick = { onNavigate(RtcRoute.MARKETPLACE_SAVED) },
                        label = { Text("Saved") },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { onNavigate(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                        label = { Text("My businesses") },
                        leadingIcon = { Icon(Icons.Filled.BusinessCenter, contentDescription = null) },
                    )
                    onSwitchToServices?.let { switchToServices ->
                        AssistChip(
                            onClick = switchToServices,
                            label = { Text("Open Service Centre") },
                            leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Marketplace") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                listOf("Discover", "Saved", "My Business").forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab) },
                    )
                }
            }
        }

        when (activeTab) {
            "Saved" -> {
                if (savedBusinesses.isEmpty()) {
                    item {
                        MarketplaceHomeEmptyCard(
                            title = "No saved businesses yet",
                            body = "Bookmark a business while browsing and it will appear here.",
                        )
                    }
                } else {
                    items(savedBusinesses, key = { it.id }) { business ->
                        MarketplaceBusinessCardView(
                            business = business,
                            isSaved = true,
                            onToggleSave = { onToggleSave(business.id) },
                        ) { onNavigate("community/marketplace/business/${business.slug}") }
                    }
                }
            }

            "My Business" -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(RtcSpacing.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
                        ) {
                            Text("Manage your Marketplace presence", fontWeight = FontWeight.Bold)
                            Text("Update listings, operating hours, services, media, and publication status.")
                            Button(
                                onClick = { onNavigate(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                                modifier = Modifier.height(RtcSize.minimumTouchTarget),
                            ) { Text("Manage My Businesses") }
                        }
                    }
                }
            }

            else -> {
                if (visibleBusinesses.isEmpty()) {
                    item {
                        MarketplaceHomeEmptyCard(
                            title = "No matching businesses",
                            body = "Try another name, category, or locality.",
                        )
                    }
                } else {
                    item {
                        Text(
                            text = if (searchQuery.isBlank()) "Recommended near you" else "Search results",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(visibleBusinesses, key = { it.id }) { business ->
                        MarketplaceBusinessCardView(
                            business = business,
                            isSaved = business.id in savedIds,
                            onToggleSave = { onToggleSave(business.id) },
                        ) { onNavigate("community/marketplace/business/${business.slug}") }
                    }
                }

                if (home.categories.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
                            Text("Browse categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                            ) {
                                home.categories.forEach { category ->
                                    AssistChip(
                                        onClick = {
                                            onNavigate("${RtcRoute.MARKETPLACE_SEARCH}?categoryId=${category.id}")
                                        },
                                        label = { Text(category.name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Locality", fontWeight = FontWeight.Bold)
                            Text(
                                declaredLocality?.let { "Account locality: $it" }
                                    ?: "Set an area or use device location for proximity ranking.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = areaText,
                        onValueChange = { areaText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Area or locality") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                    ) {
                        Button(
                            onClick = { onSetArea(areaText) },
                            modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
                        ) { Text("Set area") }
                        OutlinedButton(
                            onClick = onUseMyLocation,
                            modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = RtcSpacing.opticalCorrection))
                            Text("Use GPS")
                        }
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                OutlinedButton(
                    onClick = { onNavigate(RtcRoute.MARKETPLACE_BUSINESS_NEW) },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Storefront, contentDescription = null)
                    Text("List a Business")
                }
                OutlinedButton(
                    onClick = { onNavigate(RtcRoute.MARKETPLACE_MAP) },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Text("Open Map")
                }
            }
        }
    }
}

@Composable
private fun MarketplaceHomeEmptyCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(RtcSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

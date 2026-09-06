package za.org.rtc.community.feature.marketplace.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.ui.components.RtcNoResultsFound
import java.text.SimpleDateFormat
import java.util.*
import za.org.rtc.community.core.map.tomtom.MarketplaceMapGateway
import za.org.rtc.community.core.map.tomtom.UnavailableTomTomMarketplaceGateway
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
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true || granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.useMyLocation()
        }
    }
    val onUseMyLocation = {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarse || fine) viewModel.useMyLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) {
        viewModel.loadHome()
        viewModel.loadSaved()
    }
    val savedBusinesses = remember(savedState) {
        (savedState as? MarketplaceLoadState.Data)?.value.orEmpty()
    }
    val savedIds = remember(savedBusinesses) {
        savedBusinesses.map { it.id }.toSet()
    }
    val onToggleSave = { businessId: String ->
        val isSaved = savedIds.contains(businessId)
        viewModel.toggleSaved(businessId, !isSaved)
        viewModel.loadSaved()
        Unit
    }
    MarketplaceLoadContainer(state, viewModel::loadHome) { home ->
        MarketplaceHomeContent(
            home = home,
            area = area,
            session = sessionState,
            savedIds = savedIds,
            savedBusinesses = savedBusinesses,
            modifier = modifier,
            onToggleSave = onToggleSave,
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
    session: za.org.rtc.community.core.RtcSession,
    savedIds: Set<String>,
    savedBusinesses: List<MarketplaceBusinessCard>,
    modifier: Modifier = Modifier,
    onToggleSave: (String) -> Unit,
    onSetArea: (String?) -> Unit,
    onUseMyLocation: () -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchToServices: (() -> Unit)? = null,
) {
    var activeFeedTab by rememberSaveable { mutableStateOf("Discover") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showTrendingSearches by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var nearMeViewMode by rememberSaveable { mutableStateOf("Cards") }
    var areaText by rememberSaveable(area) { mutableStateOf(area.orEmpty()) }
    val todayDateFormatted = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()).uppercase()
    }

    // Filter businesses by active category tab and search query
    val matchesCategory: (MarketplaceBusinessCard) -> Boolean = { card ->
        when (selectedCategory) {
            "All" -> true
            "Restaurants & Food" -> card.category.contains("Food", ignoreCase = true) || card.category.contains("Restaur", ignoreCase = true) || card.category.contains("Bake", ignoreCase = true) || card.category.contains("Caf", ignoreCase = true) || card.category.contains("Deli", ignoreCase = true)
            "Retail & Shops" -> card.category.contains("Retail", ignoreCase = true) || card.category.contains("Shop", ignoreCase = true) || card.category.contains("Fashion", ignoreCase = true) || card.category.contains("Boutique", ignoreCase = true) || card.category.contains("Store", ignoreCase = true)
            "Services & Trades" -> card.category.contains("Service", ignoreCase = true) || card.category.contains("Trade", ignoreCase = true) || card.category.contains("Handyman", ignoreCase = true) || card.category.contains("Plumb", ignoreCase = true) || card.category.contains("Elect", ignoreCase = true)
            "Health & Wellness" -> card.category.contains("Health", ignoreCase = true) || card.category.contains("Physio", ignoreCase = true) || card.category.contains("Well", ignoreCase = true) || card.category.contains("Spa", ignoreCase = true)
            "Tech & Electronics" -> card.category.contains("Tech", ignoreCase = true) || card.category.contains("Repair", ignoreCase = true) || card.category.contains("Gadget", ignoreCase = true) || card.category.contains("Comput", ignoreCase = true)
            "Auto & Mechanical" -> card.category.contains("Auto", ignoreCase = true) || card.category.contains("Mechanic", ignoreCase = true) || card.category.contains("Car", ignoreCase = true)
            "Home & Garden" -> card.category.contains("Home", ignoreCase = true) || card.category.contains("Garden", ignoreCase = true) || card.category.contains("Landscap", ignoreCase = true)
            "Groceries & Fresh" -> card.category.contains("Grocer", ignoreCase = true) || card.category.contains("Fresh", ignoreCase = true) || card.category.contains("Market", ignoreCase = true)
            else -> card.category.equals(selectedCategory, ignoreCase = true)
        }
    }

    val matchesSearch: (MarketplaceBusinessCard) -> Boolean = { card ->
        if (searchQuery.isBlank()) true
        else {
            val q = searchQuery.trim().lowercase()
            card.displayName.lowercase().contains(q) ||
            card.tagline.lowercase().contains(q) ||
            card.category.lowercase().contains(q) ||
            card.locality.lowercase().contains(q)
        }
    }

    val filterCard: (MarketplaceBusinessCard) -> Boolean = { card ->
        matchesCategory(card) && matchesSearch(card)
    }

    val allUniqueBusinesses = remember(home) {
        (home.featured + home.topRated + home.nearby + home.newest).distinctBy { it.id }
    }
    val searchResults = remember(allUniqueBusinesses, searchQuery, selectedCategory) {
        if (searchQuery.isBlank()) emptyList()
        else allUniqueBusinesses.filter(filterCard)
    }

    val filteredFeatured = remember(home.featured, selectedCategory, searchQuery) { home.featured.filter(filterCard) }
    val filteredTrending = remember(home.topRated, selectedCategory, searchQuery) { home.topRated.filter(filterCard) }
    val filteredNearby = remember(home.nearby, selectedCategory, searchQuery) { home.nearby.filter(filterCard) }
    val filteredNewest = remember(home.newest, selectedCategory, searchQuery) { home.newest.filter(filterCard) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("marketplace_home_content"),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // App Store Today Style Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = todayDateFormatted,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                        Text(
                            text = "Marketplace",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                            ),
                        )
                    }

                    // Top Action Icons (Saved bookmark, My Businesses, & Register business)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onNavigate(RtcRoute.MARKETPLACE_SAVED) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (savedIds.isNotEmpty()) {
                                        Badge { Text(savedIds.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmark,
                                    contentDescription = "Saved Businesses",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        IconButton(
                            onClick = { onNavigate(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = "Manage My Businesses",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        IconButton(
                            onClick = { onNavigate(RtcRoute.MARKETPLACE_BUSINESS_NEW) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddBusiness,
                                contentDescription = "List Your Business",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "Services, shops, and local businesses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // App Store Search Bar at Top of Marketplace
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isBlank()) showTrendingSearches = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                showTrendingSearches = true
                            }
                        }
                        .testTag("marketplace_search_input"),
                    placeholder = {
                        Text(
                            text = "Search businesses or services…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        showTrendingSearches = true
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Advanced Search & Radius",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    singleLine = true,
                )

                if (showTrendingSearches) {
                    TrendingSearchesPanel(
                        onSelectTag = { tag ->
                            searchQuery = tag
                            showTrendingSearches = false
                        },
                        onDismiss = {
                            showTrendingSearches = false
                        },
                    )
                }
            }
        }

        // Sub-Tab Switcher: "Discover", "Saved", and "My Business"
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { activeFeedTab = "Discover" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeFeedTab == "Discover") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = null,
                            tint = if (activeFeedTab == "Discover") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Discover",
                            fontWeight = FontWeight.Bold,
                            color = if (activeFeedTab == "Discover") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Surface(
                    onClick = { activeFeedTab = "Saved" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeFeedTab == "Saved") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = if (activeFeedTab == "Saved") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (savedIds.isNotEmpty()) "Saved (${savedIds.size})" else "Saved",
                            fontWeight = FontWeight.Bold,
                            color = if (activeFeedTab == "Saved") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Surface(
                    onClick = { activeFeedTab = "My Business" },
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeFeedTab == "My Business") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = if (activeFeedTab == "My Business") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "My Business",
                            fontWeight = FontWeight.Bold,
                            color = if (activeFeedTab == "My Business") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        if (activeFeedTab == "My Business") {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Marketplace Business Profile Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Manage your listings, update business details, edit operating hours, and track customer engagement.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                onClick = { onNavigate(RtcRoute.MARKETPLACE_MY_BUSINESSES) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Business,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("My Business Listings", fontWeight = FontWeight.SemiBold)
                                        Text("View, edit details, update photos, operating hours & services", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                onClick = { onNavigate(RtcRoute.MARKETPLACE_BUSINESS_NEW) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddBusiness,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Create New Business Profile", fontWeight = FontWeight.SemiBold)
                                        Text("Register a new shop, trade, or local enterprise listing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                onClick = { onNavigate(RtcRoute.MARKETPLACE_INVITATIONS) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GroupAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Team Co-Management Invitations", fontWeight = FontWeight.SemiBold)
                                        Text("Accept or reject team requests to manage business listings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                onClick = { onNavigate(RtcRoute.MARKETPLACE_MY_REVIEWS) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RateReview,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Customer Reviews & Responses", fontWeight = FontWeight.SemiBold)
                                        Text("Manage customer feedback and publish official owner responses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeFeedTab == "Saved") {
            if (savedBusinesses.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Text(
                            text = "No Saved Businesses Yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Tap the bookmark icon on any business profile to save your favorite local shops and providers here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Button(
                            onClick = { activeFeedTab = "Discover" },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Explore Marketplace")
                        }
                    }
                }
            } else {
                items(savedBusinesses, key = { it.id }) { business ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val cardScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.965f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "savedCardScale",
                    )
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                AppStoreSquircleLogo(
                                    name = business.displayName,
                                    category = business.category,
                                    size = 56.dp,
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
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        if (business.verified) {
                                            Icon(
                                                imageVector = Icons.Filled.Verified,
                                                contentDescription = "Verified",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${business.category} · ${business.locality}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "%.1f".format(business.ratingAverage),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        )
                                        Text(
                                            text = "(${business.reviewCount} reviews)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AppStoreGetButton(
                                        text = "OPEN",
                                        onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                    )

                                    IconButton(
                                        onClick = { onToggleSave(business.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bookmark,
                                            contentDescription = "Remove bookmark",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // DISCOVER TAB CONTENT

            // Real-Time Search Results Section (if search query is entered)
            if (searchQuery.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "SEARCH RESULTS",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = if (searchResults.isNotEmpty()) "${searchResults.size} businesses matching \"$searchQuery\"" else "No matching results",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (searchResults.isNotEmpty()) {
                                TextButton(onClick = { searchQuery = "" }) {
                                    Text("Clear")
                                }
                            }
                        }

                        if (searchResults.isEmpty()) {
                            RtcNoResultsFound(
                                searchQuery = searchQuery,
                                message = "Try searching for a different keyword, category or service name.",
                                actionLabel = "Clear search",
                                onAction = { searchQuery = "" }
                            )
                        } else {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    searchResults.forEachIndexed { index, business ->
                                        AppStoreBusinessRowCard(
                                            business = business,
                                            onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                            onGetClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                        )
                                        if (index < searchResults.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 80.dp, end = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // App Store Category Tabs Strip (Horizontal Chips)
            item {
                val categories = listOf(
                    "All" to Icons.Filled.Apps,
                    "Restaurants & Food" to Icons.Filled.Restaurant,
                    "Retail & Shops" to Icons.Filled.ShoppingBag,
                    "Services & Trades" to Icons.Filled.Handyman,
                    "Health & Wellness" to Icons.Filled.Spa,
                    "Tech & Electronics" to Icons.Filled.Devices,
                    "Auto & Mechanical" to Icons.Filled.DirectionsCar,
                    "Home & Garden" to Icons.Filled.Yard,
                    "Groceries & Fresh" to Icons.Filled.LocalGroceryStore,
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it.first }) { (categoryName, icon) ->
                        val isSelected = selectedCategory == categoryName
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = categoryName },
                            label = {
                                Text(
                                    text = categoryName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                        )
                    }
                }
            }

            if (selectedCategory != "All") {
                val totalCategoryMatches = (filteredFeatured + filteredTrending + filteredNearby + filteredNewest).distinctBy { it.id }.size
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "FILTERED BY:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "$selectedCategory ($totalCategoryMatches)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                        TextButton(onClick = { selectedCategory = "All" }) {
                            Text("Show All")
                        }
                    }
                }

                if (totalCategoryMatches == 0) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = "No businesses listed under $selectedCategory",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "Be the first local provider to list your business in this category!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Button(
                                    onClick = { selectedCategory = "All" },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Reset Category Filter")
                                }
                            }
                        }
                    }
                }
            }

            // Spotlight Hero (Featured Business / Provider)
            if (filteredFeatured.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "FEATURED SPOTLIGHT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                ),
                            )
                            Text(
                                text = "See All",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(filteredFeatured, key = { it.id }) { business ->
                                Box(modifier = Modifier.width(320.dp)) {
                                    AppStoreSpotlightHero(
                                        business = business,
                                        onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                        onGetClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // App Store Top Charts / Trending
            if (filteredTrending.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Trending Businesses & Shops",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "Most requested in your community this week",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "Top Charts",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onNavigate(RtcRoute.MARKETPLACE_SEARCH) },
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                filteredTrending.take(4).forEachIndexed { index, business ->
                                    AppStoreBusinessRowCard(
                                        business = business,
                                        rank = index + 1,
                                        onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                        onGetClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                    )
                                    if (index < filteredTrending.take(4).size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 80.dp, end = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Businesses Near You (With Embedded Map View & Cards Switcher)
            if (filteredNearby.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = if (area.isNullOrBlank()) "Businesses Near You" else "Near You in $area",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "Verified neighborhood providers ready to assist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // View Switcher (Cards vs Map)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Surface(
                                    onClick = { nearMeViewMode = "Cards" },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (nearMeViewMode == "Cards") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.ViewAgenda,
                                            contentDescription = "Card List",
                                            tint = if (nearMeViewMode == "Cards") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { nearMeViewMode = "Map" },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (nearMeViewMode == "Map") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Map,
                                            contentDescription = "Map View",
                                            tint = if (nearMeViewMode == "Map") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Display either interactive Map View or Horizontal Cards Carousel
                        if (nearMeViewMode == "Map") {
                            MarketplaceNearMeMapView(
                                businesses = filteredNearby,
                                locality = area,
                                isSaved = { savedIds.contains(it) },
                                onToggleSave = onToggleSave,
                                onBusinessClick = { onNavigate("community/marketplace/business/${it.slug}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp),
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(filteredNearby, key = { it.id }) { business ->
                                    AppStoreMediumCard(
                                        business = business,
                                        isSaved = savedIds.contains(business.id),
                                        onToggleSave = { onToggleSave(business.id) },
                                        onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

        // New & Notable ("Just Joined")
        if (filteredNewest.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "New & Notable",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Fresh services, crafters and shops recently registered",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            filteredNewest.take(4).forEachIndexed { index, business ->
                                AppStoreBusinessRowCard(
                                    business = business,
                                    onClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                    onGetClick = { onNavigate("community/marketplace/business/${business.slug}") },
                                )
                                if (index < filteredNewest.take(4).size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 80.dp, end = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Locality Quick Switch & Account Preference Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Locality & Proximity Tuning",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text = if (!session.declaredLocality.isNullOrBlank()) {
                            "Showing results prioritized for your declared area: \"${session.declaredLocality}\"."
                        } else {
                            "Enter your neighborhood or tap GPS to find nearest verified service providers & shops."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = areaText,
                            onValueChange = { areaText = it },
                            placeholder = { Text("e.g. Cape Town, Randburg") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                        Button(
                            onClick = { onSetArea(areaText) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Set")
                        }
                    }

                    OutlinedButton(
                        onClick = onUseMyLocation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use My GPS Location")
                    }
                }
            }
        }

        // App Store Footer Action Cards (List Business & Interactive Map)
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    onClick = { onNavigate(RtcRoute.MARKETPLACE_BUSINESS_NEW) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Are you a business or service provider?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("List your offerings, build local trust, and get reviews.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                }

                Card(
                    onClick = { onNavigate(RtcRoute.MARKETPLACE_MAP) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Interactive Neighborhood Map", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Explore physical locations & radius in your community.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun MarketplaceMapRoute(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val area by viewModel.area.collectAsStateWithLifecycle()
    val savedState by viewModel.saved.collectAsStateWithLifecycle()

    val savedIds = remember(savedState) {
        (savedState as? MarketplaceLoadState.Data)?.value?.map { it.id }?.toSet().orEmpty()
    }
    val businesses = remember(state) {
        (state as? MarketplaceLoadState.Data)?.value?.let { home ->
            (home.nearby + home.featured + home.topRated + home.newest).distinctBy { it.id }
        }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Marketplace Map",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = if (area.isNullOrBlank()) "All registered neighborhood businesses" else "Showing locations in $area",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MarketplaceNearMeMapView(
            businesses = businesses,
            locality = area,
            isSaved = { savedIds.contains(it) },
            onToggleSave = { id -> viewModel.toggleSaved(id, !savedIds.contains(id)) },
            onBusinessClick = { onNavigate("community/marketplace/business/${it.slug}") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
fun MarketplaceSavedRoute(
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadSaved() }
    MarketplaceLoadContainer(state, viewModel::loadSaved) { saved ->
        LazyColumn(Modifier.fillMaxSize().padding(RtcSpacing.pageGutter), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
            item { Text("Saved businesses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            if (saved.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(RtcSpacing.pageGutter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                        ) {
                            Icon(
                                Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                "No Saved Businesses Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Bookmark trusted plumbers, electricians, caterers, and repair pros while browsing the Marketplace to access them quickly here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onNavigate("community/marketplace") },
                                modifier = Modifier.height(RtcSize.minimumTouchTarget),
                            ) {
                                Icon(Icons.Filled.Storefront, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Explore Marketplace")
                            }
                        }
                    }
                }
            }
            items(saved, key = { it.id }) { business ->
                MarketplaceBusinessCardView(
                    business = business,
                    isSaved = true,
                    onToggleSave = {
                        viewModel.toggleSaved(business.id, false)
                        viewModel.loadSaved()
                    },
                ) { onNavigate("community/marketplace/business/${business.slug}") }
            }
        }
    }
}

@Composable
fun MarketplaceInvitationsRoute(viewModel: MarketplaceOwnerViewModel = hiltViewModel()) {
    val state by viewModel.invitations.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadInvitations() }
    MarketplaceLoadContainer(state, viewModel::loadInvitations) { invitations ->
        LazyColumn(Modifier.fillMaxSize().padding(RtcSpacing.pageGutter), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
            item { Text("Business team invitations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            if (invitations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(RtcSpacing.pageGutter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                        ) {
                            Icon(
                                Icons.Filled.GroupAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                "No Pending Invitations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "When a business owner invites your account to co-manage their directory listing, respond to customer inquiries, or update opening hours, the team invite will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
            items(invitations, key = { it.id }) { invitation ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text(invitation.businessName, fontWeight = FontWeight.SemiBold)
                        Text("${invitation.role} · ${invitation.state}")
                        invitation.expiresAt?.let { Text("Expires $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Invitation acceptance remains governed by the backend invitation workflow.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendingSearchesPanel(
    onSelectTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val trendingCategories = remember {
        listOf(
            "Plumbing & Gas" to "🔥 Trending",
            "Electrical Repairs" to "⚡ Popular",
            "Restaurants & Food" to "🍽️ Active",
            "Auto Services" to "🚗 Top Demand",
            "Home Cleaning" to "✨ Verified",
            "Beauty & Wellness" to "💇 Recommended",
        )
    }

    val popularTags = remember {
        listOf(
            "#EmergencyRepair",
            "#VerifiedPro",
            "#OpenNow",
            "#SameDayBooking",
            "#LocalDiscount",
            "#24SevenService",
            "#SolarInstallation",
            "#Handyman",
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Trending Searches",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("Close", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                text = "Popular Business Categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                trendingCategories.forEach { (catName, badge) ->
                    Surface(
                        onClick = { onSelectTag(catName) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = "Popular Activity Tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                popularTags.forEach { tag ->
                    AssistChip(
                        onClick = { onSelectTag(tag.removePrefix("#")) },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    }
}



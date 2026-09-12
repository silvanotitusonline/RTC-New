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
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing


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

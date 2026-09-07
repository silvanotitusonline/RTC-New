package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReviewReportReason
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

// -------------------------------------------------------------
// 1. Marketplace Admin Flagged Reviews Moderation Screen
// -------------------------------------------------------------


data class AdminCategoryItem(
    val id: String,
    val name: String,
    val slug: String,
    val description: String,
    val iconName: String,
    val activeListingCount: Int,
    val isEnabled: Boolean,
    val isFeatured: Boolean,
)

@Composable
fun MarketplaceAdminCategoriesRoute(
    onBack: () -> Unit,
    discoveryViewModel: MarketplaceDiscoveryViewModel = hiltViewModel(),
) {
    val homeState by discoveryViewModel.home.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var actionNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { discoveryViewModel.loadHome() }

    var localCategories by remember {
        mutableStateOf(
            listOf(
                AdminCategoryItem("cat-1", "Plumbing & Drainage", "plumbing-drainage", "Emergency repairs, leak detection, geysers and pipes.", "plumbing", 42, isEnabled = true, isFeatured = true),
                AdminCategoryItem("cat-2", "Electrical & Solar", "electrical-solar", "Wiring, solar installations, inverter backups and certificates.", "bolt", 38, isEnabled = true, isFeatured = true),
                AdminCategoryItem("cat-3", "Automotive & Mechanical", "automotive-mechanical", "Panel beating, tyre repair, diagnostics and towing services.", "directions_car", 29, isEnabled = true, isFeatured = false),
                AdminCategoryItem("cat-4", "Home Cleaning & Hygiene", "home-cleaning", "Residential and commercial deep cleaning, carpet washing.", "cleaning_services", 35, isEnabled = true, isFeatured = true),
                AdminCategoryItem("cat-5", "Food & Catering", "food-catering", "Local restaurants, bakeries, event caterers and food stalls.", "restaurant", 54, isEnabled = true, isFeatured = true),
                AdminCategoryItem("cat-6", "Beauty & Hair Salons", "beauty-hair", "Braiding, barber services, nails, skincare and spa treatments.", "face", 46, isEnabled = true, isFeatured = false),
                AdminCategoryItem("cat-7", "Construction & Building", "construction-building", "Bricklaying, roofing, painting, paving and general renovations.", "construction", 31, isEnabled = true, isFeatured = false),
                AdminCategoryItem("cat-8", "Education & Tutoring", "education-tutoring", "Matric tutoring, driving schools, music lessons and language classes.", "school", 19, isEnabled = true, isFeatured = false),
            )
        )
    }

    val filteredCategories = remember(localCategories, searchQuery) {
        if (searchQuery.isBlank()) localCategories
        else localCategories.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.slug.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Admin")
                }
                Text(
                    text = "Category Taxonomy",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Manage marketplace industry categories, sub-topics, visibility toggles, and discover tags.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Search & Add Button Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Filter categories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                Button(
                    onClick = { showAddCategoryDialog = true },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        // Stats overview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Active Categories", style = MaterialTheme.typography.labelSmall)
                        Text("${localCategories.count { it.isEnabled }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Total Listings", style = MaterialTheme.typography.labelSmall)
                        Text("${localCategories.sumOf { it.activeListingCount }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Featured Topics", style = MaterialTheme.typography.labelSmall)
                        Text("${localCategories.count { it.isFeatured }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Category items
        items(filteredCategories, key = { it.id }) { category ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (category.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "slug: /${category.slug} · ${category.activeListingCount} published businesses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = category.isEnabled,
                            onCheckedChange = { checked ->
                                localCategories = localCategories.map {
                                    if (it.id == category.id) it.copy(isEnabled = checked) else it
                                }
                                actionNotice = if (checked) "Category '${category.name}' enabled." else "Category '${category.name}' hidden from browse."
                            },
                        )
                    }

                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = category.isFeatured,
                            onClick = {
                                localCategories = localCategories.map {
                                    if (it.id == category.id) it.copy(isFeatured = !category.isFeatured) else it
                                }
                                actionNotice = "Featured status updated for ${category.name}."
                            },
                            label = { Text(if (category.isFeatured) "⭐ Featured on Home" else "Standard Topic") },
                        )
                        Text(
                            text = "Icon: ${category.iconName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Add category dialog
    if (showAddCategoryDialog) {
        var newName by rememberSaveable { mutableStateOf("") }
        var newSlug by rememberSaveable { mutableStateOf("") }
        var newDesc by rememberSaveable { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Marketplace Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            if (newSlug.isBlank() || newSlug == it.dropLast(1).lowercase().replace(' ', '-')) {
                                newSlug = it.lowercase().replace(' ', '-').filter { ch -> ch.isLetterOrDigit() || ch == '-' }
                            }
                        },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newSlug,
                        onValueChange = { newSlug = it },
                        label = { Text("URL Slug (e.g. solar-power)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val newCat = AdminCategoryItem(
                                id = "cat-custom-${System.currentTimeMillis()}",
                                name = newName.trim(),
                                slug = newSlug.trim().ifBlank { newName.lowercase().replace(' ', '-') },
                                description = newDesc.trim().ifBlank { "Local services in $newName" },
                                iconName = "category",
                                activeListingCount = 0,
                                isEnabled = true,
                                isFeatured = false,
                            )
                            localCategories = listOf(newCat) + localCategories
                            actionNotice = "Category '${newName.trim()}' created successfully."
                            showAddCategoryDialog = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Create Category") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// -------------------------------------------------------------
// 3. Marketplace Admin Featured Listings Screen
// -------------------------------------------------------------

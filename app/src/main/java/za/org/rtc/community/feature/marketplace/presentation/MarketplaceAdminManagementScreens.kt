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

data class FlaggedReviewItem(
    val id: String,
    val businessId: String,
    val businessName: String,
    val authorName: String,
    val rating: Int,
    val title: String,
    val body: String,
    val reportReason: String,
    val reportDetails: String,
    val reportCount: Int,
    val reportedAt: String,
    val status: String, // "PENDING", "APPROVED", "REMOVED"
)

@Composable
fun MarketplaceAdminReviewsRoute(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var selectedReviewForDetail by remember { mutableStateOf<FlaggedReviewItem?>(null) }
    var reviewToDismiss by remember { mutableStateOf<FlaggedReviewItem?>(null) }
    var reviewToRemove by remember { mutableStateOf<FlaggedReviewItem?>(null) }

    var flaggedReviews by remember {
        mutableStateOf(
            listOf(
                FlaggedReviewItem(
                    id = "rev-fl-101",
                    businessId = "biz-plumb-01",
                    businessName = "Khayelitsha 24/7 Plumbing Pro",
                    authorName = "Vuyo M.",
                    rating = 1,
                    title = "Suspected fake review / competitor attack",
                    body = "This business charged exorbitant rates and did not complete the job properly on Site B.",
                    reportReason = "Competitor / Fraudulent Claim",
                    reportDetails = "Customer account created 1 hour ago; no booking record found in our CRM system.",
                    reportCount = 3,
                    reportedAt = "2 hours ago",
                    status = "PENDING",
                ),
                FlaggedReviewItem(
                    id = "rev-fl-102",
                    businessId = "biz-auto-04",
                    businessName = "Apex Auto & Body Repairs",
                    authorName = "Anonymous",
                    rating = 1,
                    title = "Abusive and offensive language",
                    body = "Completely rude staff and total scam artists who don't know what they are doing.",
                    reportReason = "Harassment / Inappropriate Content",
                    reportDetails = "Contains defamatory accusations and personal insults targeted at service technician.",
                    reportCount = 5,
                    reportedAt = "Yesterday",
                    status = "PENDING",
                ),
                FlaggedReviewItem(
                    id = "rev-fl-103",
                    businessId = "biz-solar-02",
                    businessName = "GreenCape Solar & Inverters",
                    authorName = "David K.",
                    rating = 5,
                    title = "Cryptocurrency solicitation spam in review",
                    body = "For high yield investment contact WhatsApp +27 82 000 1234 guaranteed 500% returns.",
                    reportReason = "Spam / Promotion",
                    reportDetails = "External WhatsApp scam link posted in public review section.",
                    reportCount = 8,
                    reportedAt = "3 days ago",
                    status = "PENDING",
                ),
            )
        )
    }

    val filteredList = remember(flaggedReviews, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> flaggedReviews.filter { it.status == "PENDING" }
            "SPAM" -> flaggedReviews.filter { it.reportReason.contains("Spam", ignoreCase = true) }
            "HARASSMENT" -> flaggedReviews.filter { it.reportReason.contains("Harassment", ignoreCase = true) }
            "COMPETITOR" -> flaggedReviews.filter { it.reportReason.contains("Competitor", ignoreCase = true) }
            else -> flaggedReviews
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
                    text = "Review Moderation Triage",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Review and resolve user-flagged reviews to maintain trust and safety across the Marketplace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Filter chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
            ) {
                listOf(
                    "ALL" to "All Flagged (${flaggedReviews.count { it.status == "PENDING" }})",
                    "SPAM" to "Spam & Links",
                    "HARASSMENT" to "Harassment & Abuse",
                    "COMPETITOR" to "Fraud / Competitor",
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = selectedFilter == code,
                        onClick = { selectedFilter = code },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Review list
        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = "No Flagged Reviews Pending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "All reported reviews in this category have been moderated and resolved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { review ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (review.status == "PENDING") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(RtcSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Header row: business name & report badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = review.businessName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "By ${review.authorName} · ${review.reportedAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Flag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "${review.reportCount} reports",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        // Rating & Review snippet
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "⭐ ${review.rating}/5",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (review.title.isNotBlank()) {
                                Text(
                                    text = "· ${review.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Text(
                            text = "\"${review.body}\"",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // Flag details
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Reported for: ${review.reportReason}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = review.reportDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Moderator action buttons
                        if (review.status == "PENDING") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                                verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap),
                            ) {
                                Button(
                                    onClick = { reviewToDismiss = review },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Dismiss Flag (Approve)")
                                }
                                OutlinedButton(
                                    onClick = { reviewToRemove = review },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove Review")
                                }
                            }
                        } else {
                            Text(
                                text = "Status: ${review.status}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (review.status == "APPROVED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    // Dismiss flag dialog
    reviewToDismiss?.let { review ->
        AlertDialog(
            onDismissRequest = { reviewToDismiss = null },
            title = { Text("Approve Review & Dismiss Flag?") },
            text = {
                Text("This will mark the review as compliant with Community guidelines, dismiss the report flags, and keep it visible on ${review.businessName}.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        flaggedReviews = flaggedReviews.map {
                            if (it.id == review.id) it.copy(status = "APPROVED") else it
                        }
                        actionNotice = "Review approved and report flag dismissed."
                        reviewToDismiss = null
                    },
                ) { Text("Approve & Keep") }
            },
            dismissButton = {
                TextButton(onClick = { reviewToDismiss = null }) { Text("Cancel") }
            },
        )
    }

    // Remove review dialog
    reviewToRemove?.let { review ->
        AlertDialog(
            onDismissRequest = { reviewToRemove = null },
            title = { Text("Remove Review for Policy Violation?") },
            text = {
                Text("This permanently removes the review from public listing and notifies the reviewer regarding policy enforcement.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        flaggedReviews = flaggedReviews.map {
                            if (it.id == review.id) it.copy(status = "REMOVED") else it
                        }
                        actionNotice = "Review removed due to policy violation."
                        reviewToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Confirm Removal") }
            },
            dismissButton = {
                TextButton(onClick = { reviewToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

// -------------------------------------------------------------
// 2. Marketplace Admin Categories Management Screen
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

data class FeaturedListingItem(
    val id: String,
    val businessId: String,
    val businessName: String,
    val categoryName: String,
    val rating: Double,
    val slot: String, // "HERO_BANNER", "CATEGORY_SPOTLIGHT", "TRENDING"
    val rank: Int,
    val impressionsCount: Int,
    val clickCount: Int,
    val expiresAt: String,
    val isActive: Boolean,
)

@Composable
fun MarketplaceAdminFeaturedRoute(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var showAddFeaturedDialog by rememberSaveable { mutableStateOf(false) }

    var featuredListings by remember {
        mutableStateOf(
            listOf(
                FeaturedListingItem(
                    id = "feat-01",
                    businessId = "biz-solar-01",
                    businessName = "BrightPower Solar & Inverter Solutions",
                    categoryName = "Electrical & Solar",
                    rating = 4.9,
                    slot = "HERO_BANNER",
                    rank = 1,
                    impressionsCount = 8420,
                    clickCount = 1240,
                    expiresAt = "In 18 days",
                    isActive = true,
                ),
                FeaturedListingItem(
                    id = "feat-02",
                    businessId = "biz-auto-01",
                    businessName = "Precision Auto Care & Diagnostics",
                    categoryName = "Automotive & Mechanical",
                    rating = 4.8,
                    slot = "CATEGORY_SPOTLIGHT",
                    rank = 2,
                    impressionsCount = 5120,
                    clickCount = 780,
                    expiresAt = "In 25 days",
                    isActive = true,
                ),
                FeaturedListingItem(
                    id = "feat-03",
                    businessId = "biz-plumb-02",
                    businessName = "Metro Plumbing & Emergency Drainage",
                    categoryName = "Plumbing & Drainage",
                    rating = 4.7,
                    slot = "TRENDING",
                    rank = 3,
                    impressionsCount = 3980,
                    clickCount = 610,
                    expiresAt = "In 12 days",
                    isActive = true,
                ),
                FeaturedListingItem(
                    id = "feat-04",
                    businessId = "biz-clean-01",
                    businessName = "SparklePro Commercial & Domestic Hygiene",
                    categoryName = "Home Cleaning & Hygiene",
                    rating = 4.9,
                    slot = "CATEGORY_SPOTLIGHT",
                    rank = 4,
                    impressionsCount = 4210,
                    clickCount = 590,
                    expiresAt = "In 30 days",
                    isActive = true,
                ),
            )
        )
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
                    text = "Featured Listings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Promote and spotlight verified community businesses across the home banner, discovery rows, and top searches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Top Summary & CTA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Promoted Spots (${featuredListings.count { it.isActive }})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { showAddFeaturedDialog = true },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Spotlight Business")
                }
            }
        }

        // Promoted businesses
        items(featuredListings, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (item.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        text = "#${item.rank} ${item.slot.replace('_', ' ')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text("⭐ ${item.rating}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.businessName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${item.categoryName} · Expires ${item.expiresAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = item.isActive,
                            onCheckedChange = { checked ->
                                featuredListings = featuredListings.map {
                                    if (it.id == item.id) it.copy(isActive = checked) else it
                                }
                                actionNotice = if (checked) "Featured promotion resumed for ${item.businessName}." else "Featured promotion paused."
                            },
                        )
                    }

                    // Impression & Click Metrics Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Impressions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${item.impressionsCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Clicks / Inquiries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${item.clickCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CTR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val ctr = if (item.impressionsCount > 0) (item.clickCount * 100f / item.impressionsCount) else 0f
                                Text(String.format("%.1f%%", ctr), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                featuredListings = featuredListings.filterNot { it.id == item.id }
                                actionNotice = "Removed ${item.businessName} from featured spots."
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove Spotlight")
                        }
                    }
                }
            }
        }
    }

    if (showAddFeaturedDialog) {
        var businessNameInput by rememberSaveable { mutableStateOf("") }
        var selectedSlot by rememberSaveable { mutableStateOf("HERO_BANNER") }
        var durationDays by rememberSaveable { mutableIntStateOf(30) }

        AlertDialog(
            onDismissRequest = { showAddFeaturedDialog = false },
            title = { Text("Spotlight Business Listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = businessNameInput,
                        onValueChange = { businessNameInput = it },
                        label = { Text("Business Name or ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Select Placement Slot", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "HERO_BANNER" to "Hero Banner",
                            "CATEGORY_SPOTLIGHT" to "Category Spotlight",
                            "TRENDING" to "Trending Row",
                        ).forEach { (slot, label) ->
                            FilterChip(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text("Duration: $durationDays days", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = durationDays.toFloat(),
                        onValueChange = { durationDays = it.toInt() },
                        valueRange = 7f..90f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (businessNameInput.isNotBlank()) {
                            val newItem = FeaturedListingItem(
                                id = "feat-${System.currentTimeMillis()}",
                                businessId = "biz-${System.currentTimeMillis()}",
                                businessName = businessNameInput.trim(),
                                categoryName = "General Service",
                                rating = 5.0,
                                slot = selectedSlot,
                                rank = featuredListings.size + 1,
                                impressionsCount = 0,
                                clickCount = 0,
                                expiresAt = "In $durationDays days",
                                isActive = true,
                            )
                            featuredListings = listOf(newItem) + featuredListings
                            actionNotice = "Business '${businessNameInput.trim()}' added to featured spotlight."
                            showAddFeaturedDialog = false
                        }
                    },
                    enabled = businessNameInput.isNotBlank(),
                ) { Text("Add to Spotlight") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFeaturedDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// -------------------------------------------------------------
// 4. Marketplace Admin Analytics & Growth Dashboard
// -------------------------------------------------------------

@Composable
fun MarketplaceAdminAnalyticsRoute(
    onBack: () -> Unit,
) {
    var timeframe by rememberSaveable { mutableStateOf("30D") }
    var actionNotice by remember { mutableStateOf<String?>(null) }

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
                    text = "Marketplace Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Track listing growth, search engagement, customer lead generation, and regional activity velocity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarketplaceNotice(actionNotice) { actionNotice = null }
        }

        // Timeframe selector
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "7D" to "Last 7 Days",
                    "30D" to "Last 30 Days",
                    "90D" to "Last 90 Days",
                    "ALL" to "All Time",
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = timeframe == code,
                        onClick = { timeframe = code },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Core KPIs Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricSummaryCard(
                        title = "Directory Views",
                        value = if (timeframe == "7D") "3,480" else if (timeframe == "30D") "14,820" else "48,910",
                        change = "+18.4% vs prev",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                    MetricSummaryCard(
                        title = "Customer Inquiries",
                        value = if (timeframe == "7D") "492" else if (timeframe == "30D") "2,130" else "7,450",
                        change = "+24.1% vs prev",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricSummaryCard(
                        title = "Active Businesses",
                        value = "186",
                        change = "12 pending review",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                    MetricSummaryCard(
                        title = "Average Rating",
                        value = "4.8 / 5.0",
                        change = "94% 4+ stars",
                        isPositive = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Top Category Demand Distribution
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Category Search & Demand Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    listOf(
                        "Plumbing & Emergency Drainage" to 0.32f,
                        "Electrical & Solar Solutions" to 0.28f,
                        "Food, Restaurants & Catering" to 0.16f,
                        "Home Cleaning & Hygiene" to 0.14f,
                        "Automotive & Towing" to 0.10f,
                    ).forEach { (catName, share) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(catName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { share },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Top Search Queries
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Top Marketplace Search Terms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    listOf(
                        "emergency plumber" to "1,240 searches",
                        "solar inverter installation" to "980 searches",
                        "car battery jumpstart" to "740 searches",
                        "deep cleaning services" to "620 searches",
                        "braiding salon open sunday" to "490 searches",
                        "electrician coC certificate" to "410 searches",
                    ).forEach { (term, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(term, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Export Report Button
        item {
            Button(
                onClick = { actionNotice = "Marketplace performance report exported as CSV." },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RtcSize.minimumTouchTarget),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Analytics Summary (CSV)")
            }
        }
    }
}

@Composable
private fun MetricSummaryCard(
    title: String,
    value: String,
    change: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = change,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

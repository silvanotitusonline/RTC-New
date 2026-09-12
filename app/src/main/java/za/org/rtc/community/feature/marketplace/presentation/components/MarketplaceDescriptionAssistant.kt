package za.org.rtc.community.feature.marketplace.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Data model for category-based business description templates and auto-suggestions.
 */
data class DescriptionSuggestion(
    val id: String,
    val categoryId: String,
    val title: String,
    val style: SuggestionStyle,
    val template: (businessName: String) -> String,
)

enum class SuggestionStyle(val label: String, val icon: ImageVector) {
    OVERVIEW("Full Story", Icons.Filled.Storefront),
    EXPERIENCE("Quality & Trust", Icons.Filled.Verified),
    PITCH("Quick Summary", Icons.Filled.Bolt),
    BULLETS("Structured Outline", Icons.Filled.FormatListBulleted),
}

/**
 * Curated repository of category-specific description templates tailored to South African community enterprises.
 */
object MarketplaceDescriptionSuggestionsData {

    val suggestions: List<DescriptionSuggestion> = listOf(
        // Services & Trades
        DescriptionSuggestion(
            id = "services_overview",
            categoryId = "services",
            title = "Professional Trade & Repair Services",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, we provide" else "We provide"
                "$prefix professional plumbing, electrical maintenance, building repairs, and installations across the local community. Our team is dedicated to reliable workmanship, transparent pricing, and fast response times for all residential and commercial needs."
            }
        ),
        DescriptionSuggestion(
            id = "services_experience",
            categoryId = "services",
            title = "Experienced & Certified Contractor",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val brand = if (name.isNotBlank()) "$name brings" else "We bring"
                "$brand extensive hands-on trade experience with qualified artisans. Every job is completed to safety standards, using durable materials with customer satisfaction guaranteed."
            }
        ),
        DescriptionSuggestion(
            id = "services_bullets",
            categoryId = "services",
            title = "Trade Services Outline",
            style = SuggestionStyle.BULLETS,
            template = { name ->
                val title = if (name.isNotBlank()) "$name - Services & Capabilities:" else "Services & Capabilities:"
                "$title\n• Plumbing, pipe repairs & geyser installations\n• Electrical wiring, fault-finding & lighting\n• General home maintenance, painting & tiling\n• Fast response for urgent repairs\n• Call or message us for a free quote!"
            }
        ),

        // Food & Groceries
        DescriptionSuggestion(
            id = "food_overview",
            categoryId = "food",
            title = "Fresh Daily Grocery & Produce Market",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "Welcome to $name! We are" else "We are"
                "$prefix your friendly local neighborhood market, offering fresh daily vegetables, butchery cuts, freshly baked bread, dairy essentials, and pantry groceries at affordable community prices."
            }
        ),
        DescriptionSuggestion(
            id = "food_eatery",
            categoryId = "food",
            title = "Local Cuisine & Flame-Grilled Takeaway",
            style = SuggestionStyle.PITCH,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name serves" else "We serve"
                "$prefix delicious, freshly prepared traditional dishes, shisanyama platters, burgers, and daily home-cooked specials made with premium ingredients and authentic local flavor."
            }
        ),
        DescriptionSuggestion(
            id = "food_bullets",
            categoryId = "food",
            title = "Menu & Food Offerings Outline",
            style = SuggestionStyle.BULLETS,
            template = { name ->
                val title = if (name.isNotBlank()) "$name - Offerings:" else "Our Offerings:"
                "$title\n• Daily fresh farm produce & bakery items\n• Quality butchery meats & deli cuts\n• Hot takeaway meals & platters\n• Bulk purchasing & catering orders available"
            }
        ),

        // Tech & Repair
        DescriptionSuggestion(
            id = "tech_repairs",
            categoryId = "tech",
            title = "Smartphone & Laptop Repair Lab",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, we specialize in" else "We specialize in"
                "$prefix fast and dependable screen repairs, battery replacements, charging port fixes, and motherboard micro-soldering for iPhones, Samsungs, Huawei, laptops, and tablets."
            }
        ),
        DescriptionSuggestion(
            id = "tech_it",
            categoryId = "tech",
            title = "IT Support, Printing & Tech Accessories",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name is" else "We are"
                "$prefix your one-stop technology hub providing computer servicing, Windows/macOS reinstalls, virus removal, internet cafe printing, document typing, and quality tech accessories."
            }
        ),
        DescriptionSuggestion(
            id = "tech_bullets",
            categoryId = "tech",
            title = "Tech Services Checklist",
            style = SuggestionStyle.BULLETS,
            template = { name ->
                val title = if (name.isNotBlank()) "$name - Tech Services:" else "Tech Services:"
                "$title\n• Screen & battery replacements (same-day service)\n• Laptop diagnostics, RAM/SSD upgrades & OS reinstall\n• Phone unlocking & software troubleshooting\n• Document printing, scanning & photocopying\n• Genuine chargers, cables & audio accessories"
            }
        ),

        // Health & Wellness
        DescriptionSuggestion(
            id = "health_barber",
            categoryId = "health",
            title = "Modern Barbershop & Grooming",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name offers" else "We offer"
                "$prefix premium grooming, precision fades, beard sculpting, line-ups, and hot towel treatments in a clean, relaxed, and welcoming community atmosphere."
            }
        ),
        DescriptionSuggestion(
            id = "health_salon",
            categoryId = "health",
            title = "Hair Salon, Braiding & Beauty Spa",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, our stylists specialize in" else "Our stylists specialize in"
                "$prefix knotless braids, dreadlock maintenance, wig installations, wash & sets, manicures, pedicures, and skincare treatments designed to make you look and feel your best."
            }
        ),
        DescriptionSuggestion(
            id = "health_bullets",
            categoryId = "health",
            title = "Beauty & Wellness Treatments Outline",
            style = SuggestionStyle.BULLETS,
            template = { name ->
                val title = if (name.isNotBlank()) "$name - Treatments:" else "Our Services:"
                "$title\n• Braids, weaves, wig customization & hair care\n• Precision haircuts, beard grooming & styling\n• Gel nails, pedicures & eyelash extensions\n• Walk-ins & advance appointments welcome"
            }
        ),

        // Auto & Mechanical
        DescriptionSuggestion(
            id = "auto_mechanical",
            categoryId = "auto",
            title = "Full Vehicle Maintenance & Diagnostics",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name provides" else "We provide"
                "$prefix comprehensive motor vehicle servicing, computer diagnostics, brake & clutch replacements, suspension repairs, and engine overhauls with experienced mechanics."
            }
        ),
        DescriptionSuggestion(
            id = "auto_body",
            categoryId = "auto",
            title = "Panel Beating, Spray Painting & Detailing",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, we take pride in" else "We take pride in"
                "$prefix expert panel beating, dent removal, chassis realignment, professional spray painting, and premium auto detailing that restores your vehicle to showroom quality."
            }
        ),
        DescriptionSuggestion(
            id = "auto_bullets",
            categoryId = "auto",
            title = "Automotive Services Outline",
            style = SuggestionStyle.BULLETS,
            template = { name ->
                val title = if (name.isNotBlank()) "$name - Auto Services:" else "Auto Services:"
                "$title\n• Minor & major vehicle services\n• Brake pads, discs, shocks & clutch repair\n• Engine troubleshooting & computerized diagnostics\n• Panel beating, scratch repair & spray painting\n• Roadworthy checkups & oil changes"
            }
        ),

        // Home & Garden
        DescriptionSuggestion(
            id = "home_garden",
            categoryId = "home",
            title = "Landscaping, Gardening & Yard Care",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, we maintain" else "We maintain"
                "$prefix beautiful and neat outdoor spaces with professional lawn mowing, hedge trimming, tree cutting, weed eradication, flower planting, and complete yard clean-ups."
            }
        ),
        DescriptionSuggestion(
            id = "home_furniture",
            categoryId = "home",
            title = "Custom Furniture, Carpentry & Upholstery",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name designs and crafts" else "We design and craft"
                "$prefix custom wooden furniture, kitchen cupboards, wardrobe fittings, and couch re-upholstery built with durable timber and tailored to your home's exact dimensions."
            }
        ),

        // Retail & Fashion
        DescriptionSuggestion(
            id = "retail_fashion",
            categoryId = "retail",
            title = "Boutique Fashion & Tailoring",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name is" else "We are"
                "$prefix a community fashion boutique offering trendy streetwear, traditional African attire, custom dressmaking, tailoring alterations, and stylish accessories."
            }
        ),
        DescriptionSuggestion(
            id = "retail_general",
            categoryId = "retail",
            title = "Quality Retail & Home Essentials",
            style = SuggestionStyle.PITCH,
            template = { name ->
                val prefix = if (name.isNotBlank()) "Welcome to $name!" else "Welcome!"
                "$prefix Discover quality clothing, footwear, homeware essentials, and gift items at unbeatable community prices with friendly personal assistance."
            }
        ),

        // Education & Training
        DescriptionSuggestion(
            id = "education_tutoring",
            categoryId = "education",
            title = "School Tutoring & Academic Support",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, we empower" else "We empower"
                "$prefix primary and high school learners with dedicated tutoring in Mathematics, Physical Sciences, English, and Accounting, boosting confidence and exam results."
            }
        ),
        DescriptionSuggestion(
            id = "education_creche",
            categoryId = "education",
            title = "Early Childhood Development & Creche",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name provides" else "We provide"
                "$prefix a safe, nurturing, and stimulating learning environment for toddlers and young children, complete with educational play, nutritious meals, and caring staff."
            }
        ),

        // Universal / General
        DescriptionSuggestion(
            id = "general_community",
            categoryId = "general",
            title = "Dedicated Local Enterprise",
            style = SuggestionStyle.OVERVIEW,
            template = { name ->
                val prefix = if (name.isNotBlank()) "$name is" else "We are"
                "$prefix a proud local business committed to delivering quality products, dependable services, and warm customer care to our residents and neighborhood."
            }
        ),
        DescriptionSuggestion(
            id = "general_custom",
            categoryId = "general",
            title = "Custom Solutions & Client Care",
            style = SuggestionStyle.EXPERIENCE,
            template = { name ->
                val prefix = if (name.isNotBlank()) "At $name, we strive to" else "We strive to"
                "$prefix exceed our customers' expectations with personalized attention, top-tier craftsmanship, fair pricing, and reliable service on every project."
            }
        ),
    )

    fun getSuggestionsForCategory(categoryId: String): List<DescriptionSuggestion> {
        val direct = suggestions.filter { it.categoryId.equals(categoryId, ignoreCase = true) }
        val general = suggestions.filter { it.categoryId == "general" }
        return if (direct.isNotEmpty()) direct + general else general
    }
}

/**
 * Interactive category-based auto-suggestions component for business description drafting.
 */
@Composable
fun MarketplaceDescriptionSuggestionsAssistant(
    businessName: String,
    currentDescription: String,
    primaryCategoryId: String,
    selectedCategoryIds: Set<String>,
    availableCategories: List<MarketplaceCategory>,
    onApplyDescription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTabCategory by remember(primaryCategoryId, selectedCategoryIds) {
        val initial = primaryCategoryId.ifBlank { selectedCategoryIds.firstOrNull().orEmpty() }
        mutableStateOf(if (initial.isNotBlank()) initial else "services")
    }
    var selectedStyleFilter by remember { mutableStateOf<SuggestionStyle?>(null) }
    var previousDescriptionForUndo by remember { mutableStateOf<String?>(null) }
    var lastActionFeedback by remember { mutableStateOf<String?>(null) }

    // Clear feedback timer
    LaunchedEffect(lastActionFeedback) {
        if (lastActionFeedback != null) {
            kotlinx.coroutines.delay(3500)
            lastActionFeedback = null
        }
    }

    val matchingSuggestions = remember(selectedTabCategory, selectedStyleFilter) {
        val list = MarketplaceDescriptionSuggestionsData.getSuggestionsForCategory(selectedTabCategory)
        if (selectedStyleFilter != null) {
            list.filter { it.style == selectedStyleFilter }
        } else {
            list
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with toggle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Category Description Starters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpanded) "Tap a template to quickly draft or enrich your description" else "Need inspiration? Tap to browse pre-made templates for your industry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse suggestions" else "Expand suggestions",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Undo Feedback notification
            AnimatedVisibility(visible = lastActionFeedback != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lastActionFeedback.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (previousDescriptionForUndo != null) {
                            TextButton(
                                onClick = {
                                    previousDescriptionForUndo?.let { onApplyDescription(it) }
                                    previousDescriptionForUndo = null
                                    lastActionFeedback = "Reverted previous description"
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Expanded Suggestions Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. Category Selector Chips
                    Text(
                        text = "Select industry / category:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val displayCategories = remember(availableCategories) {
                        if (availableCategories.isNotEmpty()) {
                            availableCategories.filter { it.id != "all" }
                        } else {
                            listOf(
                                MarketplaceCategory("services", "Services & Trades", "services", "handyman"),
                                MarketplaceCategory("food", "Food & Groceries", "food", "restaurant"),
                                MarketplaceCategory("tech", "Tech & Repair", "tech", "laptop"),
                                MarketplaceCategory("health", "Health & Wellness", "health", "spa"),
                                MarketplaceCategory("auto", "Auto & Mechanical", "auto", "directions_car"),
                                MarketplaceCategory("home", "Home & Garden", "home", "yard"),
                                MarketplaceCategory("retail", "Retail & Fashion", "retail", "shopping_bag"),
                                MarketplaceCategory("education", "Education & Training", "education", "school"),
                                MarketplaceCategory("general", "General & Other", "general", "storefront")
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(displayCategories, key = { it.id }) { cat ->
                            val isSelected = selectedTabCategory.equals(cat.id, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTabCategory = cat.id },
                                label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    // 2. Style Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Format:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SuggestionStyle.values().forEach { style ->
                            val isFilterSelected = selectedStyleFilter == style
                            FilterChip(
                                selected = isFilterSelected,
                                onClick = {
                                    selectedStyleFilter = if (isFilterSelected) null else style
                                },
                                label = { Text(style.label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = style.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }

                    // 3. Suggestion Cards
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingSuggestions.forEach { suggestion ->
                            val generatedText = remember(suggestion.id, businessName) {
                                suggestion.template(businessName)
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = suggestion.style.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = suggestion.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                text = suggestion.style.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = generatedText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (currentDescription.isNotBlank()) {
                                            TextButton(
                                                onClick = {
                                                    previousDescriptionForUndo = currentDescription
                                                    val combined = "$currentDescription\n\n$generatedText".trim()
                                                    onApplyDescription(combined)
                                                    lastActionFeedback = "Appended template to description"
                                                },
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Append", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                previousDescriptionForUndo = currentDescription
                                                onApplyDescription(generatedText)
                                                lastActionFeedback = "Applied template to description"
                                            },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (currentDescription.isBlank()) "Use Template" else "Replace All",
                                                style = MaterialTheme.typography.labelSmall
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
}

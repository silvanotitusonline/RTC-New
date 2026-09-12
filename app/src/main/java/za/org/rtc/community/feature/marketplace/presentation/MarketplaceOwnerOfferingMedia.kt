package za.org.rtc.community.feature.marketplace.presentation

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.org.rtc.community.feature.marketplace.data.local.BusinessCreationDraft
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDraftEditor
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerBusiness
import za.org.rtc.community.feature.marketplace.presentation.components.MarketplaceDescriptionSuggestionsAssistant
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing


@Composable
internal fun OfferingStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
    var editingOfferingId by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf("SERVICE") }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priceType by rememberSaveable { mutableStateOf("QUOTE") }
    var minPrice by rememberSaveable { mutableStateOf("") }
    var maxPrice by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var availability by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("5. Services and products", style = MaterialTheme.typography.titleLarge)
        
        if (editor.offerings.isNotEmpty()) {
            Text("Currently Saved Offerings:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            editor.offerings.forEach { offering ->
                val id = offering.marketplaceString("id")
                val offType = offering.marketplaceString("offeringType")
                val offTitle = offering.marketplaceString("title")
                val offDesc = offering.marketplaceString("description")
                val offPriceType = offering.marketplaceString("priceType")
                val offMinPrice = offering.marketplaceString("priceMin")
                val offMaxPrice = offering.marketplaceString("priceMax")
                val offDuration = offering.marketplaceString("durationMinutes")
                val offAvail = offering.marketplaceString("availabilityNote")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingOfferingId == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(offTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    editingOfferingId = id
                                    type = offType
                                    title = offTitle
                                    description = offDesc
                                    priceType = offPriceType
                                    minPrice = offMinPrice
                                    maxPrice = offMaxPrice
                                    duration = offDuration
                                    availability = offAvail
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Edit")
                            }
                        }
                        Text(offDesc, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = buildString {
                                append("Type: ${offType.lowercase().replaceFirstChar(Char::uppercase)}")
                                append(" · Price: $offPriceType")
                                if (offMinPrice.isNotBlank()) append(" (ZAR $offMinPrice")
                                if (offMaxPrice.isNotBlank()) append(" - $offMaxPrice")
                                if (offMinPrice.isNotBlank()) append(")")
                                if (offDuration.isNotBlank()) append(" · ${offDuration}m")
                                if (offAvail.isNotBlank()) append(" · $offAvail")
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Text("No offerings saved yet. Please add a service or product below.")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = RtcSpacing.compact))
        
        Text(if (editingOfferingId == null) "Add New Service/Product" else "Edit Service/Product Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            listOf("SERVICE", "PRODUCT").forEach { value ->
                FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value.lowercase().replaceFirstChar(Char::uppercase)) })
            }
        }
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        
        Text("Pricing Model", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            listOf("QUOTE", "FREE", "FIXED", "FROM", "RANGE").forEach { value ->
                FilterChip(selected = priceType == value, onClick = { priceType = value }, label = { Text(value) })
            }
        }
        if (priceType in setOf("FIXED", "FROM", "RANGE")) {
            OutlinedTextField(value = minPrice, onValueChange = { minPrice = it }, label = { Text(if (priceType == "RANGE") "Minimum price (ZAR)" else "Price (ZAR)") }, modifier = Modifier.fillMaxWidth())
        }
        if (priceType == "RANGE") {
            OutlinedTextField(value = maxPrice, onValueChange = { maxPrice = it }, label = { Text("Maximum price (ZAR)") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(value = duration, onValueChange = { duration = it.filter(Char::isDigit) }, label = { Text("Duration minutes (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = availability, onValueChange = { availability = it }, label = { Text("Availability note") }, modifier = Modifier.fillMaxWidth())
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            Button(
                onClick = {
                    viewModel.saveOffering(editor.businessId, editingOfferingId, buildJsonObject {
                        put("offeringType", type)
                        put("title", title)
                        put("description", description)
                        put("priceType", priceType)
                        put("currencyCode", "ZAR")
                        put("priceMin", minPrice)
                        put("priceMax", maxPrice)
                        put("durationMinutes", duration)
                        put("availabilityNote", availability)
                        put("locationIds", JsonArray(emptyList()))
                    })
                    // Reset
                    editingOfferingId = null
                    type = "SERVICE"
                    title = ""
                    description = ""
                    priceType = "QUOTE"
                    minPrice = ""
                    maxPrice = ""
                    duration = ""
                    availability = ""
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget)
            ) {
                Text(if (editingOfferingId == null) "Add offering" else "Update offering")
            }
            if (editingOfferingId != null) {
                OutlinedButton(
                    onClick = {
                        editingOfferingId = null
                        type = "SERVICE"
                        title = ""
                        description = ""
                        priceType = "QUOTE"
                        minPrice = ""
                        maxPrice = ""
                        duration = ""
                        availability = ""
                    },
                    modifier = Modifier.height(RtcSize.minimumTouchTarget)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
internal fun ContactStep(
    editor: MarketplaceDraftEditor,
    name: String,
    tagline: String,
    description: String,
    categories: Set<String>,
    primary: String,
    phone: String,
    onPhone: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    website: String,
    onWebsite: (String) -> Unit,
    whatsapp: Boolean,
    onWhatsapp: (Boolean) -> Unit,
    viewModel: MarketplaceOwnerViewModel
) {
    val phoneDigits = phone.filter { it.isDigit() }
    val phoneError = if (phone.isNotBlank() && phoneDigits.length < 7) "Enter a valid phone number (at least 7 digits)" else null
    val emailError = if (email.isNotBlank() && !email.contains("@")) "Enter a valid email address (e.g. info@business.co.za)" else null
    val websiteError = if (website.isNotBlank() && !website.startsWith("http://") && !website.startsWith("https://")) "Website URL should start with http:// or https://" else null

    val hasContactChannel = phone.isNotBlank() || email.isNotBlank()
    val isFormValid = phoneError == null && emailError == null && websiteError == null
    val configuration = LocalConfiguration.current
    val isLandscapeOrTablet = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("6. Public contact preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = if (hasContactChannel && isFormValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = if (hasContactChannel && isFormValid) "Valid" else "Recommended",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasContactChannel && isFormValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (!hasContactChannel) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Tip: Providing at least one contact channel (phone or email) makes it easy for local community members to reach you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (isLandscapeOrTablet) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhone,
                    label = { Text("Public phone") },
                    placeholder = { Text("e.g. 082 123 4567") },
                    modifier = Modifier.weight(1f),
                    isError = phoneError != null,
                    supportingText = phoneError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = onEmail,
                    label = { Text("Public email") },
                    placeholder = { Text("e.g. contact@mybusiness.co.za") },
                    modifier = Modifier.weight(1f),
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
        } else {
            OutlinedTextField(
                value = phone,
                onValueChange = onPhone,
                label = { Text("Public phone") },
                placeholder = { Text("e.g. 082 123 4567") },
                modifier = Modifier.fillMaxWidth(),
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            OutlinedTextField(
                value = email,
                onValueChange = onEmail,
                label = { Text("Public email") },
                placeholder = { Text("e.g. contact@mybusiness.co.za") },
                modifier = Modifier.fillMaxWidth(),
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
            )
        }

        OutlinedTextField(
            value = website,
            onValueChange = onWebsite,
            label = { Text("Website URL (optional)") },
            placeholder = { Text("https://www.mybusiness.co.za") },
            modifier = Modifier.fillMaxWidth(),
            isError = websiteError != null,
            supportingText = websiteError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = whatsapp, onCheckedChange = onWhatsapp)
            Text("Allow WhatsApp contact on the public listing")
        }

        Button(
            onClick = {
                viewModel.saveIdentity(
                    editor.businessId,
                    identityPayload(editor, name, tagline, description, categories, primary, phone, email, website, whatsapp)
                )
                viewModel.checkpoint(editor.businessId, 6)
            },
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Draft & Sync Contact Details")
        }
    }
}

@Composable
internal fun MediaStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
    val context = LocalContext.current
    val operations by viewModel.mediaOperations.collectAsStateWithLifecycle()
    var assetType by rememberSaveable { mutableStateOf("LOGO") }
    var altText by rememberSaveable { mutableStateOf("") }
    var deleteAssetId by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia((8 - editor.media.size).coerceAtLeast(1))) { uris ->
        uris.take((8 - editor.media.size).coerceAtLeast(1)).forEach {
            viewModel.uploadMedia(editor.businessId, assetType, it.toString(), altText.ifBlank { "Business media asset" })
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "biz_photo_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                val photoUri = android.net.Uri.fromFile(cacheFile)
                viewModel.uploadMedia(
                    editor.businessId,
                    assetType,
                    photoUri.toString(),
                    if (altText.isNotBlank()) altText else "Business profile photo captured with camera",
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("7. Business Media & Profile Photo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Capture or upload profile photos and logos using your device camera or gallery. Images are saved to Supabase storage.", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            listOf("LOGO", "COVER", "GALLERY").forEach { value ->
                FilterChip(selected = assetType == value, onClick = { assetType = value }, label = { Text(if (value == "LOGO") "PROFILE LOGO" else value) })
            }
        }
        OutlinedTextField(
            value = altText,
            onValueChange = { altText = it },
            label = { Text("Photo description / caption") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Capture Photo")
            }

            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = assetType != "GALLERY" || editor.media.size < 8,
                modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
            ) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Pick from Gallery")
            }
        }

        operations.values.forEach { op ->
            Column {
                Text("${op.stage.name.lowercase().replaceFirstChar(Char::uppercase)} · ${(op.progress * 100).toInt()}%")
                LinearProgressIndicator(progress = { op.progress }, modifier = Modifier.fillMaxWidth())
                op.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        editor.media.forEach { media ->
            val id = media.marketplaceString("id")
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(RtcSpacing.cardPadding),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "${media.marketplaceString("asset_type").ifBlank { media.marketplaceString("type") }} · ${media.marketplaceString("alt_text").ifBlank { media.marketplaceString("altText") }}",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { deleteAssetId = id }) { Text("Remove") }
                }
            }
        }
    }

    deleteAssetId?.let { id ->
        ConfirmMarketplaceActionDialog(
            title = "Remove draft media?",
            message = "The media asset will be removed from this business listing.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { deleteAssetId = null },
            onConfirm = {
                deleteAssetId = null
                viewModel.deleteMedia(editor.businessId, id)
            },
        )
    }
}

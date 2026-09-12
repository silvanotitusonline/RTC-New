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
internal fun IdentityStep(
    name: String,
    onName: (String) -> Unit,
    tagline: String,
    onTagline: (String) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    editor: MarketplaceDraftEditor,
    selectedCategories: Set<String>,
    primaryCategory: String,
    categories: List<MarketplaceCategory>,
    viewModel: MarketplaceOwnerViewModel
) {
    val nameError = when {
        name.isBlank() -> "Display name is required"
        name.trim().length < 2 -> "Name must be at least 2 characters"
        name.length > 100 -> "Name cannot exceed 100 characters"
        else -> null
    }
    val taglineError = when {
        tagline.length > 120 -> "Tagline cannot exceed 120 characters"
        else -> null
    }
    val descriptionError = when {
        description.isBlank() -> "Description is required"
        description.trim().length < 10 -> "Please enter at least 10 characters describing your business"
        description.length > 1000 -> "Description cannot exceed 1000 characters"
        else -> null
    }

    val isStepValid = nameError == null && taglineError == null && descriptionError == null
    val configuration = LocalConfiguration.current
    val isLandscapeOrTablet = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1. Identity and story", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = if (isStepValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = if (isStepValid) "Valid" else "Requires attention",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStepValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (isLandscapeOrTablet) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    modifier = Modifier.weight(1f),
                    label = { Text("Display name *") },
                    isError = nameError != null,
                    supportingText = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(nameError ?: "Official or public trading name", color = if (nameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${name.length}/100")
                        }
                    }
                )

                OutlinedTextField(
                    value = tagline,
                    onValueChange = onTagline,
                    modifier = Modifier.weight(1f),
                    label = { Text("Tagline (optional)") },
                    placeholder = { Text("Short catchphrase or summary") },
                    isError = taglineError != null,
                    supportingText = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(taglineError ?: "e.g. Quality plumbing & repairs since 2018", color = if (taglineError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${tagline.length}/120")
                        }
                    }
                )
            }
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name *") },
                isError = nameError != null,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(nameError ?: "Official or public trading name", color = if (nameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${name.length}/100")
                    }
                }
            )

            OutlinedTextField(
                value = tagline,
                onValueChange = onTagline,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tagline (optional)") },
                placeholder = { Text("Short catchphrase or summary") },
                isError = taglineError != null,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(taglineError ?: "e.g. Quality plumbing & repairs since 2018", color = if (taglineError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${tagline.length}/120")
                    }
                }
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = onDescription,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description *") },
            placeholder = { Text("Provide details about services, experience, specialties, and history...") },
            isError = descriptionError != null,
            minLines = 4,
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(descriptionError ?: "Tell residents what makes your business unique", color = if (descriptionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${description.length}/1000")
                }
            }
        )

        // Category-based auto-suggestions assistant for drafting description efficiently
        MarketplaceDescriptionSuggestionsAssistant(
            businessName = name,
            currentDescription = description,
            primaryCategoryId = primaryCategory,
            selectedCategoryIds = selectedCategories,
            availableCategories = categories,
            onApplyDescription = onDescription
        )
        
        Spacer(Modifier.height(RtcSpacing.compact))
        
        Button(
            onClick = {
                viewModel.saveIdentity(
                    editor.businessId,
                    identityPayload(editor, name, tagline, description, selectedCategories, primaryCategory)
                )
                viewModel.checkpoint(editor.businessId, 1)
            },
            enabled = isStepValid,
            modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Draft & Sync Identity")
        }
        
        Text("Your draft progress is saved automatically when navigating or clicking 'Save Draft'.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CategoryStep(categories: List<MarketplaceCategory>, selected: Set<String>, primary: String, onSave: (Set<String>, String) -> Unit) {
    var working by remember(selected) { mutableStateOf(selected) }
    var primaryId by remember(primary) { mutableStateOf(primary) }
    val isValid = working.isNotEmpty() && primaryId in working

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("2. Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = if (isValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = if (isValid) "Valid" else "Select 1+",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text("Choose one or more categories and mark one as primary so customers can discover your profile easily.")

        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            categories.forEach { category ->
                FilterChip(
                    selected = category.id in working,
                    onClick = {
                        working = if (category.id in working) working - category.id else working + category.id
                        if (primaryId !in working) primaryId = working.firstOrNull().orEmpty()
                    },
                    label = { Text(category.name) }
                )
            }
        }

        if (working.isEmpty()) {
            Text("⚠️ Please select at least one category to proceed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Primary category *", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                categories.filter { it.id in working }.forEach { category ->
                    FilterChip(selected = category.id == primaryId, onClick = { primaryId = category.id }, label = { Text(category.name) })
                }
            }
        }

        Button(
            onClick = { onSave(working, primaryId) },
            enabled = isValid,
            modifier = Modifier.height(RtcSize.minimumTouchTarget).fillMaxWidth()
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Draft & Update Categories")
        }
    }
}

internal fun identityPayload(editor: MarketplaceDraftEditor, name: String, tagline: String, description: String, categories: Set<String>, primary: String, phone: String = editor.phone, email: String = editor.email, website: String = editor.websiteUrl, whatsapp: Boolean = false): JsonObject = buildJsonObject {
    put("displayName", name.trim()); put("businessType", editor.businessType); put("tagline", tagline.trim()); put("description", description.trim())
    put("publicPhone", phone.trim()); put("publicEmail", email.trim()); put("websiteUrl", website.trim()); put("whatsappEnabled", whatsapp)
    put("ownerDeclared", true); put("publicContactConsent", true); put("publicAddressConsent", true); put("noEndorsementAcknowledged", true); put("accuracyDeclared", true)
    put("categories", buildJsonArray { categories.forEach { id -> add(buildJsonObject { put("id", id); put("primary", id == primary) }) } })
}

@Composable
internal fun LocationStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
    var editingLocationId by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf("Primary location") }
    var locality by rememberSaveable { mutableStateOf("") }
    var municipality by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("Northern Cape") }
    var address by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf("EXACT") }

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("3. Locations", style = MaterialTheme.typography.titleLarge)
        
        if (editor.locations.isNotEmpty()) {
            Text("Currently Saved Locations:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            editor.locations.forEach { location ->
                val id = location.marketplaceString("id")
                val locLabel = location.marketplaceString("label")
                val locLocality = location.marketplaceString("locality")
                val locMunicipality = location.marketplaceString("municipality")
                val locProvince = location.marketplaceString("province")
                val locAddress = location.marketplaceString("addressLine1")
                val locVisibility = location.marketplaceString("addressVisibility")
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingLocationId == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(locLabel.ifBlank { "Location" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    editingLocationId = id
                                    label = locLabel
                                    locality = locLocality
                                    municipality = locMunicipality
                                    province = locProvince
                                    address = locAddress
                                    visibility = locVisibility
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Edit")
                            }
                        }
                        Text("$locAddress, $locLocality, $locMunicipality, $locProvince")
                        Text("Visibility: ${locVisibility.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Text("No locations saved yet. Please add a location below.")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = RtcSpacing.compact))
        
        Text(if (editingLocationId == null) "Add New Location" else "Edit Location Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(label = { Text("Location label") }, value = label, onValueChange = { label = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label = { Text("Locality") }, value = locality, onValueChange = { locality = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label = { Text("Municipality") }, value = municipality, onValueChange = { municipality = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label = { Text("Province") }, value = province, onValueChange = { province = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label = { Text("Address") }, value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth())
        Text("Address Visibility", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            listOf("EXACT", "AREA_ONLY", "HIDDEN").forEach { value -> FilterChip(selected = visibility == value, onClick = { visibility = value }, label = { Text(value.replace('_', ' ')) }) }
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            Button(
                onClick = {
                    viewModel.saveLocation(editor.businessId, editingLocationId, buildJsonObject {
                        put("label", label); put("locality", locality); put("municipality", municipality); put("province", province); put("addressLine1", address)
                        put("locationType", "PHYSICAL"); put("addressVisibility", visibility); put("timezone", "Africa/Johannesburg"); put("isPrimary", editor.locations.isEmpty() && editingLocationId == null)
                    })
                    // Reset
                    editingLocationId = null
                    label = "Primary location"
                    locality = ""
                    municipality = ""
                    province = "Northern Cape"
                    address = ""
                    visibility = "EXACT"
                },
                enabled = locality.isNotBlank(),
                modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget)
            ) {
                Text(if (editingLocationId == null) "Add location" else "Update location")
            }
            if (editingLocationId != null) {
                OutlinedButton(
                    onClick = {
                        editingLocationId = null
                        label = "Primary location"
                        locality = ""
                        municipality = ""
                        province = "Northern Cape"
                        address = ""
                        visibility = "EXACT"
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
internal fun HoursStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
    val locations = editor.locations
    var locationId by remember(locations) { mutableStateOf(locations.firstOrNull()?.marketplaceString("id").orEmpty()) }
    val days = remember { mutableStateListOf(
        WeeklyHoursDraft(0, "Sunday"), WeeklyHoursDraft(1, "Monday", true), WeeklyHoursDraft(2, "Tuesday", true), WeeklyHoursDraft(3, "Wednesday", true),
        WeeklyHoursDraft(4, "Thursday", true), WeeklyHoursDraft(5, "Friday", true), WeeklyHoursDraft(6, "Saturday")
    ) }
    val exceptions = remember { mutableStateListOf<HourExceptionDraft>() }
    var date by rememberSaveable { mutableStateOf("") }; var exceptionState by rememberSaveable { mutableStateOf("CLOSED") }
    var exceptionOpen by rememberSaveable { mutableStateOf("08:00") }; var exceptionClose by rememberSaveable { mutableStateOf("17:00") }; var note by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("4. Opening hours", style = MaterialTheme.typography.titleLarge)
        if (locations.isEmpty()) { Text("Save a location before adding hours."); return@Column }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            locations.forEach { location -> val id = location.marketplaceString("id"); FilterChip(selected = locationId == id, onClick = { locationId = id }, label = { Text(location.marketplaceString("label").ifBlank { "Location" }) }) }
        }
        days.forEachIndexed { index, day ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                Switch(checked = day.open, onCheckedChange = { days[index] = day.copy(open = it) })
                Text(day.label, modifier = Modifier.weight(1f))
                if (day.open) {
                    OutlinedTextField(value = day.opensAt, onValueChange = { days[index] = day.copy(opensAt = it) }, label = { Text("Open") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = day.closesAt, onValueChange = { days[index] = day.copy(closesAt = it) }, label = { Text("Close") }, modifier = Modifier.weight(1f))
                }
            }
        }
        Text("Special-date exceptions", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) { listOf("CLOSED", "OPEN", "OPEN_24_HOURS", "APPOINTMENT_ONLY").forEach { value -> FilterChip(selected = exceptionState == value, onClick = { exceptionState = value }, label = { Text(value.replace('_', ' ')) }) } }
        if (exceptionState == "OPEN") Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
            OutlinedTextField(value = exceptionOpen, onValueChange = { exceptionOpen = it }, label = { Text("Open") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = exceptionClose, onValueChange = { exceptionClose = it }, label = { Text("Close") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { exceptions += HourExceptionDraft(date, exceptionState, exceptionOpen, exceptionClose, note); date = ""; note = "" }, enabled = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { Text("Add exception") }
        exceptions.forEach { exception -> Text("${exception.date} · ${exception.state.replace('_', ' ')}${exception.note.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}") }
        Button(onClick = {
            val hours = days.map { day -> buildJsonObject { put("dayOfWeek", day.day); put("intervalOrder", 1); put("state", if (day.open) "OPEN" else "CLOSED"); put("opensAt", if (day.open) day.opensAt else ""); put("closesAt", if (day.open) day.closesAt else "") } }
            val exceptionPayload = exceptions.map { value -> buildJsonObject { put("date", value.date); put("state", value.state); put("opensAt", if (value.state == "OPEN") value.opensAt else ""); put("closesAt", if (value.state == "OPEN") value.closesAt else ""); put("note", value.note) } }
            viewModel.saveHours(editor.businessId, locationId, hours, exceptionPayload)
        }, enabled = locationId.isNotBlank(), modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Replace saved hours") }
        Text("Saving replaces the selected location's weekly schedule and special-date exceptions atomically.", style = MaterialTheme.typography.bodySmall)
    }
}

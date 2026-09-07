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
internal fun NewMarketplaceDraftScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MarketplaceOwnerViewModel
) {
    val editorState by viewModel.editor.collectAsStateWithLifecycle()
    val savedDraft by viewModel.creationDraft.collectAsStateWithLifecycle(initialValue = BusinessCreationDraft())

    var name by rememberSaveable { mutableStateOf("") }
    var tagline by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var initialDraftLoaded by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    // Restore draft state from DataStore when opening if fields are empty
    LaunchedEffect(savedDraft) {
        if (!initialDraftLoaded && savedDraft.isNotEmpty) {
            if (name.isEmpty() && savedDraft.name.isNotEmpty()) name = savedDraft.name
            if (tagline.isEmpty() && savedDraft.tagline.isNotEmpty()) tagline = savedDraft.tagline
            if (description.isEmpty() && savedDraft.description.isNotEmpty()) description = savedDraft.description
            if (category.isEmpty() && savedDraft.category.isNotEmpty()) category = savedDraft.category
            if (phone.isEmpty() && savedDraft.phone.isNotEmpty()) phone = savedDraft.phone
            if (email.isEmpty() && savedDraft.email.isNotEmpty()) email = savedDraft.email
            initialDraftLoaded = true
        }
    }

    // Auto-save form state to DataStore in background while typing
    LaunchedEffect(name, tagline, description, category, phone, email) {
        if (name.isNotBlank() || tagline.isNotBlank() || description.isNotBlank() || phone.isNotBlank() || email.isNotBlank()) {
            viewModel.autoSaveCreationDraft(
                BusinessCreationDraft(
                    name = name,
                    tagline = tagline,
                    description = description,
                    category = category,
                    phone = phone,
                    email = email,
                    lastSavedTimestamp = System.currentTimeMillis(),
                )
            )
        }
    }

    val isLoading = editorState is MarketplaceLoadState.Loading
    val isFailure = editorState is MarketplaceLoadState.Failure
    val isData = editorState is MarketplaceLoadState.Data

    val nameTrimmed = name.trim()
    val isNameTooShort = name.isNotBlank() && nameTrimmed.length < 2
    val isNameTooLong = name.length > 100
    val isNameValid = nameTrimmed.length in 2..100

    val errorMessage = when {
        isNameTooShort -> "Business name must be at least 2 characters"
        isNameTooLong -> "Business name cannot exceed 100 characters"
        else -> null
    }

    if (submitted && isData) {
        val editor = (editorState as MarketplaceLoadState.Data<MarketplaceDraftEditor>).value
        LaunchedEffect(editor.businessId) {
            onNavigate("account/marketplace/business/${editor.businessId}/edit")
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscapeOrTablet = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    if (isLandscapeOrTablet) {
        // Landscape & Tablet Responsive Two-Column Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(RtcSpacing.pageGutter),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: Overview, Guidance & Auto-save Status Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to businesses"
                        )
                    }
                    Text(
                        text = "Create Business Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Marketplace Business Onboarding",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "Register your enterprise, local shop, services, or trade. Once created, you can set operating hours, physical & service areas, product catalogs, and photo galleries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // DataStore Auto-save Status Indicator
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-save Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                if (name.isNotBlank()) "Your progress is continuously saved to DataStore" else "Form state will automatically save as you type",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (name.isNotBlank() || tagline.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    name = ""
                                    tagline = ""
                                    description = ""
                                    phone = ""
                                    email = ""
                                    viewModel.clearCreationDraft()
                                }
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Right Column: Creation Form & Actions
            LazyColumn(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)
            ) {
                if (isFailure) {
                    item {
                        val errorMsg = (editorState as MarketplaceLoadState.Failure).message
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Creation Attempt Failed",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = errorMsg,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Your business details are safely preserved in auto-save. Tap 'Retry Submission' to try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (isNameValid) {
                                            submitted = true
                                            viewModel.createDraft(nameTrimmed)
                                        }
                                    },
                                    enabled = isNameValid && !isLoading,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry Submission")
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Business name *") },
                        placeholder = { Text("e.g. Khayelitsha Fresh Groceries & Cafe") },
                        singleLine = true,
                        enabled = !isLoading,
                        isError = errorMessage != null,
                        supportingText = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = errorMessage ?: "Enter your official or trading business name",
                                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${name.length}/100",
                                    color = if (isNameTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tagline / Short description (optional)") },
                        placeholder = { Text("e.g. Organic produce & daily baked bread") },
                        singleLine = true,
                        enabled = !isLoading
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(RtcSize.minimumTouchTarget)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (isNameValid) {
                                    submitted = true
                                    viewModel.createDraft(nameTrimmed)
                                }
                            },
                            enabled = isNameValid && !isLoading,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(RtcSize.minimumTouchTarget),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Creating…")
                            } else {
                                Icon(Icons.Filled.AddBusiness, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Profile")
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Portrait Mobile Single-Column Layout
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(RtcSpacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to businesses"
                        )
                    }
                    Text(
                        text = "Create Business Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text(
                    "Register your local business, trade, shop, or enterprise on the Community Marketplace. Once created, you can configure operating hours, location, offerings, and photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Auto-save Badge
            item {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Auto-saving draft in background to DataStore",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (name.isNotBlank() || tagline.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    name = ""
                                    tagline = ""
                                    description = ""
                                    phone = ""
                                    email = ""
                                    viewModel.clearCreationDraft()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (isFailure) {
                item {
                    val errorMsg = (editorState as MarketplaceLoadState.Failure).message
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Creation Attempt Failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Your business details are safely preserved in auto-save. Tap 'Retry Submission' to try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (isNameValid) {
                                        submitted = true
                                        viewModel.createDraft(nameTrimmed)
                                    }
                                },
                                enabled = isNameValid && !isLoading,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry Submission")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Business name *") },
                    placeholder = { Text("e.g. Khayelitsha Fresh Groceries & Cafe") },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    supportingText = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = errorMessage ?: "Enter your official or trading business name",
                                color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${name.length}/100",
                                color = if (isNameTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tagline / Short description (optional)") },
                    placeholder = { Text("e.g. Organic produce & daily baked bread") },
                    singleLine = true,
                    enabled = !isLoading
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(RtcSize.minimumTouchTarget)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (isNameValid) {
                                submitted = true
                                viewModel.createDraft(nameTrimmed)
                            }
                        },
                        enabled = isNameValid && !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(RtcSize.minimumTouchTarget),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Creating…")
                        } else {
                            Icon(Icons.Filled.AddBusiness, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Profile")
                        }
                    }
                }
            }
        }
    }
}

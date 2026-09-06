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

private data class WeeklyHoursDraft(val day: Int, val label: String, val open: Boolean = false, val opensAt: String = "08:00", val closesAt: String = "17:00")
private data class HourExceptionDraft(val date: String, val state: String, val opensAt: String, val closesAt: String, val note: String)

@Composable
fun MarketplaceOwnerRoute(
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceOwnerViewModel = hiltViewModel(),
) {
    val state by viewModel.businesses.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    MarketplaceLoadContainer(state, viewModel::loadBusinesses) { businesses ->
        LazyColumn(
            Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
        ) {
            item {
                Text("My businesses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Manage your local business listings, operating hours, photos, services, and team members.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarketplaceNotice(notice, viewModel::dismissNotice)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onNavigate(za.org.rtc.community.navigation.RtcRoute.MARKETPLACE_INVITATIONS) },
                        label = { Text("Team Invitations") },
                        leadingIcon = { Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    AssistChip(
                        onClick = { onNavigate(za.org.rtc.community.navigation.RtcRoute.MARKETPLACE_MY_REVIEWS) },
                        label = { Text("Reviews") },
                        leadingIcon = { Icon(Icons.Filled.RateReview, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    AssistChip(
                        onClick = { onNavigate(za.org.rtc.community.navigation.RtcRoute.MARKETPLACE_SAVED) },
                        label = { Text("Saved") },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            item {
                Button(
                    onClick = { onNavigate("account/marketplace/business/new") },
                    modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(Icons.Filled.AddBusiness, contentDescription = null)
                    Spacer(Modifier.size(RtcSpacing.compact))
                    Text("Create New Business Profile")
                }
            }
            if (businesses.isEmpty()) {
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
                                Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                "No Business Listings Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Grow your local enterprise by listing your services, contact channels, operating hours, and location on the Community Marketplace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onNavigate("account/marketplace/business/new") },
                                modifier = Modifier.height(RtcSize.minimumTouchTarget),
                            ) {
                                Icon(Icons.Filled.AddBusiness, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Get Started Now")
                            }
                        }
                    }
                }
            }
            items(businesses, key = { it.id }) { business -> OwnerBusinessCard(business, onNavigate, viewModel::archive) }
        }
    }
}

@Composable
private fun OwnerBusinessCard(business: MarketplaceOwnerBusiness, onNavigate: (String) -> Unit, onArchive: (String) -> Unit) {
    val context = LocalContext.current
    var confirmArchive by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var announcementSuccessNotice by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(business.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (business.revisionState == "DRAFT") {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("DRAFT IN PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
            Text("${business.lifecycleState.replace('_', ' ')} · ${business.revisionState.replace('_', ' ')} · ${business.role}")
            business.feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            announcementSuccessNotice?.let { noticeMsg ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = noticeMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap)) {
                Button(onClick = { onNavigate("account/marketplace/business/${business.id}/edit") }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) {
                    if (business.revisionState == "DRAFT") {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Continue Draft")
                    } else {
                        Text("Edit")
                    }
                }
                OutlinedButton(onClick = { showAnnouncementDialog = true }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Post Announcement")
                }
                OutlinedButton(onClick = { onNavigate("account/marketplace/business/${business.id}/preview") }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Preview") }
                OutlinedButton(onClick = { onNavigate("account/marketplace/business/${business.id}/status") }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Status") }
                if (business.lifecycleState != "ARCHIVED") OutlinedButton(onClick = { confirmArchive = true }, modifier = Modifier.height(RtcSize.minimumTouchTarget)) { Text("Archive") }
            }
        }
    }

    if (showAnnouncementDialog) {
        var annTitle by remember { mutableStateOf("") }
        var annBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = { Text("Post Business Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Alert all residents who bookmarked ${business.displayName}:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = annTitle,
                        onValueChange = { annTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = annBody,
                        onValueChange = { annBody = it },
                        label = { Text("Announcement Details") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (annTitle.isNotBlank() && annBody.isNotBlank()) {
                            showAnnouncementDialog = false
                            dispatchBusinessAnnouncementNotification(
                                context = context,
                                businessName = business.displayName,
                                title = annTitle,
                                body = annBody,
                            )
                            announcementSuccessNotice = "📢 Announcement published! Instant push notification alert dispatched to bookmarked residents."
                        }
                    },
                    enabled = annTitle.isNotBlank() && annBody.isNotBlank(),
                ) {
                    Text("Publish & Dispatch Push Alert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnnouncementDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmArchive) ConfirmMarketplaceActionDialog(
        title = "Archive business?",
        message = "This removes the business from active owner workflows. The backend remains authoritative for whether archiving is allowed in its current state.",
        confirmLabel = "Archive",
        destructive = true,
        onDismiss = { confirmArchive = false },
        onConfirm = { confirmArchive = false; onArchive(business.id) },
    )
}

@Composable
fun MarketplaceOwnerWizardRoute(
    businessId: String?,
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MarketplaceOwnerViewModel = hiltViewModel(),
) {
    val handleExit: () -> Unit = {
        if (onBack != null) {
            onBack()
        } else {
            onNavigate("account/marketplace/my-businesses")
        }
    }

    if (businessId == null) {
        NewMarketplaceDraftScreen(onNavigate = onNavigate, onBack = handleExit, viewModel = viewModel)
        return
    }
    val editorState by viewModel.editor.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val step by viewModel.currentStep.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.loadEditor(businessId) }
    MarketplaceLoadContainer(editorState, { viewModel.loadEditor(businessId) }) { editor ->
        MarketplaceOwnerEditorContent(
            editor = editor,
            categories = categories,
            step = step,
            notice = notice,
            onNavigate = onNavigate,
            onBack = handleExit,
            viewModel = viewModel
        )
    }
}

@Composable
private fun MarketplaceOwnerEditorContent(
    editor: MarketplaceDraftEditor,
    categories: List<MarketplaceCategory>,
    step: Int,
    notice: String?,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MarketplaceOwnerViewModel,
) {
    var displayName by rememberSaveable(editor.businessId) { mutableStateOf(editor.displayName) }
    var tagline by rememberSaveable(editor.businessId) { mutableStateOf(editor.tagline) }
    var description by rememberSaveable(editor.businessId) { mutableStateOf(editor.description) }
    var selectedCategories by remember(editor.businessId) { mutableStateOf(editor.categories.toSet()) }
    var primaryCategory by remember(editor.businessId) { mutableStateOf(editor.categories.firstOrNull().orEmpty()) }
    var phone by rememberSaveable(editor.businessId) { mutableStateOf(editor.phone) }
    var email by rememberSaveable(editor.businessId) { mutableStateOf(editor.email) }
    var website by rememberSaveable(editor.businessId) { mutableStateOf(editor.websiteUrl) }
    var whatsapp by rememberSaveable(editor.businessId) { mutableStateOf(false) }

    val submissionState by viewModel.submissionState.collectAsStateWithLifecycle()

    LaunchedEffect(submissionState) {
        if (submissionState is MarketplaceLoadState.Data) {
            viewModel.resetSubmissionState()
            onNavigate("account/marketplace/business/${editor.businessId}/status")
        }
    }

    val performSaveDraft: () -> Unit = {
        viewModel.saveIdentity(
            editor.businessId,
            identityPayload(editor, displayName, tagline, description, selectedCategories, primaryCategory, phone, email, website, whatsapp)
        )
        viewModel.checkpoint(editor.businessId, step)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(RtcSpacing.pageGutter),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Business editor · step $step of 9",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Draft ID: ${editor.businessId.take(8)}… · Auto-saved to device & cloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = performSaveDraft,
                        modifier = Modifier.height(RtcSize.minimumTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Save Draft",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Save Draft")
                    }
                    FilledTonalButton(
                        onClick = {
                            performSaveDraft()
                            onBack()
                        },
                        modifier = Modifier.height(RtcSize.minimumTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit Editor",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Exit")
                    }
                }
            }
            Spacer(Modifier.height(RtcSpacing.compact))
            LinearProgressIndicator(progress = { step / 9f }, modifier = Modifier.fillMaxWidth())
            MarketplaceNotice(notice, viewModel::dismissNotice)
        }
        item {
            when (step) {
                1 -> IdentityStep(
                    name = displayName,
                    onName = { displayName = it },
                    tagline = tagline,
                    onTagline = { tagline = it },
                    description = description,
                    onDescription = { description = it },
                    editor = editor,
                    selectedCategories = selectedCategories,
                    primaryCategory = primaryCategory,
                    categories = categories,
                    viewModel = viewModel
                )
                2 -> CategoryStep(categories, selectedCategories, primaryCategory) { selected, primary ->
                    selectedCategories = selected
                    primaryCategory = primary
                    viewModel.saveIdentity(editor.businessId, identityPayload(editor, displayName, tagline, description, selected, primary, phone, email, website, whatsapp))
                }
                3 -> LocationStep(editor, viewModel)
                4 -> HoursStep(editor, viewModel)
                5 -> OfferingStep(editor, viewModel)
                6 -> ContactStep(
                    editor = editor,
                    name = displayName,
                    tagline = tagline,
                    description = description,
                    categories = selectedCategories,
                    primary = primaryCategory,
                    phone = phone,
                    onPhone = { phone = it },
                    email = email,
                    onEmail = { email = it },
                    website = website,
                    onWebsite = { website = it },
                    whatsapp = whatsapp,
                    onWhatsapp = { whatsapp = it },
                    viewModel = viewModel
                )
                7 -> MediaStep(editor, viewModel)
                8 -> DraftPreview(editor)
                else -> SubmitStep(
                    editor = editor,
                    submissionState = submissionState,
                    onSubmit = { viewModel.submit(editor.businessId) },
                    onRetry = { viewModel.submit(editor.businessId) },
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (step == 1 || step == 2 || step == 6) {
                            performSaveDraft()
                        }
                        if (step == 1) {
                            onBack()
                        } else {
                            viewModel.checkpoint(editor.businessId, step - 1)
                        }
                    },
                    modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
                ) {
                    if (step == 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Exit Editor")
                    } else {
                        Text("Back")
                    }
                }

                OutlinedButton(
                    onClick = performSaveDraft,
                    modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Save Draft")
                }

                Button(
                    onClick = {
                        if (step == 1 || step == 2 || step == 6) {
                            performSaveDraft()
                        }
                        viewModel.checkpoint(editor.businessId, step + 1)
                    },
                    enabled = step < 9,
                    modifier = Modifier.weight(1f).height(RtcSize.minimumTouchTarget),
                ) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun NewMarketplaceDraftScreen(
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

@Composable
private fun IdentityStep(
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
private fun CategoryStep(categories: List<MarketplaceCategory>, selected: Set<String>, primary: String, onSave: (Set<String>, String) -> Unit) {
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

private fun identityPayload(editor: MarketplaceDraftEditor, name: String, tagline: String, description: String, categories: Set<String>, primary: String, phone: String = editor.phone, email: String = editor.email, website: String = editor.websiteUrl, whatsapp: Boolean = false): JsonObject = buildJsonObject {
    put("displayName", name.trim()); put("businessType", editor.businessType); put("tagline", tagline.trim()); put("description", description.trim())
    put("publicPhone", phone.trim()); put("publicEmail", email.trim()); put("websiteUrl", website.trim()); put("whatsappEnabled", whatsapp)
    put("ownerDeclared", true); put("publicContactConsent", true); put("publicAddressConsent", true); put("noEndorsementAcknowledged", true); put("accuracyDeclared", true)
    put("categories", buildJsonArray { categories.forEach { id -> add(buildJsonObject { put("id", id); put("primary", id == primary) }) } })
}

@Composable
private fun LocationStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
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
private fun HoursStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
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

@Composable
private fun OfferingStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
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
private fun ContactStep(
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
private fun MediaStep(editor: MarketplaceDraftEditor, viewModel: MarketplaceOwnerViewModel) {
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

@Composable
private fun DraftPreview(editor: MarketplaceDraftEditor) {
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("8. Draft preview", style = MaterialTheme.typography.titleLarge)
        Text("This preview is built from the current Supabase-backed private editor payload; it is not the public listing.", style = MaterialTheme.typography.bodySmall)
        Text(editor.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        editor.tagline.takeIf(String::isNotBlank)?.let { Text(it) }; Text(editor.description)
        Text("${editor.categories.size} categories · ${editor.locations.size} locations · ${editor.offerings.size} offerings · ${editor.media.size} media assets")
        editor.locations.forEach { location -> Text("Location: ${location.marketplaceString("label").ifBlank { location.marketplaceString("locality") }}") }
        editor.offerings.forEach { offering -> Text("Offering: ${offering.marketplaceString("title")}") }
    }
}

@Composable
private fun SubmitStep(
    editor: MarketplaceDraftEditor,
    submissionState: MarketplaceLoadState<Unit>,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    val hasName = editor.displayName.trim().length >= 2
    val hasDescription = editor.description.trim().length >= 10
    val hasCategory = editor.categories.isNotEmpty()
    val hasContactOrLoc = editor.phone.isNotBlank() || editor.email.isNotBlank() || editor.websiteUrl.isNotBlank() || editor.locations.isNotEmpty()

    val canSubmit = hasName && hasDescription && hasCategory && hasContactOrLoc
    val isLoading = submissionState is MarketplaceLoadState.Loading
    val isFailure = submissionState is MarketplaceLoadState.Failure

    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("9. Submit for review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Review your business profile completion checklist below before submitting for administrative verification.")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(RtcSpacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
            ) {
                Text("Submission Readiness Checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                ChecklistRow(
                    label = "Business Display Name (>= 2 chars)",
                    isComplete = hasName,
                    helpText = if (!hasName) "Step 1: Identity required" else null
                )
                ChecklistRow(
                    label = "Business Description / Story (>= 10 chars)",
                    isComplete = hasDescription,
                    helpText = if (!hasDescription) "Step 1: Description required" else null
                )
                ChecklistRow(
                    label = "Category Selected (at least 1 category)",
                    isComplete = hasCategory,
                    helpText = if (!hasCategory) "Step 2: Categories required" else null
                )
                ChecklistRow(
                    label = "Contact Method or Location Address",
                    isComplete = hasContactOrLoc,
                    helpText = if (!hasContactOrLoc) "Step 3: Location or Step 6: Contact required" else null
                )
            }
        }

        if (isFailure) {
            val errorMsg = (submissionState as MarketplaceLoadState.Failure).message
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
                            text = "Submission Attempt Failed",
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
                        text = "Your business profile data (identity, hours, locations, offerings, and media) remains safely preserved in your private draft. You will not lose any progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                    FilledTonalButton(
                        onClick = onRetry,
                        enabled = !isLoading,
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
        } else if (!canSubmit) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Please complete all required checklist items before submitting your profile for review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Button(
            onClick = {
                if (isFailure) onRetry() else onSubmit()
            },
            enabled = canSubmit && !isLoading,
            modifier = Modifier.fillMaxWidth().height(RtcSize.minimumTouchTarget)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Submitting Profile…")
            } else if (isFailure) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retry Submission")
            } else {
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Submit Profile For Review")
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, isComplete: Boolean, helpText: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isComplete) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isComplete) FontWeight.Normal else FontWeight.Medium,
                color = if (isComplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
            if (helpText != null) {
                Text(
                    text = helpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MarketplaceOwnerPreviewRoute(businessId: String, viewModel: MarketplaceOwnerViewModel = hiltViewModel()) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.loadEditor(businessId) }
    MarketplaceLoadContainer(state, { viewModel.loadEditor(businessId) }) { editor ->
        LazyColumn(Modifier.fillMaxSize().padding(RtcSpacing.pageGutter), verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap)) { item { DraftPreview(editor) } }
    }
}

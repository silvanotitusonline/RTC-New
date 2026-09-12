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
internal fun MarketplaceOwnerWizardRouteContent(
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
internal fun MarketplaceOwnerEditorContent(
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

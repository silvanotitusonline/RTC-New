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
internal fun DraftPreview(editor: MarketplaceDraftEditor) {
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
internal fun SubmitStep(
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
internal fun ChecklistRow(label: String, isComplete: Boolean, helpText: String?) {
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
internal fun MarketplaceOwnerPreviewRouteContent(businessId: String, viewModel: MarketplaceOwnerViewModel = hiltViewModel()) {
    val state by viewModel.editor.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.loadEditor(businessId) }
    MarketplaceLoadContainer(state, { viewModel.loadEditor(businessId) }) { editor ->
        LazyColumn(Modifier.fillMaxSize().padding(RtcSpacing.pageGutter), verticalArrangement = Arrangement.spacedBy(RtcSpacing.sectionGap)) { item { DraftPreview(editor) } }
    }
}

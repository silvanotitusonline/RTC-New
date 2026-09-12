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


internal data class WeeklyHoursDraft(val day: Int, val label: String, val open: Boolean = false, val opensAt: String = "08:00", val closesAt: String = "17:00")
internal data class HourExceptionDraft(val date: String, val state: String, val opensAt: String, val closesAt: String, val note: String)

@Composable
internal fun MarketplaceOwnerRouteContent(
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
internal fun OwnerBusinessCard(business: MarketplaceOwnerBusiness, onNavigate: (String) -> Unit, onArchive: (String) -> Unit) {
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

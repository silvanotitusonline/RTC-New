package za.org.rtc.community.feature.account

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import za.org.rtc.community.core.RtcNotification
import za.org.rtc.community.core.RtcSession
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.feature.community.CommunityAvatar
import za.org.rtc.community.ui.components.*
import za.org.rtc.community.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorSheet(
    session: RtcSession,
    pendingPhotoUri: Uri?,
    photoSaveInProgress: Boolean,
    photoSaveSucceeded: Boolean,
    photoMessage: String?,
    onSave: (String, String, List<String>) -> Unit,
    onChooseGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSavePhoto: (Uri) -> Unit,
    onClearSelectedPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(session.displayName) }
    var bio by rememberSaveable { mutableStateOf(session.bio) }
    var interests by rememberSaveable { mutableStateOf(session.interests.joinToString(", ")) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Profile photos are optional. In production, photos are cropped on-device and stored privately in Supabase Storage; initials remain the fallback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = session.handle, onValueChange = {}, label = { Text("Account handle") }, supportingText = { Text("Derived from your verified email and cannot be changed here.") }, readOnly = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(interests, { interests = it }, label = { Text("Interests, separated by commas") }, modifier = Modifier.fillMaxWidth())
            if (session.authority == SessionAuthority.SUPABASE_AUTH) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    if (pendingPhotoUri != null) CommunityAvatar(pendingPhotoUri.toString(), session.displayName, Modifier.size(RtcSize.avatarLarge)) else ProfileAvatar(session = session, modifier = Modifier.size(RtcSize.avatarLarge))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Profile photo", fontWeight = FontWeight.SemiBold)
                        Text(if (pendingPhotoUri == null) "Choose an image, preview it, then save it to your private account folder." else "Selected photo preview. Save it when you are satisfied.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onChooseGallery, modifier = Modifier.weight(1f), enabled = !photoSaveInProgress) { Text("Choose gallery") }
                    OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f), enabled = !photoSaveInProgress) { Text("Use camera") }
                }
                pendingPhotoUri?.let { selectedUri ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onClearSelectedPhoto, modifier = Modifier.weight(1f), enabled = !photoSaveInProgress) { Text("Choose another") }
                        Button(onClick = { onSavePhoto(selectedUri) }, modifier = Modifier.weight(1f), enabled = !photoSaveInProgress) { Text(if (photoSaveInProgress) "Saving…" else "Save selected photo") }
                    }
                }
                photoMessage?.let { message -> Text(message, style = MaterialTheme.typography.bodySmall, color = if (photoSaveSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                if (session.avatarUrl != null && pendingPhotoUri == null) TextButton(onClick = onRemovePhoto, modifier = Modifier.fillMaxWidth(), enabled = !photoSaveInProgress) { Text("Remove profile photo") }
            } else {
                Text("Sign in with your verified account to manage a private profile photo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onSave(name, bio, interests.split(",")) }, enabled = name.trim().length in 2..120 && bio.trim().length <= 600, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
            Spacer(Modifier.height(RtcSpacing.small))
        }
    }
}

@Composable
internal fun ProfileAvatar(session: RtcSession, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.clip(MaterialTheme.shapes.extraLarge)) {
        if (session.avatarUrl != null) {
            AsyncImage(model = session.avatarUrl, contentDescription = "${session.displayName}'s profile photo", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) { Text(session.displayName.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationPreferencesSheet(
    supportEnabled: Boolean,
    communityEnabled: Boolean,
    onSupportChange: (Boolean) -> Unit,
    onCommunityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Notification preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PreferenceSwitch("Support-case updates", "Stages change or staff need information from you.", supportEnabled, onSupportChange)
            PreferenceSwitch("Community activity", "Replies, mentions, and followed-topic updates.", communityEnabled, onCommunityChange)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("Official notices stay enabled so you do not miss important community information.", modifier = Modifier.padding(RtcSpacing.small), style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.height(RtcSpacing.small))
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationPanel(
    notifications: List<RtcNotification>,
    onDismiss: () -> Unit,
    onOpen: (RtcNotification) -> Unit,
    onViewAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Recent notifications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val recent = notifications.sortedByDescending { it.unread }.take(4)
            if (recent.isEmpty()) Text("You are up to date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else recent.forEach { notification ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(notification) }) {
                    Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (notification.unread) Badge()
                            if (notification.unread) Spacer(Modifier.width(RtcSpacing.compact))
                            Text(notification.title, fontWeight = FontWeight.SemiBold)
                        }
                        Text(notification.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(notification.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = onViewAll, modifier = Modifier.fillMaxWidth()) { Text("View all notifications") }
            Spacer(Modifier.height(RtcSpacing.compact))
        }
    }
}

@Composable
internal fun NotificationsScreen(
    notifications: List<RtcNotification>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMarkRead: () -> Unit,
    onOpen: (RtcNotification) -> Unit,
) {
    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item { RtcSectionHeader(title = "Notifications", subtitle = "Support, Community and official RTC updates in one place.", trailing = { TextButton(onClick = onMarkRead) { Text("Mark all read") } }) }
            if (notifications.isEmpty()) item { RtcEmptyState("You are up to date", "New Support, Community and official updates will appear here.") }
            else items(notifications) { notification ->
                RtcCard(onClick = { onOpen(notification) }) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = if (notification.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(notification.title, fontWeight = if (notification.unread) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (notification.unread) RtcStatusChip("New", RtcStatusTone.SUCCESS)
                            }
                            Text(notification.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(notification.createdAt, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

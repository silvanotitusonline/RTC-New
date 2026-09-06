package za.org.rtc.community.feature.account

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.*
import za.org.rtc.community.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountScreen(
    viewModel: RtcViewModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onHelp: () -> Unit,
    onMarketplace: (String) -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val authenticationUi by viewModel.authenticationUi.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val passwordUi by viewModel.passwordUi.collectAsStateWithLifecycle()
    val passwordRecoveryActive by viewModel.passwordRecoveryActive.collectAsStateWithLifecycle()
    val declaredLocalityUi by viewModel.declaredLocalityUi.collectAsStateWithLifecycle()
    var exportDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var notificationPreferencesOpen by rememberSaveable { mutableStateOf(false) }
    var declaredLocalityOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedProfilePhotoUri by remember { mutableStateOf<Uri?>(null) }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedProfilePhotoUri = uri
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) selectedProfilePhotoUri = pendingCameraUri
        pendingCameraUri = null
    }
    var passwordChangeOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(passwordRecoveryActive) { if (passwordRecoveryActive) passwordChangeOpen = true }
    LaunchedEffect(authenticationUi.isSuccess) {
        if (authenticationUi.isSuccess) selectedProfilePhotoUri = null
    }
    ResidentPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        RtcScreenScaffold {
            item {
                RtcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ProfileAvatar(session = session, modifier = Modifier.size(RtcSize.avatarProfile))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = session.handle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Text(
                                        text = session.role.name.replace('_', ' '),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        if (session.bio.isNotBlank()) {
                            Text(
                                text = session.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        if (session.interests.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                session.interests.take(3).forEach { interest ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(interest, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        Button(
                            onClick = { profileOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Profile")
                        }
                    }
                }
            }

            item { AccountSectionTitle("Civic Geofencing & Safety Alerts") }
            item {
                var geofenceActive by rememberSaveable { mutableStateOf(true) }
                RtcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(RtcSpacing.compact))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Background High-Issue Geofencing", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Sends real-time alerts when you enter areas with high concentrations of reported potholes, leaks, or safety hazards.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = geofenceActive,
                                onCheckedChange = { active ->
                                    geofenceActive = active
                                    if (active) {
                                        za.org.rtc.community.geofence.HighIssueGeofenceService.start(context)
                                    } else {
                                        za.org.rtc.community.geofence.HighIssueGeofenceService.stop(context)
                                    }
                                },
                            )
                        }

                        Text(
                            text = "Monitored Hotspots: Main Rd Corridor (8 reports), Community Park Entrance (5 reports), Rec Centre Sector 2 (6 reports).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        OutlinedButton(
                            onClick = {
                                za.org.rtc.community.geofence.HighIssueGeofenceService.sendHotspotNotification(
                                    context = context,
                                    hotspot = za.org.rtc.community.geofence.HighIssueGeofenceData.HOTSPOTS.first(),
                                    distanceMeters = 180,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test High-Issue Zone Alert Notification")
                        }
                    }
                }
            }

            item { AccountSectionTitle("Profile and experience") }
            item {
                RtcCard {
                    Text("Appearance", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        ThemePreference.entries.forEach { preference ->
                            AssistChip(
                                onClick = { viewModel.setTheme(preference) },
                                label = { Text(preference.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                leadingIcon = { Icon(if (preference == ThemePreference.DARK) Icons.Filled.DarkMode else Icons.Filled.LightMode, null, modifier = Modifier.size(RtcSize.inlineIcon)) },
                                colors = if (session.darkMode == preference) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(RtcSpacing.compact))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dynamic color (Material You)", fontWeight = FontWeight.SemiBold)
                                Text("Harmonizes UI colors with your system wallpaper on Android 12+.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = session.dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AccessibilityNew, null)
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Simplified reading mode", fontWeight = FontWeight.SemiBold)
                            Text("Uses clearer spacing and larger content on public screens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = session.readingMode, onCheckedChange = { viewModel.toggleReadingMode() })
                    }
                }
            }
            item { AccountSectionTitle("Marketplace Business & Settings") }
            item {
                var marketplaceExpanded by rememberSaveable { mutableStateOf(true) }
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    AccountExpandableHeader(
                        title = "Marketplace & Business Hub",
                        description = "Manage business listings, saved bookmarks, team invitations, and review history.",
                        icon = Icons.Filled.Storefront,
                        expanded = marketplaceExpanded,
                        onToggle = { marketplaceExpanded = !marketplaceExpanded }
                    )
                    
                    AnimatedVisibility(
                        visible = marketplaceExpanded,
                        enter = expandVertically(animationSpec = za.org.rtc.community.ui.animation.RtcMotionPatterns.standardDecelerateTween()) + fadeIn(animationSpec = za.org.rtc.community.ui.animation.RtcMotionPatterns.standardDecelerateTween()),
                        exit = shrinkVertically(animationSpec = za.org.rtc.community.ui.animation.RtcMotionPatterns.standardAccelerateTween()) + fadeOut(animationSpec = za.org.rtc.community.ui.animation.RtcMotionPatterns.standardAccelerateTween())
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
                        ) {
                            AccountAction(
                                title = "My businesses (Edit & Update)",
                                description = "View, edit, and update your business details, photos, opening hours, and offerings.",
                                icon = Icons.Filled.Business
                            ) { onMarketplace(RtcRoute.MARKETPLACE_MY_BUSINESSES) }

                            AccountAction(
                                title = "Create new business profile",
                                description = "List your local enterprise, services, and location on the Community Marketplace.",
                                icon = Icons.Filled.AddBusiness
                            ) { onMarketplace(RtcRoute.MARKETPLACE_BUSINESS_NEW) }

                            AccountAction(
                                title = "Business team invitations",
                                description = "Manage received invitations to co-manage community business listings.",
                                icon = Icons.Filled.GroupAdd
                            ) { onMarketplace(RtcRoute.MARKETPLACE_INVITATIONS) }
                            
                            AccountAction(
                                title = "Saved businesses",
                                description = "View bookmarked businesses.",
                                icon = Icons.Filled.Favorite
                            ) { onMarketplace(RtcRoute.MARKETPLACE_SAVED) }
                            
                            AccountAction(
                                title = "My reviews & ratings",
                                description = "Manage your reviews and ratings.",
                                icon = Icons.Filled.RateReview
                            ) { onMarketplace(RtcRoute.MARKETPLACE_MY_REVIEWS) }
                        }
                    }
                }
            }
            item { AccountSectionTitle("Bookings & Service Requests") }
            item { BookingStatusTrackerCard(onNavigateToBooking = { onMarketplace(it) }) }
            item { AccountSectionTitle("My activity") }
            item { RtcCard { Text("Activity history", fontWeight = FontWeight.SemiBold); Text("Saved items and reviews are listed above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (pendingSyncCount > 0) item { PendingSyncIndicator(pendingSyncCount, "Pending offline sync.") }
            item { AccountSectionTitle("Notifications") }
            item { AccountAction("Notification preferences", "Manage notification categories.", Icons.Filled.Notifications) { notificationPreferencesOpen = true } }
            item {
                AccountAction(
                    "Device notification permission",
                    if (notificationsAllowed) "Notifications enabled." else "Disabled. Open settings to enable.",
                    Icons.Filled.Notifications
                ) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            }
            item { AccountSectionTitle("Security") }
            item { AccountAction("Change password", "Update account password.", Icons.Filled.Edit) { passwordChangeOpen = true } }
            item { AccountSectionTitle("Privacy and data") }
            item { AccountAction("Declared locality (optional)", session.declaredLocality ?: "Not set. Add a locality for aggregated reports.", Icons.Filled.LocationOn) { declaredLocalityOpen = true } }
            item { AccountAction("Request my data export", "Export your account data archive.", Icons.Filled.Description) { exportDialog = true } }
            item { AccountAction("Request account deletion", "Permanently delete your account.", Icons.Filled.DeleteOutline) { deleteDialog = true } }
            item { AccountSectionTitle("Help and feedback") }
            item { AccountAction("Help Centre", "View FAQs and support guides.", Icons.AutoMirrored.Filled.HelpOutline, onHelp) }
            item { AccountAction("Interactive feature tour", "Guided tour of features.", Icons.Filled.Explore) { viewModel.showInteractiveTutorial() } }
            item { AccountAction("Send feedback", "Send thoughts or bug reports.", Icons.Filled.ChatBubbleOutline) { feedbackOpen = true } }
            if (BuildConfig.DEBUG && session.role.isStaff) {
                item { AccountSectionTitle("Development identity adapter") }
                item {
                    RtcCard {
                        Text("Synthetic role preview", fontWeight = FontWeight.SemiBold)
                        Text("Development-only identity simulation. Production role enforcement remains server-controlled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                            listOf(UserRole.RESIDENT_A, UserRole.CONTENT_EDITOR, UserRole.MODERATOR, UserRole.SYSTEM_ADMIN).forEach { role ->
                                AssistChip(onClick = { viewModel.switchRole(role) }, label = { Text(role.name.replace('_', ' ')) })
                            }
                        }
                    }
                }
            }
            item { AccountAction("Sign out", "End this session on this device.", Icons.Filled.Logout, viewModel::signOutToPublicWelcome) }
        }
    }
    if (exportDialog) PrivacyDialog("Request data export", "We will prepare a machine-readable archive containing only your permitted data. The download will expire after a limited time.", "Request export", { exportDialog = false }) { exportDialog = false }
    if (deleteDialog) PrivacyDialog("Request account deletion", "To protect your account, you must reauthenticate before submitting the request. We will explain the treatment of your public contributions before confirmation.", "Continue to reauthenticate", { deleteDialog = false }) { deleteDialog = false }
    if (feedbackOpen) FeedbackSheet(viewModel = viewModel, onDismiss = { feedbackOpen = false })
    if (profileOpen) ProfileEditorSheet(
        session = session,
        pendingPhotoUri = selectedProfilePhotoUri,
        photoSaveInProgress = authenticationUi.isWorking,
        photoSaveSucceeded = authenticationUi.isSuccess,
        photoMessage = authenticationUi.message,
        onSave = { name, bio, interests -> viewModel.updateProfile(name, bio, interests); profileOpen = false },
        onChooseGallery = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onTakePhoto = {
            val directory = File(context.cacheDir, "profile_photos").apply { mkdirs() }
            val file = File.createTempFile("avatar_", ".jpg", directory)
            val captureUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = captureUri
            cameraCapture.launch(captureUri)
        },
        onSavePhoto = viewModel::uploadProfilePhoto,
        onClearSelectedPhoto = { selectedProfilePhotoUri = null },
        onRemovePhoto = viewModel::deleteProfilePhoto,
        onDismiss = { profileOpen = false }
    )
    if (notificationPreferencesOpen) NotificationPreferencesSheet(
        supportEnabled = session.supportNotifications,
        communityEnabled = session.communityNotifications,
        onSupportChange = { viewModel.setNotificationPreference("support", it) },
        onCommunityChange = { viewModel.setNotificationPreference("community", it) },
        onDismiss = { notificationPreferencesOpen = false }
    )
    if (declaredLocalityOpen) DeclaredLocalitySheet(
        initialLocality = session.declaredLocality,
        localityUi = declaredLocalityUi,
        onSave = viewModel::saveDeclaredLocality,
        onDismiss = { declaredLocalityOpen = false; viewModel.dismissDeclaredLocalityMessage() }
    )
    if (passwordChangeOpen) PasswordUpdateDialog(
        isRecoveryFlow = passwordRecoveryActive,
        passwordUi = passwordUi,
        onUpdate = viewModel::updatePassword,
        onDismiss = { passwordChangeOpen = false; viewModel.dismissPasswordUi(); viewModel.finishPasswordRecovery() }
    )
}

private data class TrackerBookingItem(
    val id: String,
    val providerName: String,
    val serviceTitle: String,
    val dateText: String,
    val status: String,
    val offerAmount: String,
    val locationText: String,
)

@Composable
private fun BookingStatusTrackerCard(
    onNavigateToBooking: (String) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }

    val sampleBookings = remember {
        listOf(
            TrackerBookingItem(
                id = "bk-101",
                providerName = "Apex Electrical & Plumbing Services",
                serviceTitle = "Solar Inverter & DB Board Inspection",
                dateText = "Tomorrow at 10:00 AM",
                status = "CONFIRMED",
                offerAmount = "R 650.00",
                locationText = "Main Rd Corridor, Sector 4",
            ),
            TrackerBookingItem(
                id = "bk-102",
                providerName = "RTC Community Handyman",
                serviceTitle = "Geyser Valve Repair & Leak Check",
                dateText = "Pending Provider Response",
                status = "PENDING",
                offerAmount = "R 350.00",
                locationText = "Rec Centre Sector 2",
            ),
            TrackerBookingItem(
                id = "bk-103",
                providerName = "Northern Cape Auto Care",
                serviceTitle = "Annual Vehicle Inspection & Oil Change",
                dateText = "3 Sep 2026 at 14:30 PM",
                status = "COMPLETED",
                offerAmount = "R 850.00",
                locationText = "Industrial Zone, Lot 12",
            ),
        )
    }

    val filteredBookings = remember(sampleBookings, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> sampleBookings.filter { it.status == "PENDING" }
            "CONFIRMED" -> sampleBookings.filter { it.status == "CONFIRMED" }
            "COMPLETED" -> sampleBookings.filter { it.status == "COMPLETED" }
            else -> sampleBookings
        }
    }

    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Booking Status Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${sampleBookings.size} Requests",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    "ALL" to "All",
                    "PENDING" to "Pending",
                    "CONFIRMED" to "Confirmed",
                    "COMPLETED" to "Completed",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = (selectedFilter == key),
                        onClick = { selectedFilter = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (filteredBookings.isEmpty()) {
                Text(
                    text = "No service requests match the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                filteredBookings.forEach { booking ->
                    BookingTimelineCard(booking = booking, onNavigateToBooking = onNavigateToBooking)
                }
            }
        }
    }
}

@Composable
private fun BookingTimelineCard(
    booking: TrackerBookingItem,
    onNavigateToBooking: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToBooking("account/service-centre/booking/${booking.id}") },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.serviceTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = booking.providerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (booking.status) {
                        "PENDING" -> MaterialTheme.colorScheme.tertiaryContainer
                        "CONFIRMED" -> MaterialTheme.colorScheme.primaryContainer
                        "COMPLETED" -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        text = booking.status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = when (booking.status) {
                            "PENDING" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "CONFIRMED" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "COMPLETED" -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "📍 ${booking.locationText} · 🕒 ${booking.dateText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = booking.offerAmount,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Timeline Progress Visualizer
            BookingTimelineVisualizer(status = booking.status)

            OutlinedButton(
                onClick = { onNavigateToBooking("account/service-centre/booking/${booking.id}") },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("View Timeline & Chat", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun BookingTimelineVisualizer(status: String) {
    val currentStepIndex = when (status) {
        "PENDING" -> 1
        "CONFIRMED" -> 2
        "COMPLETED" -> 3
        else -> 1
    }

    val steps = listOf(
        "Requested",
        "Confirmed",
        "Completed",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEachIndexed { index, label ->
                val stepNum = index + 1
                val isDone = stepNum <= currentStepIndex
                val isCurrent = stepNum == currentStepIndex

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        } else {
                            Text(
                                text = stepNum.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }

                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (stepNum < currentStepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AccountExpandableHeader(
    title: String,
    description: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    RtcCard(onClick = onToggle) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


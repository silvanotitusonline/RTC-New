package za.org.rtc.community.feature.administration

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import za.org.rtc.community.app.AdministratorMfaUiState
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.core.UserRole
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcEmptyState
import za.org.rtc.community.ui.theme.RtcAspectRatio
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OperationsWorkItemCard(
    item: OperationsWorkItem,
    canReassign: Boolean,
    onClaim: () -> Unit,
    onRelease: (String) -> Unit,
    onReadyForReview: (String) -> Unit,
    onReassign: (String, String) -> Unit,
    onOpen: () -> Unit,
) {
    var releaseOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var readyOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var reassignOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(item.priority.lowercase().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${item.sourceType.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)} · ${item.state.lowercase().replaceFirstChar(Char::titlecase)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            when {
                item.isUnassigned -> OutlinedButton(onClick = onClaim, modifier = Modifier.align(Alignment.End)) { Text("Claim") }
                item.assignedToMe -> {
                    Text("Assigned to you", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        OutlinedButton(onClick = { readyOpen = true }) { Text("Ready for review") }
                        TextButton(onClick = { releaseOpen = true }) { Text("Release") }
                        if (canReassign) TextButton(onClick = { reassignOpen = true }) { Text("Reassign") }
                    }
                }
                canReassign -> TextButton(onClick = { reassignOpen = true }, modifier = Modifier.align(Alignment.End)) { Text("Reassign") }
            }
        }
    }
    if (releaseOpen) WorkItemReasonDialog("Release work item", "Release reason", "Release", { reason -> onRelease(reason); releaseOpen = false }, { releaseOpen = false })
    if (readyOpen) WorkItemReasonDialog("Mark ready for review", "Review note", "Mark ready", { note -> onReadyForReview(note); readyOpen = false }, { readyOpen = false })
    if (reassignOpen) WorkItemReassignDialog({ ownerId, reason -> onReassign(ownerId, reason); reassignOpen = false }, { reassignOpen = false })
}

@Composable
private fun WorkItemReasonDialog(title: String, label: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(reason, { reason = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 3) }, confirmButton = { Button(onClick = { onConfirm(reason.trim()) }, enabled = reason.trim().length >= 3) { Text(confirmLabel) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun WorkItemReassignDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var ownerId by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reassign work item") }, text = { Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Text("Enter the active staff member’s account ID and a reason. The server verifies role eligibility and records immutable assignment history."); OutlinedTextField(ownerId, { ownerId = it }, label = { Text("New owner account ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(reason, { reason = it }, label = { Text("Reassignment reason") }, modifier = Modifier.fillMaxWidth(), minLines = 3) } }, confirmButton = { Button(onClick = { onConfirm(ownerId.trim(), reason.trim()) }, enabled = ownerId.trim().isNotEmpty() && reason.trim().length >= 3) { Text("Reassign") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun AdminWorkspaceMetricTile(value: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.heightIn(min = RtcSize.minimumTouchTarget * 2).clickable(role = Role.Button, onClick = onClick)) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
internal fun AdminReferenceToolTile(title: String, description: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.heightIn(min = RtcSize.minimumTouchTarget * 2).clickable(role = Role.Button, onClick = onClick)) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
internal fun AdminReferenceListRow(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = RtcSize.minimumTouchTarget * 2).clickable(role = Role.Button, onClick = onClick)) { Row(modifier = Modifier.padding(RtcSpacing.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = 180f }, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminWorkspaceNavigation(role: UserRole, requiresMfa: Boolean, onNavigate: (String) -> Unit) {
    RtcCard(protected = role == UserRole.SYSTEM_ADMIN) {
        Text("Workspace navigation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Every destination remains scoped to your role. Protected Administrator destinations request MFA first where configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            OutlinedButton(onClick = { onNavigate(RtcRoute.WORK_QUEUE) }) { Text("Work Queue") }
            OutlinedButton(onClick = { onNavigate(RtcRoute.MY_WORK) }) { Text("My Work Profile") }
            if (role.isStaff) {
                OutlinedButton(onClick = { onNavigate(RtcRoute.PUBLIC_REPORTS_ADMIN) }) { Text("Public Reports") }
            }
            if (role == UserRole.SYSTEM_ADMIN) {
                OutlinedButton(onClick = { onNavigate(if (requiresMfa) RtcRoute.ADMIN_MFA else RtcRoute.ADMIN_ACTIVITY) }) { Text("Administrative Activity") }
                OutlinedButton(onClick = { onNavigate(if (requiresMfa) RtcRoute.ADMIN_MFA else RtcRoute.SYSTEM_HEALTH) }) { Text("System Health") }
                OutlinedButton(onClick = { onNavigate(if (requiresMfa) RtcRoute.ADMIN_MFA else RtcRoute.ADMIN_BRANDING) }) { Text("Brand & Experience") }
            }
        }
    }
}

@Composable
internal fun TotpQrCode(uri: String) {
    val bitmap = remember(uri) {
        runCatching {
            val size = 512
            val matrix = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size)
            for (y in 0 until size) for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
        }.getOrNull()
    }
    if (bitmap == null) RtcEmptyState("Authenticator QR unavailable", "Cancel setup and retry. The app does not persist the enrollment secret.") else Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.widthIn(max = RtcSize.qrCode).aspectRatio(RtcAspectRatio.square)) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = "One-time administrator authenticator enrollment QR code", modifier = Modifier.fillMaxSize().padding(RtcSpacing.small)) } }
}

@Composable
internal fun AdministratorMfaDialog(mfaStatus: AdministratorMfaStatus, mfaUi: AdministratorMfaUiState, onEnroll: () -> Unit, onVerify: (String?, String) -> Unit, onDismiss: () -> Unit) {
    var verificationCode by rememberSaveable { mutableStateOf("") }
    val enrollment = mfaUi.enrollment
    val needsEnrollment = mfaStatus == AdministratorMfaStatus.ENROLLMENT_REQUIRED && enrollment == null
    AlertDialog(onDismissRequest = { if (!mfaUi.isWorking) onDismiss() }, title = { Text("Administrator MFA") }, text = { Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Text(if (mfaStatus == AdministratorMfaStatus.ENROLLMENT_REQUIRED) "Operational Controls require a TOTP app authenticator. This setup secret is shown only in this screen and is never stored by the app." else "Enter the current code from your registered app authenticator to continue to Operational Controls."); enrollment?.let { Text("Scan this one-time QR code with your authenticator app.", fontWeight = FontWeight.SemiBold); TotpQrCode(uri = it.uri); Text("The enrollment URI and secret remain only in memory for this setup dialog. After scanning, enter the current code below to verify the factor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (!needsEnrollment) OutlinedTextField(value = verificationCode, onValueChange = { verificationCode = it.filter(Char::isDigit).take(8) }, label = { Text("Authenticator code") }, singleLine = true, modifier = Modifier.fillMaxWidth()); mfaUi.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { Button(enabled = !mfaUi.isWorking && (needsEnrollment || verificationCode.length in 6..8), onClick = { if (needsEnrollment) onEnroll() else onVerify(enrollment?.factorId, verificationCode) }) { Text(if (mfaUi.isWorking) "Please wait" else if (needsEnrollment) "Set up authenticator" else "Verify and continue") } }, dismissButton = { TextButton(enabled = !mfaUi.isWorking, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun AdminPendingModerationSummary(
    dashboardState: AdminDashboardUiState,
    onRefresh: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenWorkQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RtcCard(protected = true, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PENDING MODERATION TASKS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Live production work overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (dashboardState.realtimeStatus) {
                        AdminRealtimeStatus.LIVE -> "Live updates · ${dashboardState.lastUpdatedLabel()}"
                        AdminRealtimeStatus.CONNECTING -> "Connecting to live updates · ${dashboardState.lastUpdatedLabel()}"
                        AdminRealtimeStatus.DISCONNECTED -> "Refresh required · ${dashboardState.lastUpdatedLabel()}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (dashboardState.realtimeStatus) {
                        AdminRealtimeStatus.LIVE -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Surface(
                color = if (dashboardState.totalPendingTasks > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = RtcSpacing.compact, vertical = RtcSpacing.tiny),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.tiny)
                ) {
                    if (dashboardState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.heightIn(max = 14.dp).widthIn(max = 14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "${dashboardState.totalPendingTasks} Pending",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (dashboardState.totalPendingTasks > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            "Counts are returned by the protected production summary RPC. Open a surface to review or change its live frontend state.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)
        ) {
            AdminWorkspaceMetricTile(
                value = dashboardState.reportsCount.toString(),
                label = "Reports",
                modifier = Modifier.weight(1f),
                onClick = onOpenReports
            )
            AdminWorkspaceMetricTile(
                value = dashboardState.noticesCount.toString(),
                label = "Notices",
                modifier = Modifier.weight(1f),
                onClick = onOpenNotices
            )
            AdminWorkspaceMetricTile(
                value = dashboardState.eventsCount.toString(),
                label = "Events",
                modifier = Modifier.weight(1f),
                onClick = onOpenEvents
            )
            AdminWorkspaceMetricTile(
                value = dashboardState.workQueueCount.toString(),
                label = "Work queue",
                modifier = Modifier.weight(1f),
                onClick = onOpenWorkQueue
            )
        }

        if (!dashboardState.isAuthorized) {
            Text(
                "Notice: this workspace requires an authorised staff session. System administrator actions may also require MFA.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (dashboardState.errorMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dashboardState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh) {
                    Text("Retry")
                }
            }
        }
    }
}

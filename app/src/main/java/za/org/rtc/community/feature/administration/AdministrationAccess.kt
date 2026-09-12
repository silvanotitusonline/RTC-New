package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.core.AccessManagedAccount
import za.org.rtc.community.core.AccessRoleAuditEvent
import za.org.rtc.community.core.AccessRoleChangeRequest
import za.org.rtc.community.core.AdminAccountLookupPurpose
import za.org.rtc.community.core.AdminAnalyticsPeriod
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.ManagedAccessRole
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.feature.community.relativeTimeLabel
import za.org.rtc.community.ui.components.DirectoryEmptyState
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcProtectedAreaBanner
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcWorkQueueCard
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun AdministratorMfaVerificationScreen(viewModel: RtcViewModel, onReturnToWorkQueue: () -> Unit) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val mfaUi by viewModel.administratorMfaUi.collectAsStateWithLifecycle()
    var verificationCode by rememberSaveable { mutableStateOf("") }
    val enrollment = mfaUi.enrollment
    val needsEnrollment = session.administratorMfaStatus == AdministratorMfaStatus.ENROLLMENT_REQUIRED && enrollment == null
    LaunchedEffect(session.authority, session.administratorMfaStatus) {
        if (session.authority == SessionAuthority.SUPABASE_AUTH && session.administratorMfaStatus == AdministratorMfaStatus.VERIFIED) onReturnToWorkQueue()
    }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RtcSize.heroIcon * 2))
                Text("SYSTEM ADMINISTRATOR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Verify administrator MFA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Administrator MFA verification is required before using protected administration tools.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    Text("AUTHENTICATOR CODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    enrollment?.let { Text("Scan this one-time QR code with your authenticator app.", style = MaterialTheme.typography.bodySmall); TotpQrCode(uri = it.uri) }
                    if (!needsEnrollment) OutlinedTextField(value = verificationCode, onValueChange = { verificationCode = it.filter(Char::isDigit).take(8) }, label = { Text("Authenticator code") }, singleLine = true, enabled = !mfaUi.isWorking, modifier = Modifier.fillMaxWidth())
                    mfaUi.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (session.administratorMfaStatus == AdministratorMfaStatus.VERIFIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = { if (needsEnrollment) viewModel.enrollSystemAdministratorTotp() else viewModel.verifySystemAdministratorTotp(enrollment?.factorId, verificationCode) },
                        enabled = !mfaUi.isWorking && (needsEnrollment || verificationCode.length in 6..8),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (mfaUi.isWorking) "Please wait" else if (needsEnrollment) "Set up authenticator" else "Verify and continue") }
                }
            }
        }
        item { RtcCard(protected = true) { Text("Protected by design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Access Management, Operational Controls and Privacy Analytics require a verified app authenticator where configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { OutlinedButton(onClick = onReturnToWorkQueue, enabled = !mfaUi.isWorking, modifier = Modifier.fillMaxWidth()) { Text("Return to Work Queue") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccessManagementScreen(viewModel: RtcViewModel) {
    val selectedAccount by viewModel.accessManagedAccount.collectAsStateWithLifecycle()
    val pendingRequests by viewModel.accessRoleChangeRequests.collectAsStateWithLifecycle()
    val auditEvents by viewModel.accessRoleAuditEvents.collectAsStateWithLifecycle()
    val accessUi by viewModel.accessManagementUi.collectAsStateWithLifecycle()
    var emailQuery by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var selectedRoleWire by rememberSaveable(selectedAccount?.userId) { mutableStateOf(selectedAccount?.effectiveRole ?: ManagedAccessRole.RESIDENT.wireValue) }
    LaunchedEffect(Unit) { viewModel.refreshAccessManagement() }
    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item { RtcSectionHeader("Access Management", "Exact verified-account lookup, deliberate role changes, dual-administrator approval, and immutable access history.") }
        item { RtcProtectedAreaBanner("Protected administration. Role changes are server-authorised and audited; System Administrator changes require approval by the other System Administrator and a fresh authorised session.") }
        accessUi.message?.let { message -> item { CommunityActionFeedback(message = message, isError = !accessUi.isSuccess, onDismiss = viewModel::dismissAccessManagementMessage) } }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    Text("Find a verified account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = emailQuery, onValueChange = { emailQuery = it }, label = { Text("Complete account email") }, supportingText = { Text("Only exact, existing, email-confirmed RTC Community accounts can be selected.") }, singleLine = true, enabled = !accessUi.isWorking, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.searchAccessManagedAccount(emailQuery) }, enabled = !accessUi.isWorking && emailQuery.trim().contains('@'), modifier = Modifier.fillMaxWidth()) { Text(if (accessUi.isWorking) "Checking…" else "Find account") }
                }
            }
        }
        selectedAccount?.let { account ->
            item { AccessRoleAssignmentCard(account, selectedRoleWire, reason, accessUi.isWorking, { selectedRoleWire = it }, { reason = it }, { viewModel.saveAccessRoleAssignment(account.userId, selectedRoleWire, reason) }, { reason = ""; viewModel.clearAccessManagedAccount() }) }
        }
        item { Text("Pending Administrator approvals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (pendingRequests.isEmpty()) item { DirectoryEmptyState("There are no Administrator role changes awaiting a second decision.") }
        else items(pendingRequests, key = { it.id }) { request -> AccessRoleChangeRequestCard(request, accessUi.isWorking, { viewModel.decideAccessRoleChangeRequest(request.id, approve = true) }, { viewModel.decideAccessRoleChangeRequest(request.id, approve = false) }) }
        item { Text("Access audit history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (auditEvents.isEmpty()) item { DirectoryEmptyState("Role-management decisions will appear here after the first protected change.") }
        else items(auditEvents, key = { it.id }) { event -> AccessRoleAuditEventCard(event) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdministratorPrivacyAnalyticsScreen(viewModel: RtcViewModel) {
    val dashboard by viewModel.adminAnalyticsDashboard.collectAsStateWithLifecycle()
    val localities by viewModel.adminAnalyticsLocalities.collectAsStateWithLifecycle()
    val accountProfile by viewModel.adminAnalyticsAccountProfile.collectAsStateWithLifecycle()
    val auditEvents by viewModel.adminAnalyticsAuditEvents.collectAsStateWithLifecycle()
    val analyticsUi by viewModel.adminAnalyticsUi.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(AdminAnalyticsPeriod.LAST_30_DAYS) }
    var emailQuery by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf(AdminAccountLookupPurpose.ACCOUNT_SUPPORT) }
    var explanation by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(period) { viewModel.refreshAdminPrivacyAnalytics(period) }
    RtcScreenScaffold(density = RtcContentDensity.ANALYTICAL_DENSE) {
        item { RtcSectionHeader("Audit & Privacy Analytics", "Privacy-suppressed operational reporting with purpose-controlled exact-account lookup and immutable audit history.") }
        item { RtcProtectedAreaBanner("System Administrator-only, view-focused analytics. This workspace does not process live device location, device identifiers, Community/support text, or unrestricted raw-data exports.") }
        analyticsUi.message?.let { message -> item { CommunityActionFeedback(message = message, isError = !analyticsUi.isSuccess, onDismiss = viewModel::dismissAdminPrivacyAnalyticsMessage) } }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("Reporting period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        AdminAnalyticsPeriod.entries.forEach { option -> FilterChip(selected = period == option, onClick = { period = option }, enabled = !analyticsUi.isWorking, label = { Text(option.label) }) }
                    }
                    Text("Active accounts are privacy-minimised daily app-active signals. Directory counts record completed searches only, never the search terms.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Operational measures · ${dashboard.period.label}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                AdminAnalyticsMetricCard("Verified sign-ups", dashboard.value("VERIFIED_SIGNUPS")); AdminAnalyticsMetricCard("Active accounts", dashboard.value("ACTIVE_ACCOUNT_SIGNALS")); AdminAnalyticsMetricCard("Community posts", dashboard.value("COMMUNITY_POSTS")); AdminAnalyticsMetricCard("Community comments", dashboard.value("COMMUNITY_COMMENTS")); AdminAnalyticsMetricCard("Support requests", dashboard.value("SUPPORT_REQUESTS")); AdminAnalyticsMetricCard("Directory searches", dashboard.value("DIRECTORY_SEARCHES")); AdminAnalyticsMetricCard("Notifications created", dashboard.value("NOTIFICATIONS_SENT")); AdminAnalyticsMetricCard("Notifications delivered", dashboard.value("NOTIFICATIONS_DELIVERED")); AdminAnalyticsMetricCard("Notifications read", dashboard.value("NOTIFICATIONS_READ")); AdminAnalyticsMetricCard("Role changes", dashboard.value("ROLE_CHANGES"))
            }
        }
        item { Text("Locality summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Text("Suppression applied", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Only groups with at least five accounts or support requests appear by locality. Smaller groups are combined as “Other / insufficient data”. Declared profile locality is optional resident-provided text; support locality comes only from voluntarily supplied support-request labels. It is distinct from any future Nearby search area or live device-location feature.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (localities.isEmpty()) Text("No locality aggregates are available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else localities.forEach { locality -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text(locality.localityLabel, fontWeight = FontWeight.SemiBold); Text(locality.sourceLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(locality.metricValue.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
                }
            }
        }
        item { Text("Purpose-controlled account lookup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    Text("Exact verified email only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("This returns a minimal operational profile. It excludes passwords, authentication secrets, private uploads, device identifiers, Community text, and support content.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = emailQuery, onValueChange = { emailQuery = it }, label = { Text("Complete verified account email") }, singleLine = true, enabled = !analyticsUi.isWorking, modifier = Modifier.fillMaxWidth())
                    Text("Permitted purpose", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { AdminAccountLookupPurpose.entries.forEach { option -> FilterChip(selected = purpose == option, onClick = { purpose = option }, enabled = !analyticsUi.isWorking, label = { Text(option.label) }) } }
                    OutlinedTextField(value = explanation, onValueChange = { explanation = it.take(500) }, label = { Text("Short explanation") }, supportingText = { Text("Required for the immutable lookup record · ${explanation.trim().length}/500") }, minLines = 2, maxLines = 4, enabled = !analyticsUi.isWorking, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.lookupAdminPrivacyAccount(emailQuery, purpose, explanation) }, enabled = !analyticsUi.isWorking && emailQuery.trim().contains('@') && explanation.trim().length in 3..500, modifier = Modifier.fillMaxWidth()) { Text(if (analyticsUi.isWorking) "Checking…" else "Record purpose and view account") }
                }
            }
        }
        accountProfile?.let { profile ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(RtcSpacing.compact)); Column(modifier = Modifier.weight(1f)) { Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(enabled = !analyticsUi.isWorking, onClick = viewModel::clearAdminPrivacyAccountLookup) { Text("Clear") } }
                        Text("Role: ${profile.effectiveRole?.replace('_', ' ') ?: "Resident"}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("Signed up ${relativeTimeLabel(profile.signedUpAt)} · Confirmed ${profile.emailConfirmedAt?.let(::relativeTimeLabel) ?: "not available"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Declared locality: ${profile.declaredLocality ?: "Not provided"}", style = MaterialTheme.typography.bodySmall)
                        Text("Last active signal: ${profile.lastActiveAt?.let(::relativeTimeLabel) ?: "No activity signal in the retained window"}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Text("Last 24 months: ${profile.communityPostsLast24Months} posts · ${profile.communityCommentsLast24Months} comments · ${profile.supportRequestsLast24Months} support requests · ${profile.directorySearchesLast24Months} directory searches", style = MaterialTheme.typography.bodySmall)
                        Text("Notifications: Support ${if (profile.supportNotifications) "on" else "off"} · Community ${if (profile.communityNotifications) "on" else "off"}", style = MaterialTheme.typography.bodySmall)
                        Text("Access-management history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (profile.accessManagementHistory.isEmpty()) Text("No recorded role-management events for this account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else profile.accessManagementHistory.forEach { event -> Text("${event.eventType.replace('_', ' ')} · ${relativeTimeLabel(event.occurredAt)} · ${event.reason}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item { Text("Combined immutable audit trail", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (auditEvents.isEmpty()) item { DirectoryEmptyState("Protected views, lookup requests, and role-management events will appear here after the first action.") }
        else items(auditEvents, key = { it.id }) { event -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(event.eventType.replace('_', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); event.targetEmail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }; event.details?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("By ${event.actorEmail} · ${relativeTimeLabel(event.occurredAt)} · ${event.source ?: "system"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

@Composable
private fun AdminAnalyticsMetricCard(label: String, value: Long) {
    Card(modifier = Modifier.width(RtcSize.adaptiveCardMinWidth)) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessRoleAssignmentCard(account: AccessManagedAccount, selectedRoleWire: String, reason: String, isWorking: Boolean, onRoleSelect: (String) -> Unit, onReasonChange: (String) -> Unit, onSave: () -> Unit, onClear: () -> Unit) {
    val currentRole = ManagedAccessRole.fromWire(account.effectiveRole)
    val selectedRole = ManagedAccessRole.fromWire(selectedRoleWire) ?: ManagedAccessRole.RESIDENT
    val administratorChange = currentRole == ManagedAccessRole.SYSTEM_ADMIN || selectedRole == ManagedAccessRole.SYSTEM_ADMIN
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(RtcSpacing.compact)); Column(modifier = Modifier.weight(1f)) { Text(account.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(account.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Current role: ${currentRole?.label ?: "No managed role"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }; TextButton(onClick = onClear, enabled = !isWorking) { Text("Clear") } }
            Text("Assign one role", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { ManagedAccessRole.entries.forEach { role -> FilterChip(selected = selectedRole == role, onClick = { onRoleSelect(role.wireValue) }, enabled = !isWorking, label = { Text(role.label) }) } }
            OutlinedTextField(value = reason, onValueChange = onReasonChange, label = { Text("Reason for change") }, supportingText = { Text("Required for a staff or Administrator access decision · ${reason.trim().length}/500") }, enabled = !isWorking, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            if (administratorChange) Text("This Administrator change will be saved as a pending request. The other System Administrator must approve it before the role is applied.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = onSave, enabled = !isWorking && currentRole?.wireValue != selectedRole.wireValue && reason.trim().length in 3..500, modifier = Modifier.fillMaxWidth()) { Text(if (isWorking) "Saving…" else if (administratorChange) "Request Administrator change" else "Save role change") }
        }
    }
}

@Composable
private fun AccessRoleChangeRequestCard(request: AccessRoleChangeRequest, isWorking: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) { Text(request.targetEmail, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${ManagedAccessRole.fromWire(request.previousRole)?.label ?: "No managed role"} → ${ManagedAccessRole.fromWire(request.requestedRole)?.label ?: request.requestedRole}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold); Text("Requested by ${request.requestedByEmail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Reason: ${request.reason}", style = MaterialTheme.typography.bodyMedium); Text("Requested ${relativeTimeLabel(request.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), modifier = Modifier.fillMaxWidth()) { OutlinedButton(onClick = onReject, enabled = !isWorking, modifier = Modifier.weight(1f)) { Text("Reject") }; Button(onClick = onApprove, enabled = !isWorking, modifier = Modifier.weight(1f)) { Text("Approve") } } } }
}

@Composable
private fun AccessRoleAuditEventCard(event: AccessRoleAuditEvent) {
    Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) { Text(event.targetEmail, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text("${event.eventType.replace('_', ' ')} · ${ManagedAccessRole.fromWire(event.previousRole)?.label ?: "—"} → ${ManagedAccessRole.fromWire(event.newRole)?.label ?: "—"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary); Text("By ${event.actorEmail} · ${relativeTimeLabel(event.occurredAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(event.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
internal fun WorkQueueCard(item: za.org.rtc.community.core.WorkQueueItem) { RtcWorkQueueCard(item.title, item.description, item.priority) }

@Composable
internal fun OperationalControlsCard() {
    var reviewOpen by rememberSaveable { mutableStateOf(false) }
    RtcCard(protected = true) { RtcProtectedAreaBanner("Operational changes require impact review, typed confirmation, expiry where applicable, and an immutable audit result."); OutlinedButton(onClick = { reviewOpen = true }) { Text("Review operational controls") } }
    if (reviewOpen) OperationalControlReviewDialog(onDismiss = { reviewOpen = false })
}

@Composable
private fun OperationalControlReviewDialog(onDismiss: () -> Unit) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    var auditNote by rememberSaveable { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("Operational control safeguard") }, text = { Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) { Text("A production action will show its affected records and impact preview here. No live configuration change is available from this development adapter."); OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type CONFIRM to continue") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(auditNote, { auditNote = it }, label = { Text("Required audit note") }, modifier = Modifier.fillMaxWidth(), minLines = 2) } }, confirmButton = { Button(onClick = onDismiss, enabled = confirmation == "CONFIRM" && auditNote.trim().length >= 3) { Text("Acknowledge safeguard") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

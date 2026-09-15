package za.org.rtc.community.feature.account

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.BuildConfig
import za.org.rtc.community.app.PasswordUiState
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.feature.community.CommunityActionFeedback
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeclaredLocalitySheet(
    initialLocality: String?,
    localityUi: za.org.rtc.community.app.DeclaredLocalityUiState,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var locality by rememberSaveable(initialLocality) { mutableStateOf(initialLocality.orEmpty()) }
    ModalBottomSheet(onDismissRequest = { if (!localityUi.isWorking) onDismiss() }) {
        Column(
            modifier = Modifier.padding(start = RtcSpacing.standard, end = RtcSpacing.standard, top = RtcSpacing.compact),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
        ) {
            Text("Declared locality", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Optional resident-provided text used only in privacy-suppressed aggregate service reporting. It is not a live location, does not grant location permission, and remains separate from any future Nearby search area.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = locality,
                onValueChange = { locality = it.take(120) },
                label = { Text("Locality or service area") },
                supportingText = { Text("Leave blank to remove it · ${locality.trim().length}/120") },
                enabled = !localityUi.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            localityUi.message?.let { message ->
                Text(message, color = if (localityUi.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onSave(null) }, enabled = !localityUi.isWorking && initialLocality != null, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(onClick = { onSave(locality) }, enabled = !localityUi.isWorking && (locality.trim().isEmpty() || locality.trim().length in 2..120), modifier = Modifier.weight(1f)) { Text(if (localityUi.isWorking) "Saving…" else "Save") }
            }
            TextButton(onClick = onDismiss, enabled = !localityUi.isWorking, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

@Composable
internal fun PasswordUpdateDialog(
    isRecoveryFlow: Boolean,
    passwordUi: PasswordUiState,
    onUpdate: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isCurrentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isNewPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val passwordsMatch = newPassword.isNotBlank() && newPassword == confirmPassword
    AlertDialog(
        onDismissRequest = { if (!passwordUi.isWorking) onDismiss() },
        title = { Text(if (isRecoveryFlow) "Choose a new password" else "Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text(if (isRecoveryFlow) "Your recovery link has been verified. Choose a new password for this account." else "Enter your current password and choose a new password.")
                if (!isRecoveryFlow) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (isCurrentPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (isCurrentPasswordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (isNewPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (isNewPasswordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Use at least 12 characters, including lower- and upper-case letters, a number, and a symbol.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (confirmPassword.isNotEmpty() && !passwordsMatch) Text("The new passwords do not match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                passwordUi.message?.let { Text(it, color = if (passwordUi.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = !passwordUi.isWorking && (passwordUi.isSuccess || (passwordsMatch && (isRecoveryFlow || currentPassword.isNotBlank()))),
                onClick = { if (passwordUi.isSuccess) onDismiss() else onUpdate(newPassword, if (isRecoveryFlow) null else currentPassword) }
            ) { Text(if (passwordUi.isWorking) "Please wait" else if (passwordUi.isSuccess) "Done" else "Update password") }
        },
        dismissButton = { TextButton(enabled = !passwordUi.isWorking, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun AccountSectionTitle(text: String) { RtcSectionHeader(text) }

@Composable
internal fun AccountAction(title: String, description: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    RtcCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PrivacyDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackSheet(viewModel: RtcViewModel, onDismiss: () -> Unit) {
    val feedbackUi by viewModel.feedbackUi.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> screenshotUri = uri }
    ModalBottomSheet(onDismissRequest = { if (!feedbackUi.isWorking) onDismiss() }, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Send feedback", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Your report is stored with app version ${BuildConfig.VERSION_NAME} and ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}. A selected screenshot is uploaded privately and visible only through authorised feedback triage.")
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("What can we improve?") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            OutlinedButton(onClick = { screenshotPicker.launch("image/*") }, enabled = !feedbackUi.isWorking, modifier = Modifier.fillMaxWidth()) { Text(if (screenshotUri == null) "Add optional screenshot" else "Screenshot selected · change") }
            screenshotUri?.let { uri ->
                Text(uri.lastPathSegment ?: "Screenshot selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { screenshotUri = null }, enabled = !feedbackUi.isWorking) { Text("Remove screenshot") }
            }
            feedbackUi.message?.let { message -> CommunityActionFeedback(message, isError = !feedbackUi.isSuccess, onDismiss = viewModel::dismissFeedbackMessage) }
            Button(onClick = { viewModel.submitFeedback(text, screenshotUri) }, enabled = !feedbackUi.isWorking && text.trim().length >= 3, modifier = Modifier.fillMaxWidth()) { Text(if (feedbackUi.isWorking) "Submitting…" else "Submit feedback") }
            if (feedbackUi.isSuccess) OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            Spacer(Modifier.height(RtcSpacing.small))
        }
    }
}

@Composable
internal fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text("Sign Out")
        },
        text = {
            Text("Are you sure you want to sign out of your account on this device? You can sign back in at any time.")
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Sign Out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

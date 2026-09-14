package za.org.rtc.community.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.animation.RtcMotionAlertDialog

@Suppress("UNUSED_PARAMETER")
@Composable
fun AuthenticationErrorDialog(
    errorType: String, // "NETWORK", "CONFIGURATION", "NO_ACCOUNTS", "UNKNOWN", "PLAY_SERVICES_MISSING"
    errorMessageText: String,
    rawExceptionMessage: String,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
) {
    val safeMessage = when (errorType) {
        "NETWORK" -> "Google sign-in could not reach the service. Check your connection and try again."
        "CONFIGURATION" -> "Google sign-in is temporarily unavailable. Please use email sign-in or try again later."
        "NO_ACCOUNTS" -> "No Google account is available on this device. Add an account or use email sign-in."
        "PLAY_SERVICES_MISSING" -> "Google Play services needs attention before Google sign-in can continue."
        else -> "Google sign-in could not be completed. Please try again."
    }

    RtcMotionAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (errorType) {
                        "NETWORK" -> Icons.Filled.NetworkCheck
                        "CONFIGURATION", "PLAY_SERVICES_MISSING" -> Icons.Filled.SettingsSuggest
                        "NO_ACCOUNTS" -> Icons.Filled.WarningAmber
                        else -> Icons.Filled.ErrorOutline
                    },
                    contentDescription = null,
                    tint = when (errorType) {
                        "CONFIGURATION", "PLAY_SERVICES_MISSING" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = when (errorType) {
                        "NETWORK" -> "Connection issue"
                        "CONFIGURATION" -> "Google sign-in unavailable"
                        "NO_ACCOUNTS" -> "No Google accounts"
                        "PLAY_SERVICES_MISSING" -> "Google Play services"
                        else -> "Sign-in couldn’t finish"
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = safeMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismissRequest()
                    onRetry()
                },
            ) {
                Text("Try again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = MaterialTheme.colorScheme.outline)
            }
        },
    )
}

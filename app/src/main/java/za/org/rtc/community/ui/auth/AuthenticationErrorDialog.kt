package za.org.rtc.community.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.animation.RtcMotionAlertDialog

@Composable
fun AuthenticationErrorDialog(
    errorType: String, // "NETWORK", "CONFIGURATION", "NO_ACCOUNTS", "UNKNOWN", "PLAY_SERVICES_MISSING"
    errorMessageText: String,
    rawExceptionMessage: String,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
) {
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
                        "NETWORK" -> MaterialTheme.colorScheme.error
                        "CONFIGURATION", "PLAY_SERVICES_MISSING" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = when (errorType) {
                        "NETWORK" -> "Network Connection Issue"
                        "CONFIGURATION" -> "Google API Config Error"
                        "NO_ACCOUNTS" -> "No Google Accounts"
                        "PLAY_SERVICES_MISSING" -> "Google Play Services"
                        else -> "Sign-In Failed"
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = errorMessageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                
                // Collapse/Expand Technical Logs
                if (rawExceptionMessage.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Technical Logs:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = rawExceptionMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismissRequest()
                    onRetry()
                },
            ) {
                Text(if (errorType == "CONFIGURATION") "Re-configure" else "Retry Flow")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text("Close", color = MaterialTheme.colorScheme.outline)
            }
        }
    )
}


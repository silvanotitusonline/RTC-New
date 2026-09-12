package za.org.rtc.community.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ResidentCapabilityNoticeScreen(
    title: String,
    message: String,
    actionLabel: String = "Back",
    onAction: () -> Unit,
) {
    RtcScreenScaffold {
        item { RtcSectionHeader(title, message) }
        item {
            RtcCard {
                Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(
                        "RTC is running without resident accounts. This feature will only become writable when its server contract supports anonymous use without weakening moderation, ownership or abuse controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

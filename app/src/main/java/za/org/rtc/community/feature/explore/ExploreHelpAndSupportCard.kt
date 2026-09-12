package za.org.rtc.community.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ExploreHelpAndSupportCard(
    onOpenFaq: () -> Unit,
    onContactSupport: () -> Unit,
) {
    RtcCard {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Help and support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Find answers or contact the support team when you need a hand.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenFaq) {
                Text("Frequently asked questions", fontWeight = FontWeight.SemiBold)
                Text("Browse guidance for accounts, Community, services, and safety.", style = MaterialTheme.typography.bodySmall)
            }
            RtcCard(modifier = Modifier.fillMaxWidth(), onClick = onContactSupport) {
                Text("Contact support", fontWeight = FontWeight.SemiBold)
                Text("Send a private support request and follow its responses in Inbox.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

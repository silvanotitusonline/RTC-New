package za.org.rtc.community.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.ui.theme.RtcCivicGold
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun PendingSyncIndicator(count: Int, message: String, onClick: () -> Unit = {}) {
    RtcCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Pending sync · $count", fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmergencyNotice() {
    RtcEmergencyBanner(
        title = "Emergency?",
        message = "For immediate danger, contact local emergency services. RTC Support is a non-emergency request channel.",
    )
}

@Composable
fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    RtcSectionHeader(title = title, trailing = { TextButton(onClick = onAction) { Text(action) } })
}

@Composable
fun SupportCaseCard(item: SupportCase, onClick: (() -> Unit)? = null) {
    RtcCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(item.category.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RtcStatusChip(item.stage.label, if (item.actionRequired) RtcStatusTone.PROTECTED else RtcStatusTone.SUCCESS)
        }
        RtcCaseProgress(item.stage)
        Text(item.stage.nextStep, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.updatedAt, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.actionRequired) Text("Action needed from you", color = RtcCivicGold, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NoticeCard(notice: OfficialNotice, onClick: () -> Unit = {}) {
    RtcOfficialUpdateCard(title = notice.title, summary = notice.summary, onClick = onClick)
}

@Composable
fun DirectoryEmptyState(message: String) {
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = RtcSpacing.standard))
}

@Composable
fun PurposefulEmptyState(message: String, action: String, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RtcSize.heroIcon))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

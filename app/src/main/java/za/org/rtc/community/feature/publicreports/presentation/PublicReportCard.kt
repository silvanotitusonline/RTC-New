package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportIdentityMode
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun PublicReportCard(
    report: PublicReport,
    onOpen: () -> Unit,
    onVote: (Int) -> Unit,
) {
    val author = if (report.identityMode == PublicReportIdentityMode.ANONYMOUS) {
        "Anonymous community member"
    } else {
        report.authorDisplayName
    }
    RtcCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
            modifier = Modifier.clickable(onClick = onOpen).semantics {
                contentDescription = "${report.title}. ${report.urgency.label} urgency. ${report.status.label}."
            },
        ) {
            Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                AssistChip(onClick = {}, label = { Text(report.urgency.label) })
                AssistChip(onClick = {}, label = { Text(report.status.label) })
                if (report.verified) AssistChip(onClick = {}, label = { Text("Verified") })
            }
            if (report.categoryLabel.isNotBlank()) {
                Text(report.categoryLabel, style = MaterialTheme.typography.labelLarge)
            }
            if (report.publicLocationLabel.isNotBlank()) {
                Text(report.publicLocationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (report.description.isNotBlank()) {
                Text(report.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text("$author · ${report.createdAt}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Evidence ${report.evidenceCount}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Evidence count ${report.evidenceCount}"
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                TextButton(onClick = { onVote(1) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(
                        Icons.Outlined.ThumbUp,
                        contentDescription = "Thumbs up",
                        tint = if (report.currentUserVote == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(report.thumbsUpCount.toString())
                }
                TextButton(onClick = { onVote(-1) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(
                        Icons.Outlined.ThumbDown,
                        contentDescription = "Thumbs down",
                        tint = if (report.currentUserVote == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(report.thumbsDownCount.toString())
                }
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments")
                    Text(report.commentCount.toString())
                }
            }
        }
    }
}

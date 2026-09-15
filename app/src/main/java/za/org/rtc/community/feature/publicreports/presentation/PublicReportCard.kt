package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.TimeFormatters
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
    isAdmin: Boolean = false,
    onAdminVerify: ((Boolean) -> Unit)? = null,
    onAdminReject: (() -> Unit)? = null,
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
            // Admin Verification Status Header
            if (isAdmin) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (report.verified) {
                        Color(0xFF059669).copy(alpha = 0.12f)
                    } else {
                        Color(0xFFD97706).copy(alpha = 0.15f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (report.verified) Icons.Filled.Verified else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = if (report.verified) Color(0xFF059669) else Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (report.verified) "Verified · Visible to Public" else "Pending Admin Verification · Hidden from Public",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (report.verified) Color(0xFF059669) else Color(0xFFB45309)
                            )
                        }
                    }
                }
            }

            Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                AssistChip(onClick = {}, label = { Text(report.urgency.label) })
                AssistChip(onClick = {}, label = { Text(report.status.label) })
                if (report.verified) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Verified") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("Unverified") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
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

            Text(
                "$author · ${TimeFormatters.formatRelativeTime(report.createdAt)}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Evidence ${report.evidenceCount}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    contentDescription = "Evidence count ${report.evidenceCount}"
                },
            )

            // Admin Review Action Bar for Unverified or Modifiable Reports
            if (isAdmin && onAdminVerify != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "ADMINISTRATOR VERIFICATION ACTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!report.verified) {
                        Button(
                            onClick = { onAdminVerify(true) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve & Verify")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAdminVerify(false) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        ) {
                            Text("Revoke Verification")
                        }
                    }

                    if (onAdminReject != null && !report.verified) {
                        OutlinedButton(
                            onClick = onAdminReject,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reject Report")
                        }
                    }
                }
            }

            // Public Voter & Comments Bar
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


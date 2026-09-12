package za.org.rtc.community.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

data class HomeCommunityMetric(
    val label: String,
    val value: Long,
    val scope: PublicReportScope,
)

@Composable
fun HomeCommunityStatusCard(
    state: HomePublicReportState,
    onOpenScope: (PublicReportScope) -> Unit,
    onRetry: () -> Unit,
) {
    val dashboard = state.dashboard
    val metrics = if (dashboard != null) {
        listOf(
            HomeCommunityMetric("Verified", dashboard.verifiedReports, PublicReportScope.VERIFIED),
            HomeCommunityMetric("Active", dashboard.activeReports, PublicReportScope.ACTIVE),
            HomeCommunityMetric("Resolved", dashboard.resolvedReports, PublicReportScope.RESOLVED),
            HomeCommunityMetric("Unresolved", dashboard.unresolvedReports, PublicReportScope.UNRESOLVED),
        )
    } else emptyList()

    if (dashboard == null && state.loading) {
        RtcCard {
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                CircularProgressIndicator()
                Text("Loading public reports summary…")
            }
        }
    } else if (dashboard == null) {
        RtcCard {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text(state.message ?: "The public reports snapshot is unavailable.")
                TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Try again")
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Community snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Verified Public Reports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            za.org.rtc.community.feature.publicreports.presentation.CommunitySnapshotDonutSummary(
                dashboard = dashboard,
                onScopeSelected = { scope ->
                    metrics.firstOrNull { it.scope == scope }?.let { metric ->
                        onOpenScope(metric.scope)
                    } ?: onOpenScope(scope)
                },
            )
        }
    }
}

@Composable
fun HomePostComposerCard(
    onCommunityPost: (String) -> Unit,
    onPublicReport: (String) -> Unit,
) {
    var body by rememberSaveable { mutableStateOf("") }
    RtcCard {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
            Text("Share with community", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(2_000) },
                label = { Text("Write something…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Button(
                    onClick = { onCommunityPost(body.trim()); body = "" },
                    enabled = body.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Community Post") }
                OutlinedButton(
                    onClick = { onPublicReport(body.trim()); body = "" },
                    enabled = body.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Public Report") }
            }
            Text(
                "Opens composer before publishing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

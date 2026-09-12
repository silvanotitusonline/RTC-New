package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.components.RtcStatusChip
import za.org.rtc.community.ui.components.RtcStatusTone
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * Moderation triage summary for the admin landing surface.
 *
 * Counts come from [AdminDashboardViewModel], which aggregates the pending
 * moderation, business-submission and support-request queues under the MFA
 * guarded admin path.
 */
@Composable
fun AdminOverviewScreen(
    viewModel: AdminDashboardViewModel = hiltViewModel(),
    composerViewModel: NotificationComposerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startRealtimePolling()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(RtcSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
    ) {
        RtcSectionHeader(
            title = "Pending work",
            subtitle = "Live queue depth across moderation, submissions and support.",
        )

        if (!state.isAuthorized) {
            RtcCard {
                Text(
                    text = "Administrator access required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Complete administrator MFA verification to view moderation queues.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.errorMessage?.let { message ->
            RtcCard {
                Text(
                    text = "Queues unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OverviewRow("Reported content", state.reportsCount, RtcStatusTone.DANGER)
        OverviewRow("Business submissions", state.businessSubmissionsCount, RtcStatusTone.NEUTRAL)
        OverviewRow("Support requests", state.supportRequestsCount, RtcStatusTone.PROTECTED)
        OverviewRow("Total pending tasks", state.totalPendingTasks, RtcStatusTone.SUCCESS)

        Button(
            onClick = { viewModel.refreshCounts() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh counts")
        }

        NotificationComposerCard(viewModel = composerViewModel)
    }
}

@Composable
private fun OverviewRow(label: String, count: Long, tone: RtcStatusTone) {
    RtcCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            RtcStatusChip(text = count.toString(), tone = tone)
        }
    }
}

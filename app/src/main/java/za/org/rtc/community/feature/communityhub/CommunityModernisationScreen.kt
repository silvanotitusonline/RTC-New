package za.org.rtc.community.feature.communityhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import za.org.rtc.community.navigation.CommunitySection
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun CommunityModernisationScreen(
    initialSection: CommunitySection = CommunitySection.DISCUSSIONS,
    discussions: @Composable (onNavigateToReports: () -> Unit) -> Unit,
    reports: @Composable () -> Unit,
) {
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    val onNavigateToReports = remember { { section = CommunitySection.REPORTS } }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RtcSpacing.standard, vertical = RtcSpacing.compact),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                FilterChip(
                    selected = section == CommunitySection.DISCUSSIONS,
                    onClick = { section = CommunitySection.DISCUSSIONS },
                    label = { Text("Community Feed") },
                )
                FilterChip(
                    selected = section == CommunitySection.REPORTS,
                    onClick = { section = CommunitySection.REPORTS },
                    label = { Text("Public Reports") },
                )
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            when (section) {
                CommunitySection.DISCUSSIONS -> discussions(onNavigateToReports)
                CommunitySection.REPORTS -> reports()
            }
        }
    }
}

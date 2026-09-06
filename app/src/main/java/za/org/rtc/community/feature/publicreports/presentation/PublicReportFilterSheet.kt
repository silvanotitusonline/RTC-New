package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.community.feature.publicreports.domain.PublicReportSort
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicReportFilterSheet(
    state: PublicReportsFeedState,
    onEvent: PublicReportViewModel,
) {
    RtcCard {
        Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText), modifier = Modifier.fillMaxWidth()) {
            Text("Filters")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                PublicReportScope.entries.forEach { scope ->
                    FilterChip(
                        selected = state.filters.effectiveScope == scope,
                        onClick = { onEvent.setScope(scope) },
                        label = { Text(scope.label) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                listOf(null, PublicReportUrgency.LOW, PublicReportUrgency.NORMAL, PublicReportUrgency.HIGH, PublicReportUrgency.CRITICAL).forEach { urgency ->
                    FilterChip(
                        selected = state.filters.urgency == urgency,
                        onClick = { onEvent.setUrgency(urgency) },
                        label = { Text(urgency?.label ?: "Any urgency") },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                PublicReportSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.filters.sort == sort,
                        onClick = { onEvent.setSort(sort) },
                        label = { Text(sort.label) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText), modifier = Modifier.padding(top = RtcSpacing.relatedText)) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.filters.categorySlug == category.slug,
                        onClick = {
                            onEvent.setCategory(if (state.filters.categorySlug == category.slug) null else category.slug)
                        },
                        label = { Text(category.label) },
                    )
                }
            }
            TextButton(onClick = onEvent::clearFilters, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear all") }
        }
    }
}

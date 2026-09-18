package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.community.feature.publicreports.domain.PublicReportSort
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.domain.PublicReportUrgency
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicReportFilterSheet(
    state: PublicReportsFeedState,
    onEvent: PublicReportViewModel,
) {
    var open by remember { mutableStateOf(false) }
    val activeCount = listOf(
        state.filters.effectiveScope != PublicReportScope.VERIFIED,
        state.filters.urgency != null,
        state.filters.categorySlug != null,
        state.filters.sort != PublicReportSort.LATEST,
    ).count { it }

    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.Filled.FilterList, contentDescription = null)
        Text(if (activeCount == 0) "Filter reports" else "Filter reports · $activeCount active")
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            RtcCard(modifier = Modifier.padding(horizontal = RtcSpacing.standard)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard),
                ) {
                    Text("Filter reports")
                    Text("Scope", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        items(PublicReportScope.entries) { scope ->
                            FilterChip(
                                selected = state.filters.effectiveScope == scope,
                                onClick = { onEvent.setScope(scope) },
                                label = { Text(scope.label) },
                            )
                        }
                    }
                    Text("Urgency", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        items(listOf(null, PublicReportUrgency.LOW, PublicReportUrgency.NORMAL, PublicReportUrgency.HIGH, PublicReportUrgency.CRITICAL)) { urgency ->
                            FilterChip(
                                selected = state.filters.urgency == urgency,
                                onClick = { onEvent.setUrgency(urgency) },
                                label = { Text(urgency?.label ?: "Any") },
                            )
                        }
                    }
                    Text("Sort", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        items(PublicReportSort.entries) { sort ->
                            FilterChip(
                                selected = state.filters.sort == sort,
                                onClick = { onEvent.setSort(sort) },
                                label = { Text(sort.label) },
                            )
                        }
                    }
                    Text("Categories", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        items(state.categories) { category ->
                            FilterChip(
                                selected = state.filters.categorySlug == category.slug,
                                onClick = {
                                    onEvent.setCategory(if (state.filters.categorySlug == category.slug) null else category.slug)
                                },
                                label = { Text(category.label) },
                            )
                        }
                    }
                    TextButton(
                        onClick = { onEvent.clearFilters(); open = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Clear all") }
                }
            }
        }
    }
}

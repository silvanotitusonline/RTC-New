package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.publicreports.domain.label
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.core.MediaTargetType
import za.org.rtc.community.core.TimeFormatters
import za.org.rtc.community.feature.community.FullScreenMediaGallery
import za.org.rtc.community.feature.publicreports.domain.PublicReportMediaKind
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun PublicReportDetailScreen(
    reportId: String,
    viewModel: PublicReportViewModel = hiltViewModel(),
) {
    val state = viewModel.detail.collectAsStateWithLifecycle().value
    LaunchedEffect(reportId) { viewModel.openReport(reportId) }
    val report = state.report

    RtcScreenScaffold {
        item { RtcSectionHeader(title = report?.title ?: "Public Report", subtitle = report?.status?.label) }
        if (state.loading) item { Text("Loading report…") }
        if (!state.loading && report == null) {
            item {
                RtcCard {
                    Text("This Public Report could not be opened.")
                    TextButton(onClick = { viewModel.openReport(reportId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                }
            }
        }
        report?.let { reportItem ->
            item {
                RtcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("${reportItem.urgency.label} · ${reportItem.status.label}${if (reportItem.verified) " · Verified" else ""}")
                        Text(reportItem.description)
                        Text(reportItem.publicLocationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${reportItem.authorDisplayName} · ${TimeFormatters.formatRelativeTime(reportItem.createdAt)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { viewModel.vote(1) }, modifier = Modifier.heightIn(min = 48.dp).weight(1f)) {
                                Text("Support ${reportItem.thumbsUpCount}")
                            }
                            Button(onClick = { viewModel.vote(-1) }, modifier = Modifier.heightIn(min = 48.dp).weight(1f)) {
                                Text("Oppose ${reportItem.thumbsDownCount}")
                            }
                        }
                        TextButton(onClick = { viewModel.vote(0) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear vote") }
                    }
                }
            }
            item {
                RtcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("Evidence", fontWeight = FontWeight.SemiBold)
                        if (state.evidence.isEmpty()) {
                            Text("No public evidence is attached to this report.")
                        } else {
                            state.evidence.forEach { evidence ->
                                TextButton(
                                    onClick = { viewModel.openEvidence(evidence.id) },
                                    enabled = !state.evidenceOpening,
                                    modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().semantics {
                                        contentDescription = "Open ${evidence.mediaKind.name.lowercase()} evidence ${evidence.position}"
                                    },
                                ) {
                                    Text(
                                        if (evidence.signedUrl.isNullOrBlank()) {
                                            "Open ${evidence.mediaKind.name.lowercase()} ${evidence.position}"
                                        } else {
                                            "Reload ${evidence.mediaKind.name.lowercase()} ${evidence.position}"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                RtcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("Status timeline", fontWeight = FontWeight.SemiBold)
                        if (state.timeline.isEmpty()) Text("No public timeline yet.")
                        state.timeline.forEach { entry ->
                            Text("${entry.toStatus.label}${entry.publicNote?.let { " · $it" } ?: ""}")
                        }
                    }
                }
            }
            item {
                RtcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text("Comments", fontWeight = FontWeight.SemiBold)
                        if (state.comments.isEmpty()) Text("No comments yet.")
                        state.comments.forEach { comment ->
                            Text("${comment.authorDisplayName}: ${comment.body}", modifier = Modifier.semantics {
                                contentDescription = "Comment by ${comment.authorDisplayName}: ${comment.body}"
                            })
                        }
                        if (!state.commentsEndReached) {
                            TextButton(onClick = viewModel::loadMoreComments, modifier = Modifier.heightIn(min = 48.dp)) { Text("More comments") }
                        }
                        OutlinedTextField(
                            value = state.commentDraft,
                            onValueChange = viewModel::updateCommentDraft,
                            label = { Text("Add a comment") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = viewModel::submitComment, modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth()) {
                            Text("Post comment")
                        }
                    }
                }
            }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    }

    val selectedEvidence = state.evidence.firstOrNull { it.id == state.selectedEvidenceId }
    if (selectedEvidence?.signedUrl != null) {
        FullScreenMediaGallery(
            media = listOf(
                MediaItem(
                    id = selectedEvidence.id,
                    targetType = MediaTargetType.PUBLIC_REPORT,
                    targetId = selectedEvidence.reportId,
                    storagePath = "",
                    kind = if (selectedEvidence.mediaKind == PublicReportMediaKind.VIDEO) MediaKind.VIDEO else MediaKind.IMAGE,
                    mimeType = selectedEvidence.mimeType,
                    byteSize = selectedEvidence.byteSize,
                    width = selectedEvidence.width,
                    height = selectedEvidence.height,
                    durationSeconds = selectedEvidence.durationSeconds,
                    position = selectedEvidence.position,
                    signedUrl = selectedEvidence.signedUrl,
                ),
            ),
            onRefreshMediaUrl = viewModel::refreshEvidenceUrl,
            onDismiss = viewModel::dismissEvidence,
        )
    }
}

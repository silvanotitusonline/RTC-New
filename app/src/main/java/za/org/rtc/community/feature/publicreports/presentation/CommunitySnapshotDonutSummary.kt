package za.org.rtc.community.feature.publicreports.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import za.org.rtc.community.feature.publicreports.domain.PublicReport
import za.org.rtc.community.feature.publicreports.domain.PublicReportDashboard
import za.org.rtc.community.feature.publicreports.domain.PublicReportScope
import za.org.rtc.community.feature.publicreports.domain.PublicReportStatus
import za.org.rtc.community.ui.components.RtcCard

val DonutOpenColor = Color(0xFFF59E0B)       // Bright Orange / Amber
val DonutInProgressColor = Color(0xFF2563EB) // Vivid Blue
val DonutResolvedColor = Color(0xFF22C55E)   // Fresh Green

@Composable
fun CommunitySnapshotDonutSummary(
    dashboard: PublicReportDashboard?,
    reports: List<PublicReport> = emptyList(),
    onScopeSelected: ((PublicReportScope) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Dynamically calculate snapshot values from dashboard or public report dataset without hardcoded fallbacks
    val openCount = dashboard?.openReports
        ?: reports.count { it.status == PublicReportStatus.SUBMITTED || it.status == PublicReportStatus.ACKNOWLEDGED }.toLong()

    val inProgressCount = dashboard?.inProgressReports
        ?: reports.count { it.status == PublicReportStatus.IN_PROGRESS }.toLong()

    val resolvedCount = dashboard?.resolvedReports
        ?: reports.count { it.status == PublicReportStatus.COMPLETED || it.status == PublicReportStatus.CLOSED }.toLong()

    val totalCount = dashboard?.totalReports?.takeIf { it > 0 }
        ?: (openCount + inProgressCount + resolvedCount).takeIf { it > 0 }
        ?: reports.size.toLong()

    val openPct = if (totalCount > 0) ((openCount * 100f) / totalCount).toInt() else 0
    val inProgressPct = if (totalCount > 0) ((inProgressCount * 100f) / totalCount).toInt() else 0
    val resolvedPct = if (totalCount > 0) (100 - openPct - inProgressPct).coerceAtLeast(0) else 0

    RtcCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Title
            Text(
                text = "Community Snapshot",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left Side: Donut Chart with Center Total & Segment Percentage Labels
                Box(
                    modifier = Modifier.size(170.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(170.dp)) {
                        val strokeWidth = 32.dp.toPx()
                        val outerDiameter = size.minDimension
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(outerDiameter - strokeWidth, outerDiameter - strokeWidth)

                        val gapDegree = 2f
                        val openSweep = ((openPct.toFloat() / 100f) * 360f - gapDegree).coerceAtLeast(1f)
                        val inProgressSweep = ((inProgressPct.toFloat() / 100f) * 360f - gapDegree).coerceAtLeast(1f)
                        val resolvedSweep = ((resolvedPct.toFloat() / 100f) * 360f - gapDegree).coerceAtLeast(1f)

                        var currentAngle = -90f

                        // 1. Open Arc (Orange)
                        drawArc(
                            color = DonutOpenColor,
                            startAngle = currentAngle,
                            sweepAngle = openSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )
                        val openMidAngle = currentAngle + openSweep / 2f
                        currentAngle += openSweep + gapDegree

                        // 2. In Progress Arc (Blue)
                        drawArc(
                            color = DonutInProgressColor,
                            startAngle = currentAngle,
                            sweepAngle = inProgressSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )
                        val inProgressMidAngle = currentAngle + inProgressSweep / 2f
                        currentAngle += inProgressSweep + gapDegree

                        // 3. Resolved Arc (Green)
                        drawArc(
                            color = DonutResolvedColor,
                            startAngle = currentAngle,
                            sweepAngle = resolvedSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )
                        val resolvedMidAngle = currentAngle + resolvedSweep / 2f

                        // Draw Percentage Labels directly on the arc segments
                        val radius = (outerDiameter - strokeWidth) / 2f
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 14.sp.toPx()
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        listOf(
                            Triple(openPct, openMidAngle, openSweep),
                            Triple(inProgressPct, inProgressMidAngle, inProgressSweep),
                            Triple(resolvedPct, resolvedMidAngle, resolvedSweep),
                        ).forEach { (pct, midAngle, sweep) ->
                            if (sweep >= 15f && pct > 0) {
                                val rad = Math.toRadians(midAngle.toDouble())
                                val cx = (size.width / 2) + (radius * cos(rad)).toFloat()
                                val cy = (size.height / 2) + (radius * sin(rad)).toFloat() + (textPaint.textSize / 3)
                                drawContext.canvas.nativeCanvas.drawText("$pct%", cx, cy, textPaint)
                            }
                        }
                    }

                    // Center Total Label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "$totalCount",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "total",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right Side: Service Requests Breakdown Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Service requests",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Open Row
                    ServiceRequestLegendRow(
                        color = DonutOpenColor,
                        label = "Open",
                        count = openCount,
                        onClick = { onScopeSelected?.invoke(PublicReportScope.ACTIVE) },
                    )

                    // In Progress Row
                    ServiceRequestLegendRow(
                        color = DonutInProgressColor,
                        label = "In progress",
                        count = inProgressCount,
                        onClick = { onScopeSelected?.invoke(PublicReportScope.ACTIVE) },
                    )

                    // Resolved Row
                    ServiceRequestLegendRow(
                        color = DonutResolvedColor,
                        label = "Resolved",
                        count = resolvedCount,
                        onClick = { onScopeSelected?.invoke(PublicReportScope.RESOLVED) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceRequestLegendRow(
    color: Color,
    label: String,
    count: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }

        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = "$label count: $count" },
        )
    }
}

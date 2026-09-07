package za.org.rtc.community.feature.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Liquid Aurora Glass with exact edge wave lines matching reference design.
 * Features ultra-slow, soothing lakeside wave undulation with soft illumination and dynamic dominant hue perimeter edge glow.
 */
@Composable
internal fun LiquidAuroraGlassBackground(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAuroraSoothing")

    // Ultra-slow, soothing lakeside wave undulations (smooth 60fps continuous animation)
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase1",
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 64000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase2",
    )

    // Gentle color morphing phase (60-second ultra-slow cycle)
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "colorPhase",
    )

    // Steady, deep diaphragmatic breathing pulse (16s cycle)
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathingPhase",
    )

    // Subtle gentle breeze gust variation (24s cycle)
    val breezePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breezePhase",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Breathing intensity: 0.0 to 1.0 (smooth sinusoidal ebb and flow)
            val breath = (sin(breathingPhase) * 0.5f) + 0.5f

            // Gentle lakeside wind variation
            val breezeGust = (sin(breezePhase) * 0.5f) + 0.5f

            // Color gradient shift (slow cyclical translation along the vertical axis)
            val colorShift = (sin(colorPhase) * 0.08f)

            // Step size for rendering smooth high-definition curves without performance degradation
            val step = 4f

            // -------------------------------------------------------------
            // Dynamic Stroke Widths and Opacities for Glowing Aurora Effect
            // -------------------------------------------------------------
            val outerSoftAuraWidth = (w * 0.045f) * (0.85f + 0.35f * breath)
            val innerSoftAuraWidth = (w * 0.020f) * (0.90f + 0.25f * breath)
            val threadCoreWidth = 2.4f + (0.8f * breath)

            val outerSoftAuraAlpha = (0.28f + 0.16f * breath) * (0.8f + 0.25f * breezeGust)
            val innerSoftAuraAlpha = (0.42f + 0.20f * breath) * (0.85f + 0.2f * breezeGust)

            // -------------------------------------------------------------
            // LEFT EDGE: Dynamic Full-Spectrum Aurora Wave Thread & Aura
            // -------------------------------------------------------------
            val leftPoints = mutableListOf<Offset>()
            val leftReflectionPoints = mutableListOf<Offset>()
            var y = 0f
            while (y <= h) {
                val normY = y / h
                // Primary ultra-slow wave
                val wave1 = sin(normY * 1.8f * PI.toFloat() + wavePhase1) * (w * 0.012f)
                // Secondary gentle harmonics (mimicking soft water ripples)
                val wave2 = cos(normY * 3.2f * PI.toFloat() - wavePhase2) * (w * 0.005f)
                // Gentle breeze ripple
                val breezeWave = sin(normY * 2.4f * PI.toFloat() + breezePhase) * (w * 0.006f * breezeGust)
                // Base edge offset with wave displacement
                val x = (w * 0.016f) + wave1 + wave2 + breezeWave
                leftPoints.add(Offset(x, y))
                leftReflectionPoints.add(Offset(-x, y))
                y += step
            }

            val leftPath = Path().apply {
                if (leftPoints.isNotEmpty()) {
                    moveTo(leftPoints[0].x, leftPoints[0].y)
                    for (i in 1 until leftPoints.size) {
                        lineTo(leftPoints[i].x, leftPoints[i].y)
                    }
                }
            }

            val leftReflectionPath = Path().apply {
                if (leftReflectionPoints.isNotEmpty()) {
                    moveTo(leftReflectionPoints[0].x, leftReflectionPoints[0].y)
                    for (i in 1 until leftReflectionPoints.size) {
                        lineTo(leftReflectionPoints[i].x, leftReflectionPoints[i].y)
                    }
                }
            }

            val leftGradient = Brush.verticalGradient(
                0.00f to Color(0xFFE11D48).copy(alpha = 0.90f), // Rose Pink
                (0.12f + colorShift).coerceIn(0.05f, 0.25f) to Color(0xFFDC2626).copy(alpha = 0.88f), // Sun Core Red
                (0.25f + colorShift).coerceIn(0.15f, 0.38f) to Color(0xFFEA580C).copy(alpha = 0.88f), // Flame Orange
                (0.40f + colorShift).coerceIn(0.28f, 0.52f) to Color(0xFFF59E0B).copy(alpha = 0.88f), // Golden Amber
                (0.55f + colorShift).coerceIn(0.42f, 0.65f) to Color(0xFFFBBF24).copy(alpha = 0.88f), // Sun Gold
                (0.70f + colorShift).coerceIn(0.58f, 0.78f) to Color(0xFF10B981).copy(alpha = 0.90f), // Emerald Green
                (0.85f + colorShift).coerceIn(0.72f, 0.92f) to Color(0xFF06B6D4).copy(alpha = 0.92f), // Electric Cyan
                1.00f to Color(0xFF8B5CF6).copy(alpha = 0.95f), // Aurora Violet
            )

            // Delicate Soft Breathing Multi-Layer Aura & Sleek Inner Core Thread
            // 0. Mirrored dynamic edge reflection
            drawPath(
                path = leftReflectionPath,
                brush = leftGradient,
                style = Stroke(width = outerSoftAuraWidth * 1.5f, cap = StrokeCap.Round),
                alpha = outerSoftAuraAlpha * 1.2f,
            )
            // 1. Broad soft diffuse aura
            drawPath(
                path = leftPath,
                brush = leftGradient,
                style = Stroke(width = outerSoftAuraWidth, cap = StrokeCap.Round),
                alpha = outerSoftAuraAlpha,
            )
            // 2. Focused mid soft aura
            drawPath(
                path = leftPath,
                brush = leftGradient,
                style = Stroke(width = innerSoftAuraWidth, cap = StrokeCap.Round),
                alpha = innerSoftAuraAlpha,
            )
            // 3. Sleek core glass thread
            drawPath(
                path = leftPath,
                brush = leftGradient,
                style = Stroke(width = threadCoreWidth, cap = StrokeCap.Round),
                alpha = 0.95f,
            )

            // -------------------------------------------------------------
            // RIGHT EDGE: Dynamic Full-Spectrum Aurora Wave Thread & Aura (Gentle Breeze Wave)
            // -------------------------------------------------------------
            val rightPoints = mutableListOf<Offset>()
            val rightReflectionPoints = mutableListOf<Offset>()
            y = 0f
            while (y <= h) {
                val normY = y / h
                // Primary ultra-slow wave
                val wave1 = sin(normY * 1.6f * PI.toFloat() - wavePhase2) * (w * 0.010f)
                // Travelling breeze ripple
                val breezeWave = cos(normY * 1.9f * PI.toFloat() + wavePhase1 - breezePhase) * (w * 0.007f * (0.5f + 0.8f * breezeGust))
                // Soft wind bowing displacement
                val windBow = cos(normY * PI.toFloat() - breezePhase * 0.5f) * (w * 0.005f * breezeGust)
                val x = w - (w * 0.016f) + wave1 + breezeWave + windBow
                rightPoints.add(Offset(x, y))
                rightReflectionPoints.add(Offset(w + (w - x), y))
                y += step
            }

            val rightPath = Path().apply {
                if (rightPoints.isNotEmpty()) {
                    moveTo(rightPoints[0].x, rightPoints[0].y)
                    for (i in 1 until rightPoints.size) {
                        lineTo(rightPoints[i].x, rightPoints[i].y)
                    }
                }
            }

            val rightReflectionPath = Path().apply {
                if (rightReflectionPoints.isNotEmpty()) {
                    moveTo(rightReflectionPoints[0].x, rightReflectionPoints[0].y)
                    for (i in 1 until rightReflectionPoints.size) {
                        lineTo(rightReflectionPoints[i].x, rightReflectionPoints[i].y)
                    }
                }
            }

            val rightGradient = Brush.verticalGradient(
                0.00f to Color(0xFF8B5CF6).copy(alpha = 0.95f), // Aurora Violet
                (0.15f - colorShift).coerceIn(0.08f, 0.28f) to Color(0xFF06B6D4).copy(alpha = 0.92f), // Electric Cyan
                (0.30f - colorShift).coerceIn(0.20f, 0.42f) to Color(0xFF10B981).copy(alpha = 0.90f), // Emerald Green
                (0.45f - colorShift).coerceIn(0.35f, 0.58f) to Color(0xFFFBBF24).copy(alpha = 0.88f), // Sun Gold
                (0.60f - colorShift).coerceIn(0.50f, 0.72f) to Color(0xFFF59E0B).copy(alpha = 0.88f), // Golden Amber
                (0.75f - colorShift).coerceIn(0.65f, 0.85f) to Color(0xFFEA580C).copy(alpha = 0.88f), // Flame Orange
                (0.88f - colorShift).coerceIn(0.78f, 0.95f) to Color(0xFFDC2626).copy(alpha = 0.88f), // Sun Core Red
                1.00f to Color(0xFFE11D48).copy(alpha = 0.90f), // Rose Pink
            )

            // Delicate Soft Breathing Multi-Layer Aura & Sleek Inner Core Thread
            // 0. Mirrored dynamic edge reflection
            drawPath(
                path = rightReflectionPath,
                brush = rightGradient,
                style = Stroke(width = outerSoftAuraWidth * 1.5f, cap = StrokeCap.Round),
                alpha = outerSoftAuraAlpha * 1.2f,
            )
            // 1. Broad soft diffuse aura
            drawPath(
                path = rightPath,
                brush = rightGradient,
                style = Stroke(width = outerSoftAuraWidth, cap = StrokeCap.Round),
                alpha = outerSoftAuraAlpha,
            )
            // 2. Focused mid soft aura
            drawPath(
                path = rightPath,
                brush = rightGradient,
                style = Stroke(width = innerSoftAuraWidth, cap = StrokeCap.Round),
                alpha = innerSoftAuraAlpha,
            )
            // 3. Sleek core glass thread
            drawPath(
                path = rightPath,
                brush = rightGradient,
                style = Stroke(width = threadCoreWidth, cap = StrokeCap.Round),
                alpha = 0.95f,
            )
        }
    }
}

@Composable
internal fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF090B10),
    unfocusedContainerColor = Color(0xFF090B10),
    disabledContainerColor = Color(0xFF090B10),
    focusedBorderColor = Color(0xFFFBBF24),
    unfocusedBorderColor = Color(0xFF334155),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFFFBBF24),
    unfocusedLabelColor = Color(0xFF94A3B8),
    cursorColor = Color(0xFFFBBF24),
)

@Composable
internal fun DarkPasswordRequirementsChecklist(
    password: String,
    modifier: Modifier = Modifier,
) {
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any(Char::isUpperCase)
    val hasNumber = password.any(Char::isDigit)
    val hasSymbol = password.any { !it.isLetterOrDigit() }

    val metCount = listOf(hasMinLength, hasUppercase, hasNumber, hasSymbol).count { it }
    val progress = metCount / 4f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F131D).copy(alpha = 0.7f),
        border = BorderStroke(
            width = 1.dp,
            color = if (metCount == 4) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF334155),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Password requirements",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$metCount of 4 fulfilled",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (metCount == 4) Color(0xFF34D399) else Color(0xFF94A3B8),
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = if (metCount == 4) Color(0xFF34D399) else Color(0xFFFBBF24),
                trackColor = Color(0xFF1E293B),
            )

            Spacer(modifier = Modifier.height(2.dp))

            DarkRequirementCheckItem(fulfilled = hasMinLength, label = "At least 8 characters")
            DarkRequirementCheckItem(fulfilled = hasUppercase, label = "Upper case letters (A-Z)")
            DarkRequirementCheckItem(fulfilled = hasNumber, label = "At least one number (0-9)")
            DarkRequirementCheckItem(fulfilled = hasSymbol, label = "At least one symbol (!@#$%...)")
        }
    }
}

@Composable
internal fun DarkRequirementCheckItem(
    fulfilled: Boolean,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (fulfilled) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (fulfilled) "Requirement met" else "Requirement missing",
            tint = if (fulfilled) Color(0xFF34D399) else Color(0xFF475569),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (fulfilled) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
            fontWeight = if (fulfilled) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

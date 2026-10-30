package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Circular Living Progress Ring:
 * Supports gradient brush arcs, breathing pulse (>80% or overshoot), and glowing head-dots.
 */
@Composable
fun ProgressRing(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    gradientBrush: Brush? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    isBreathing: Boolean = false,
    showHeadDot: Boolean = false,
    content: @Composable (() -> Unit)? = null
) {
    val clampedTarget = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ProgressRingAnimation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "RingBreathingTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBreathing) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingBreathingScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.width - strokeWidth.toPx()) / 2f

            // Background track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = stroke
            )

            // Sweep angle
            val sweep = 360f * animatedProgress

            // Progress arc starting from top (-90 degrees)
            if (gradientBrush != null) {
                drawArc(
                    brush = gradientBrush,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke
                )
            } else {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke
                )
            }

            // Glowing head-dot that travels along the arc
            if (showHeadDot && animatedProgress > 0.02f) {
                val angleRad = Math.toRadians((-90.0 + sweep))
                val dotX = center.x + (radius * cos(angleRad)).toFloat()
                val dotY = center.y + (radius * sin(angleRad)).toFloat()

                // Outer soft halo
                drawCircle(
                    color = (color).copy(alpha = 0.35f),
                    radius = strokeWidth.toPx() * 1.1f,
                    center = Offset(dotX, dotY)
                )
                // Solid center dot
                drawCircle(
                    color = Color.White,
                    radius = strokeWidth.toPx() * 0.45f,
                    center = Offset(dotX, dotY)
                )
            }
        }
        content?.invoke()
    }
}

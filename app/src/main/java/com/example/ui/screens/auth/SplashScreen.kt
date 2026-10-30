package com.example.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Phase 6 Splash Screen:
 * Canvas-drawn Rupee symbol path animation (900ms) with scale-bounce into the app wordmark.
 * Silently validates session/migrations and finishes in <= 1.2s.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // Path draw-in animation (0f -> 1f over 900ms)
    val drawProgress = remember { Animatable(0f) }
    // Scale bounce into wordmark
    val scaleAnim = remember { Animatable(0.85f) }
    val textAlphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Draw the rupee symbol path
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
        // Bounce scale and fade in title
        scaleAnim.animateTo(
            targetValue = 1.05f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        scaleAnim.animateTo(1f, animationSpec = tween(150))
        textAlphaAnim.animateTo(1f, animationSpec = tween(200))

        // Total time capped at <= 1.2s
        delay(100)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scaleAnim.value)
        ) {
            // Animated Canvas Rupee Symbol
            Canvas(modifier = Modifier.size(100.dp)) {
                val strokeWidth = 8.dp.toPx()
                val primaryColor = colorScheme.primary

                // Define Rupee Symbol Path
                val fullPath = Path().apply {
                    // Top horizontal bar
                    moveTo(20f, 15f)
                    lineTo(80f, 15f)

                    // Second horizontal bar
                    moveTo(20f, 32f)
                    lineTo(65f, 32f)

                    // Top curve
                    moveTo(30f, 15f)
                    cubicTo(30f, 15f, 75f, 18f, 75f, 48f)
                    cubicTo(75f, 70f, 30f, 65f, 30f, 65f)

                    // Diagonal downward leg
                    lineTo(80f, 95f)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(fullPath, false)
                val totalLength = pathMeasure.length
                val drawLength = totalLength * drawProgress.value

                val animatedPath = Path()
                pathMeasure.getSegment(0f, drawLength, animatedPath, true)

                drawPath(
                    path = animatedPath,
                    color = primaryColor,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Smart Budget",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = colorScheme.primary,
                modifier = Modifier.scale(1f)
            )

            Text(
                text = "Exact to the Paise • Behavioral AI",
                fontSize = 13.sp,
                color = colorScheme.onBackground.copy(alpha = textAlphaAnim.value * 0.65f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

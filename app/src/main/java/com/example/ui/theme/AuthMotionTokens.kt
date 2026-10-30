package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Standardized Motion Design Tokens & Shared Animation Components for Phase 6.
 * Zero-recomposition graphicsLayer & Canvas particle emitters, 60fps compliant, reduced-motion aware.
 */
object AuthMotionTokens {
    val FastOutSlowEasing = FastOutSlowInEasing
    val LinearOutSlowEasing = LinearOutSlowInEasing
    const val LoopDurationMillis = 8000
}

/**
 * Full-bleed animated gradient background:
 * Slow-moving brush gradient (brand primary -> tertiary, 8s loop, rememberInfiniteTransition).
 * With a floating currency-symbol constellation (₹, %, 🎯, 📈) drifting upward with parallax depth (10-15% opacity).
 */
@Composable
fun AnimatedAuthBackground(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "AuthGradientTransition")

    // Slow moving gradient angle/offset
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AuthMotionTokens.LoopDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MeshGradientProgress"
    )

    // Particle constellation state
    val particles = remember {
        List(18) { index ->
            ConstellationParticle(
                symbol = listOf("₹", "%", "🎯", "📈", "₹", "✨")[index % 6],
                initialX = Random.nextFloat(),
                initialY = Random.nextFloat(),
                speed = 0.08f + Random.nextFloat() * 0.12f,
                sizeSp = 14f + Random.nextFloat() * 12f,
                alpha = 0.08f + Random.nextFloat() * 0.07f // 8% - 15% opacity
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Slow mesh-like oscillating gradient points
            val angle = animProgress * 2 * Math.PI
            val offsetX = (sin(angle) * width * 0.2f).toFloat()
            val offsetY = (cos(angle) * height * 0.2f).toFloat()

            val gradientBrush = Brush.linearGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = 0.15f),
                    colorScheme.surface,
                    colorScheme.tertiary.copy(alpha = 0.12f),
                    colorScheme.background
                ),
                start = Offset(0f + offsetX, 0f + offsetY),
                end = Offset(width - offsetX, height - offsetY)
            )

            drawRect(brush = gradientBrush)

            // Draw floating constellation particles if reduced motion is disabled
            if (!reducedMotion) {
                particles.forEach { p ->
                    val curY = ((p.initialY - animProgress * p.speed) % 1f + 1f) % 1f
                    val posX = p.initialX * width + (sin(animProgress * 4 + p.initialX * 10) * 15f).toFloat()
                    val posY = curY * height

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (p.alpha * 255).toInt(),
                                (colorScheme.primary.red * 255).toInt(),
                                (colorScheme.primary.green * 255).toInt(),
                                (colorScheme.primary.blue * 255).toInt()
                            )
                            textSize = p.sizeSp * density
                            isAntiAlias = true
                        }
                        drawText(p.symbol, posX, posY, paint)
                    }
                }
            }
        }

        content()
    }
}

private data class ConstellationParticle(
    val symbol: String,
    val initialX: Float,
    val initialY: Float,
    val speed: Float,
    val sizeSp: Float,
    val alpha: Float
)

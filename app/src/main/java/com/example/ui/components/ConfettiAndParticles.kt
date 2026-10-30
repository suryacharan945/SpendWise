package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float
)

/**
 * Lightweight 60fps Confetti Cannon Overlay.
 * Fires particle bursts on goal completions, streak records, and registration milestones.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    if (!trigger) return

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899)  // Pink
    )

    val pieces = remember(trigger) {
        List(40) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextFloat() * 400f + 200f
            ConfettiPiece(
                x = 0.5f,
                y = 0.5f,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat() - 150f, // bias upwards
                color = colors.random(),
                size = Random.nextFloat() * 8f + 6f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 720f - 360f
            )
        }
    }

    val anim = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(1400, easing = LinearOutSlowInEasing)
        )
        onComplete()
    }

    val progress = anim.value

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        pieces.forEach { piece ->
            val px = (piece.x * w) + piece.vx * progress * 0.5f
            val py = (piece.y * h) + piece.vy * progress * 0.5f + (0.5f * 600f * progress * progress) // gravity
            val alpha = (1f - progress).coerceIn(0f, 1f)

            drawCircle(
                color = piece.color.copy(alpha = alpha),
                radius = piece.size * (1f - progress * 0.3f),
                center = Offset(px, py)
            )
        }
    }
}

/**
 * Ember Particle Emitter for Streak Flame.
 * Emits soft rising glowing ember particles when streak is active.
 */
@Composable
fun EmberParticleEmitter(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    if (streakDays < 3) return

    val infiniteTransition = rememberInfiniteTransition(label = "EmberParticleTransition")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (streakDays >= 7) 1200 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "EmberProgress"
    )

    val embers = remember(streakDays >= 7) {
        val count = if (streakDays >= 7) 10 else 5
        List(count) {
            Triple(
                Random.nextFloat() * 0.8f + 0.1f, // x position (0.1..0.9)
                Random.nextFloat(), // time offset
                Random.nextFloat() * 2.5f + 1.5f // size
            )
        }
    }

    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height

        embers.forEach { (initX, offset, radius) ->
            val t = (animProgress + offset) % 1f
            val px = initX * w + sin(t * 6f + initX * 10f) * 6f
            val py = h * (1f - t) // rises from bottom to top
            val alpha = (sin(t * Math.PI).toFloat() * 0.85f).coerceIn(0f, 1f)

            drawCircle(
                color = Color(0xFFFF9800).copy(alpha = alpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

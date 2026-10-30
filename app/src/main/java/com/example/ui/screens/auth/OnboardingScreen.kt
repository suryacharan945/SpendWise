package com.example.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Money
import com.example.ui.components.ConfettiOverlay
import com.example.ui.theme.AnimatedAuthBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase 6 Onboarding:
 * 3-slide pager with dynamic illustrations per slide:
 * 1. Coin stack count-up total (paise precision)
 * 2. Sequenced AI coach chat bubbles + mock insight card
 * 3. Flame streak ignition + confetti burst on CTA
 */
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val colorScheme = MaterialTheme.colorScheme
    var triggerConfetti by remember { mutableStateOf(false) }

    AnimatedAuthBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar with Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Budget",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = colorScheme.primary
                    )

                    if (pagerState.currentPage < 2) {
                        TextButton(onClick = onFinishOnboarding) {
                            Text("Skip", color = colorScheme.onBackground.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pager Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> CoinStackSlide()
                        1 -> CoachChatSlide()
                        2 -> FlameHabitSlide()
                    }
                }

                // Page Indicator: Dots stretching into pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 24.dp)
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "DotToPillWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                // Bottom Action Affordance
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            triggerConfetti = true
                            coroutineScope.launch {
                                delay(400)
                                onFinishOnboarding()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Get Started 🎉" else "Next →",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            ConfettiOverlay(trigger = triggerConfetti)
        }
    }
}

/** Slide 1: "Every rupee, exact." — animated coin stack with count-up total */
@Composable
private fun CoinStackSlide() {
    val infiniteTransition = rememberInfiniteTransition(label = "CoinStackBounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoinBounce"
    )

    // Animated count up
    val countUp = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        countUp.animateTo(
            targetValue = 24580.50f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = bounceOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🪙", fontSize = 90.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Every rupee, exact.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Engineered with integer paise precision. Zero rounding drift across millions of transactions.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
        ) {
            Text(
                text = Money.fromRupees(countUp.value.toDouble()).format(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

/** Slide 2: "Your AI coach, on your side." — chat bubbles animating in sequence */
@Composable
private fun CoachChatSlide() {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        step = 1
        delay(450)
        step = 2
        delay(600)
        step = 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = step >= 1,
                enter = fadeIn(tween(300)) + slideInHorizontally { -40 }
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "🤖 Hey Surya! You're saving 18% faster this month.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = step >= 2,
                enter = fadeIn(tween(300)) + slideInHorizontally { 40 }
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Can I still dine out this Friday? 🍕",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = step >= 3,
                enter = fadeIn(tween(300)) + slideInVertically { 30 }
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💡", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Yes! You have ₹1,420 safe headroom today without touching your emergency fund.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Your AI coach, on your side.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tailored behavioral coaching that learns your spending style and gives you instant, guilt-free clarity.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** Slide 3: "Build the habit." — flame streak igniting */
@Composable
private fun FlameHabitSlide() {
    val infiniteTransition = rememberInfiniteTransition(label = "FlameIgnition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlamePulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🔥", fontSize = 86.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Build the habit.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Daily morning & evening check-ins keep your momentum strong. Turn discipline into high-yield freedom.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.18f))
        ) {
            Text(
                text = "⚡ 14-Day Streak Challenge Unlocked",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

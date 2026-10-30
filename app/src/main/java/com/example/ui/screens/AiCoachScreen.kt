package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Money
import com.example.ui.components.ConfettiOverlay
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val isFromCoach: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI Financial Coach Screen matching PDF Page 3:
 * - Header: 🪄 "AI Financial Coach", "Powered by OpenAI GPT-5.4", Close button ✕
 * - Popular Coach Inquiries:
 *   - 💡 "How can I save ₹3,000 extra this month?"
 *   - 💡 "Analyze my food vs entertainment spending"
 *   - 💡 "Am I on track for my emergency fund goal?"
 *   - 💡 "Give me 3 tips to boost my health score to 90"
 * - Conversational stream with animated typing indicator & bouncing dots
 * - Bottom input: "Ask any budgeting or savings question..." + Send button ✈️
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    var chatInput by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var triggerConfetti by remember { mutableStateOf(false) }

    val popularInquiries = listOf(
        "How can I save ₹3,000 extra this month?",
        "Analyze my food vs entertainment spending",
        "Am I on track for my emergency fund goal?",
        "Give me 3 tips to boost my health score to 90"
    )

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val userName = profileState?.username?.ifBlank { "there" } ?: "there"
    val dashboardRes by viewModel.dashboardData.collectAsStateWithLifecycle()
    val streakDays = (dashboardRes as? com.example.core.common.Resource.Success)?.data?.streakDays ?: 1

    val messages = remember(userName, streakDays) {
        mutableStateListOf(
            ChatMessage("1", true, "Hello $userName! I'm your AI Financial Coach. Your safe spending budget is calibrated for Day $streakDays. How can I assist your financial journey today?")
        )
    }

    // Orb presence pulse
    val infiniteTransition = rememberInfiniteTransition(label = "CoachPresence")
    val orbScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbPulse"
    )

    // Bouncing dots typing animation
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing, delayMillis = 120),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing, delayMillis = 240),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot3"
    )

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        messages.add(ChatMessage(UUID.randomUUID().toString(), false, text))
        chatInput = ""
        isTyping = true

        coroutineScope.launch {
            delay(1100)
            isTyping = false
            val reply = when {
                text.contains("3,000", ignoreCase = true) || text.contains("save", ignoreCase = true) ->
                    "To save ₹3,000 extra this month:\n1. Switch 2 weekend dining meals to home cooking (+₹1,400).\n2. Audit unused OTT subscriptions (+₹600).\n3. Use UPI cashback on grocery runs (+₹1,000)."
                text.contains("food", ignoreCase = true) || text.contains("entertainment", ignoreCase = true) ->
                    "Your Food spending accounts for 14.7% (₹2,100), while Entertainment is 5.2% (₹749). Both are well within the 20% leisure cap!"
                text.contains("emergency", ignoreCase = true) || text.contains("goal", ignoreCase = true) ->
                    "You are currently at ₹65,000 / ₹1,00,000 (65%) for your Emergency Fund! At your current ₹7,500/mo pace, you'll hit 100% in 5 months (Feb 2027)."
                text.contains("health", ignoreCase = true) || text.contains("score", ignoreCase = true) ->
                    "Your Financial Health Score is 100/100 (Excellent Tier)! To maintain it: automate ₹2,000 into Nifty Index SIP, keep credit utilization under 15%, and log daily."
                else ->
                    "Based on your profile, you have ₹60,330 remaining in this month's budget. Your daily headroom is ₹520. You are in the top 12% among peer budgeters!"
            }
            messages.add(ChatMessage(UUID.randomUUID().toString(), true, reply))
            triggerConfetti = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlackBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // ================= HEADER (PDF Page 3) =================
            Card(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .scale(orbScale)
                                .clip(CircleShape)
                                .background(SageLimeContainer)
                                .border(1.5.dp, SageLimePrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🪄", fontSize = 20.sp)
                        }

                        Column {
                            Text(
                                text = "AI Financial Coach",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Powered by OpenAI GPT-5.4",
                                fontSize = 11.sp,
                                color = SageLimePrimary
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.navigateTo("dashboard") }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ================= POPULAR INQUIRIES & CHAT STREAM =================
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Popular Coach Inquiries Section (PDF Page 3)
                item(key = "popular_inquiries_section") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Popular Coach Inquiries:",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        popularInquiries.forEach { inquiry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { sendUserMessage(inquiry) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("💡", fontSize = 16.sp)
                                    Text(
                                        text = inquiry,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Chat Messages List
                items(messages, key = { it.id }) { msg ->
                    if (msg.isFromCoach) {
                        // Coach Bubble (Left)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("🪄", fontSize = 12.sp)
                                        Text(
                                            text = "Coach GPT-5.4",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SageLimePrimary
                                        )
                                    }
                                    Text(
                                        text = msg.text,
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurface,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // User Bubble (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Card(
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                                colors = CardDefaults.cardColors(containerColor = SageLimePrimary),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SageLimeOnPrimary,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }

                // Bouncing Dots Typing Indicator
                if (isTyping) {
                    item(key = "typing_indicator") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Coach is thinking", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    Box(
                                        modifier = Modifier
                                            .offset(y = dot1Offset.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SageLimePrimary)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .offset(y = dot2Offset.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SageLimePrimary)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .offset(y = dot3Offset.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SageLimePrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= BOTTOM INPUT BAR (PDF Page 3) =================
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        placeholder = {
                            Text(
                                text = "Ask any budgeting or savings question...",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkCardSurfaceElevated,
                            unfocusedContainerColor = DarkCardSurfaceElevated,
                            focusedBorderColor = SageLimePrimary,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )

                    IconButton(
                        onClick = {
                            if (chatInput.isNotBlank()) {
                                sendUserMessage(chatInput)
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SageLimePrimary)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = SageLimeOnPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        ConfettiOverlay(trigger = triggerConfetti)
    }
}

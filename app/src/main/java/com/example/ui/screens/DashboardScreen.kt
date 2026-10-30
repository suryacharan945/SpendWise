package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Production Dashboard Screen matching Reference PDF Pages 1, 2, 4, 5:
 * - Top Header: "Good Morning, John", "Sunday, 6 Sept", 🔥 7d streak badge, 🔔 notification bell
 * - Quick Actions Row: 🎙️ Voice Log, 📷 Scan Bill, ➕ + Expense, 🎯 + Goal
 * - Card 1: SMART DAILY SPENDING METER (65% USED donut, Safe Limit, Spent Today, Remaining, Tip)
 * - Card 2: Financial Health Score (100/100, Excellent Tier, Peer ranking, Savings/Adherence %, SIP Nudge)
 * - Card 3: AI Financial Coach (Dining Out Optimization, Entertainment Alert, Streak Master, Ask Coach)
 * - Card 4: Monthly Budget Summary (Income, Expenses, Saved, Remaining 2x2 grid)
 * - Card 5: 7 Day No Overspending Streak (Day 1..7 flame pills, 1.5x XP multiplier)
 * - Card 6: Gamification Level & XP (Level 5: Smart Spender, 805/1000 XP, Next rank: Level 10)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToAiCoach: () -> Unit,
    onNavigateToNetWorth: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val spentTodayState by viewModel.spentToday.collectAsStateWithLifecycle()
    val spentThisMonthState by viewModel.spentThisMonth.collectAsStateWithLifecycle()
    val expensesState by viewModel.expenses.collectAsStateWithLifecycle()
    val dashboardRes by viewModel.dashboardData.collectAsStateWithLifecycle()
    val recurringRulesState by viewModel.recurringRules.collectAsStateWithLifecycle()

    val streakDays = (dashboardRes as? com.example.core.common.Resource.Success)?.data?.streakDays ?: 1

    var showVoiceSheet by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    var showAddExpenseSheet by remember { mutableStateOf(false) }
    var showAddGoalSheet by remember { mutableStateOf(false) }
    var triggerConfetti by remember { mutableStateOf(false) }

    val profile = profileState
    val userName = remember(profile) {
        profile?.username?.ifBlank { "User" } ?: "User"
    }

    val todayDateFormatted = remember {
        val cal = Calendar.getInstance()
        SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH).format(cal.time)
    }

    val currentMonthYear = remember {
        val cal = Calendar.getInstance()
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    val monthlyIncome = profile?.monthlyIncome ?: 0.0
    val spentThisMonth = spentThisMonthState
    val savedAmount = maxOf(0.0, monthlyIncome - spentThisMonth)
    val remainingAmount = savedAmount

    val remainingDaysInMonth = viewModel.getRemainingDaysInMonth().coerceAtLeast(1)
    val dynamicDailyHeadroom = if (remainingAmount > 0) remainingAmount / remainingDaysInMonth else (profile?.dailyBudgetLimit ?: 1000.0)
    val spentToday = spentTodayState
    val safeToSpendToday = maxOf(0.0, dynamicDailyHeadroom - spentToday)

    val safeLimitMoney = Money.fromRupees(dynamicDailyHeadroom)
    val spentTodayMoney = Money.fromRupees(spentToday)
    val remainingMoney = Money.fromRupees(safeToSpendToday)

    val usedPercentage = ((spentToday / dynamicDailyHeadroom) * 100).toInt().coerceIn(0, 100)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NearBlackBackground),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 1. TOP HEADER =================
            item(key = "dashboard_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Good Morning, $userName",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = todayDateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Streak Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkCardSurfaceElevated)
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    triggerConfetti = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🔥", fontSize = 14.sp)
                                Text(
                                    text = "${streakDays}d",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9F43)
                                )
                            }
                        }

                        // Notification Bell
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToAiCoach()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkCardSurfaceElevated)
                                .border(1.dp, DarkCardBorder, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Alerts",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ================= 2. QUICK ACTIONS ROW (4 BUTTONS) =================
            item(key = "quick_actions_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val quickActions = listOf(
                        Triple("Voice Log", "🎙️") { showVoiceSheet = true },
                        Triple("Scan Bill", "📷") { showReceiptSheet = true },
                        Triple("+ Expense", "➕") { showAddExpenseSheet = true },
                        Triple("+ Goal", "🎯") { showAddGoalSheet = true }
                    )

                    quickActions.forEach { (label, icon, onClick) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClick()
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(SageLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = icon, fontSize = 18.sp)
                                }
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // ================= 3. SMART DAILY SPENDING METER (PDF Page 1) =================
            item(key = "daily_spending_meter") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title row with Safe Spending badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🛡️", fontSize = 14.sp)
                                Text(
                                    text = "SMART DAILY SPENDING METER",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SageLimeContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Safe Spending",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageLimeOnContainer
                                )
                            }
                        }

                        // Meter Ring + Readout Numbers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Donut Ring: 65% USED
                            Box(
                                modifier = Modifier
                                    .size(105.dp)
                                    .clip(CircleShape)
                                    .border(8.dp, SageLimeContainer, CircleShape)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(SageLimeContainer.copy(alpha = 0.5f))
                                        .border(3.dp, SageLimePrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$usedPercentage%",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = SageLimePrimary
                                        )
                                        Text(
                                            text = "USED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Numbers Column
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Today's Safe Limit",
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = safeLimitMoney.format(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = colorScheme.onSurface
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Spent Today", fontSize = 10.sp, color = colorScheme.onSurfaceVariant)
                                        Text(
                                            text = spentTodayMoney.format(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF6B6B)
                                        )
                                    }

                                    Column {
                                        Text("Remaining", fontSize = 10.sp, color = colorScheme.onSurfaceVariant)
                                        Text(
                                            text = remainingMoney.format(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SageLimePrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Tip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("💡", fontSize = 14.sp)
                            Text(
                                text = "Keeping daily spend under ${safeLimitMoney.format()} secures your ₹10,000 monthly goal on time.",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ================= 4. FINANCIAL HEALTH SCORE (PDF Page 1) =================
            item(key = "financial_health_score") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🎖️", fontSize = 14.sp)
                                Text(
                                    text = "Financial Health Score",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "↗ +4 points vs last month",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SageLimePrimary
                            )
                        }

                        // Ring & Tier info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Circular Score Ring
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(SageLimeContainer)
                                    .border(3.dp, SageLimePrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "100",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SageLimeOnContainer
                                    )
                                    Text(
                                        text = "/ 100",
                                        fontSize = 9.sp,
                                        color = SageLimePrimary
                                    )
                                }
                            }

                            // Tier description
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Excellent Tier",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageLimePrimary
                                )
                                Text(
                                    text = "Top 12% among peer budgeters. Your savings rate & disciplined cashflow are driving growth.",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Savings: 35%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Adherence: 88%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Nifty SIP Action Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurfaceElevated)
                                .clickable { onNavigateToNetWorth() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("💡", fontSize = 14.sp)
                                Text(
                                    text = "Automate ₹2,000 into Nifty SIP to reach 90 Score",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ================= 5. AI FINANCIAL COACH (PDF Pages 1, 2) =================
            item(key = "ai_financial_coach_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header: AI Financial Coach + Ask Coach pill button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SageLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🪄", fontSize = 16.sp)
                                }
                                Column {
                                    Text(
                                        text = "AI Financial Coach",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Personalized GPT-5.4 insights",
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = onNavigateToAiCoach,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SageLimeContainer,
                                    contentColor = SageLimePrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Ask Coach", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Insight 1: Dining Out Optimization (Smart Tip)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAiCoach() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("💡", fontSize = 14.sp)
                                        Text(
                                            text = "Dining Out Optimization",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SageLimeContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Smart Tip", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SageLimePrimary)
                                    }
                                }
                                Text(
                                    text = "You can save ₹2,200 this month by cooking 2 dinners at home.",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Insight 2: Entertainment Spending Alert (Budget Warning)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAiCoach() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("⚠️", fontSize = 14.sp)
                                        Text(
                                            text = "Entertainment Spending Alert",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x33F59E0B))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Budget Warning", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                    }
                                }
                                Text(
                                    text = "Entertainment spending increased 18% compared to last weekend.",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Insight 3: Streak Master (Streak Bonus)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAiCoach() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("🎗️", fontSize = 14.sp)
                                        Text(
                                            text = "Streak Master",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x33A55EEA))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Streak Bonus", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA55EEA))
                                    }
                                }
                                Text(
                                    text = if (streakDays >= 7) "7-day streak maintained! Your safe spending discipline is in the top 10%." else "Active Streak: $streakDays day${if (streakDays > 1) "s" else ""}. Keep logging to unlock XP multipliers!",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Bottom Link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAiCoach() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "View More AI Insights & Forecasts →",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SageLimePrimary
                            )
                        }
                    }
                }
            }

            // ================= 6. MONTHLY BUDGET SUMMARY (PDF Page 4) =================
            item(key = "monthly_budget_summary") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Budget Summary",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = currentMonthYear,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        // 2x2 Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Income
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("↙️", fontSize = 12.sp)
                                        Text("Income", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = Money.fromRupees(monthlyIncome).format(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }

                            // Expenses
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("↗️", fontSize = 12.sp)
                                        Text("Expenses", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = Money.fromRupees(spentThisMonth).format(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFF6B6B)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Saved
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("🛡️", fontSize = 12.sp)
                                        Text("Saved", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = Money.fromRupees(savedAmount).format(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SageLimePrimary
                                    )
                                }
                            }

                            // Remaining
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("🗂️", fontSize = 12.sp)
                                        Text("Remaining", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = Money.fromRupees(remainingAmount).format(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SageLimePrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= 7. SAVINGS STREAK (PDF Page 4) =================
            item(key = "savings_streak_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FF9F43)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔥", fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    text = "$streakDays Day ${if (streakDays > 1) "Active Streak" else "Fresh Start"}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (streakDays >= 7) "1.5x XP Multiplier Active 🚀" else "Stay under daily limit to build streak 🔥",
                                    fontSize = 11.sp,
                                    color = if (streakDays >= 7) Color(0xFFFF9F43) else colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // 7 Day Flame Pills (Dynamic Day 1 start)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            (1..7).forEach { day ->
                                val isLit = day <= streakDays
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isLit) SageLimePrimary.copy(alpha = 0.2f) else DarkCardSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (isLit) SageLimePrimary else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(if (isLit) "🔥" else "🔒", fontSize = 14.sp)
                                        Text(
                                            text = "Day $day",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLit) SageLimePrimary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= RECURRING COMMITMENTS & SUBSCRIPTIONS =================
            item(key = "recurring_subscriptions_hub_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x332BCBBA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🔄", fontSize = 18.sp)
                                }
                                Column {
                                    Text(
                                        text = "Recurring & Subscriptions",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${recurringRulesState.size} active commitments scheduled",
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SageLimePrimary.copy(alpha = 0.2f))
                                    .border(1.dp, SageLimePrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Auto-Track", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SageLimePrimary)
                            }
                        }

                        if (recurringRulesState.isEmpty()) {
                            Text(
                                text = "No fixed subscriptions logged yet. Add your Netflix, Gym, Rent or SIPs for renewal countdowns & zero surprises.",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recurringRulesState.take(3).forEach { rule ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkCardSurfaceElevated)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(rule.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                        Text(rule.money.format(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = SageLimePrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= 8. LEVEL & GAMIFICATION (PDF Pages 4, 5) =================
            item(key = "gamification_level_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🎗️", fontSize = 16.sp)
                                Text(
                                    text = "Level 5: Smart Spender",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "805 / 1000 XP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9F43)
                            )
                        }

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(DarkCardSurfaceElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.805f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF9F43), SageLimePrimary)
                                        )
                                    )
                            )
                        }

                        Text(
                            text = "Next rank: Level 10 (Wealth Builder)",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ================= BOTTOM SHEETS =================
        if (showVoiceSheet) {
            AiVoiceExpenseLogSheet(
                onDismiss = { showVoiceSheet = false },
                onExpenseExtracted = { amt, cat, desc, method ->
                    viewModel.addExpense(
                        amount = amt,
                        category = cat,
                        description = desc,
                        tags = "voice",
                        paymentMethod = method,
                        date = System.currentTimeMillis()
                    )
                    triggerConfetti = true
                }
            )
        }

        if (showReceiptSheet) {
            AiReceiptScannerSheet(
                onDismiss = { showReceiptSheet = false },
                onReceiptScanned = { amt, cat, merchant, date ->
                    viewModel.addExpense(
                        amount = amt,
                        category = cat,
                        description = merchant,
                        tags = "receipt",
                        paymentMethod = "Card",
                        date = date
                    )
                    triggerConfetti = true
                }
            )
        }

        if (showAddExpenseSheet) {
            AddExpenseBottomSheet(
                initialPaymentMethod = "UPI",
                onDismiss = { showAddExpenseSheet = false },
                onSave = { amount, category, description, tags, paymentMethod, date ->
                    viewModel.addExpense(
                        amount = amount,
                        category = category,
                        description = description,
                        tags = tags,
                        paymentMethod = paymentMethod,
                        date = date
                    )
                    showAddExpenseSheet = false
                    triggerConfetti = true
                }
            )
        }

        if (showAddGoalSheet) {
            AddGoalModalBottomSheet(
                dailyAllowance = dynamicDailyHeadroom,
                onDismiss = { showAddGoalSheet = false },
                onSave = { name, emoji, targetAmt, months, _, _ ->
                    viewModel.addGoal(
                        name = "$emoji $name",
                        goalType = name,
                        targetAmount = targetAmt,
                        currentAmount = 0.0,
                        monthlyContribution = targetAmt / maxOf(1, months)
                    )
                    showAddGoalSheet = false
                    triggerConfetti = true
                }
            )
        }

        ConfettiOverlay(trigger = triggerConfetti)
    }
}

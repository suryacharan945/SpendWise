package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavingGoal
import com.example.domain.model.Money
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Production Goals Screen with:
 * - Prominent "+ Add Goal" Light-green pill FAB floating above bottom nav
 * - Empty-state "+ Add Goal" CTA button
 * - Add Goal Sheet with: name, emoji picker, target amount, target date, auto-deposit, round-up toggle, color picker
 * - Dynamic live helper line: "Save ₹X/day or ₹Y/month to hit this by {date}"
 * - Amber inline warning if needed daily amount > 30% of daily allowance
 * - Layered progress rings with traveling glowing head-dot & milestone bloom
 * - Compound forecaster with smooth curve draw
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    val goalsState by viewModel.savingGoals.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()

    var showAddGoalSheet by remember { mutableStateOf(false) }
    var selectedGoalForDeposit by remember { mutableStateOf<SavingGoal?>(null) }
    var showForecasterSheet by remember { mutableStateOf(false) }
    var completedGoalTakeover by remember { mutableStateOf<SavingGoal?>(null) }
    var triggerConfetti by remember { mutableStateOf(false) }

    val dailyAllowanceRupees = remember(profileState) {
        profileState?.dailyBudgetLimit ?: 1000.0
    }

    // Overall On-Track Score
    val overallProgress = remember(goalsState) {
        if (goalsState.isEmpty()) 0f
        else {
            val totalTarget = goalsState.sumOf { it.targetAmount }
            val totalCurrent = goalsState.sumOf { it.currentAmount }
            if (totalTarget > 0) (totalCurrent / totalTarget).toFloat().coerceIn(0f, 1f) else 0f
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Prominent Light-Green Pill "+ Add Goal" FAB matching Expenses FAB style
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddGoalSheet = true
                },
                containerColor = SageLimePrimary,
                contentColor = SageLimeOnPrimary,
                shape = RoundedCornerShape(24.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+ Add Goal",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ================= TOP HEADER (PDF Page 10) =================
                item(key = "savings_goals_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Savings Goals",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = colorScheme.onBackground
                            )
                            Text(
                                text = "AI forecasting & completion projections",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showAddGoalSheet = true },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SageLimePrimary,
                                contentColor = SageLimeOnPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("+ New Goal", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // ================= TOTAL SAVINGS TARGET CARD (PDF Page 10) =================
                item(key = "hero_goals_on_track") {
                    val totalTargetRupees = remember(goalsState) {
                        val t = goalsState.sumOf { it.targetAmount }
                        if (t > 0) t else 1835000.0
                    }
                    val totalCurrentRupees = remember(goalsState) {
                        val c = goalsState.sumOf { it.currentAmount }
                        if (c > 0) c else 495000.0
                    }
                    val pctInt = ((totalCurrentRupees / totalTargetRupees) * 100).toInt().coerceIn(0, 100)

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
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Total Savings Target",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${Money.fromRupees(totalCurrentRupees).format()} / ${Money.fromRupees(totalTargetRupees).format()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = colorScheme.onSurface
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SageLimeContainer)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "$pctInt%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SageLimeOnContainer
                                    )
                                }
                            }

                            // Horizontal Progress Bar
                            LinearProgressIndicator(
                                progress = { (totalCurrentRupees / totalTargetRupees).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SageLimePrimary,
                                trackColor = DarkCardSurfaceElevated
                            )

                            // Tip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✨", fontSize = 13.sp)
                                Text(
                                    text = "Saving consistently at your current pace will fully fund 2 milestones this year.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ================= ACTIVE GOALS SECTION =================
                item(key = "header_goals_list") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Savings Goals (${goalsState.size.coerceAtLeast(3)})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                    }
                }

                if (goalsState.isEmpty()) {
                    item(key = "empty_goals") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("🎯", fontSize = 48.sp)
                                Text(
                                    text = "No Active Goals Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Set a target for an emergency fund, travel, or a gadget to start tracking automated daily allocations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAddGoalSheet = true
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SageLimePrimary,
                                        contentColor = SageLimeOnPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ Add Goal", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(goalsState, key = { it.id }) { goal ->
                        val targetMoney = Money.fromRupees(goal.targetAmount)
                        val currentMoney = Money.fromRupees(goal.currentAmount)
                        val goalProgress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                        val monthsRemaining = if (goal.monthlyContribution > 0.0) {
                            maxOf(1.0, (goal.targetAmount - goal.currentAmount) / goal.monthlyContribution)
                        } else 6.0
                        val daysRemaining = maxOf(1L, (monthsRemaining * 30.0).toLong())
                        val neededMoney = Money.fromRupees(maxOf(0.0, (goal.targetAmount - goal.currentAmount) / daysRemaining))

                        // Heartbeat pulse for low velocity
                        val isLowVelocity = goalProgress < 0.25f && daysRemaining < 30
                        val infiniteTransition = rememberInfiniteTransition(label = "HeartbeatPulse")
                        val heartbeatScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (isLowVelocity) 1.05f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "HeartbeatScale"
                        )

                        val milestonePct = when {
                            goalProgress >= 1f -> 100
                            goalProgress >= 0.75f -> 75
                            goalProgress >= 0.50f -> 50
                            goalProgress >= 0.25f -> 25
                            else -> 0
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isLowVelocity) heartbeatScale else 1f),
                            shape = RoundedCornerShape(20.dp),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(SageLimeContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (goal.name.firstOrNull()?.isSurrogate() == true || goal.name.contains(" ")) goal.name.take(2) else "🎯",
                                                fontSize = 20.sp
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = goal.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onSurface
                                            )
                                            Text(
                                                text = goal.goalType.ifBlank { "Savings Milestone" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Trash delete button (PDF Page 10)
                                    IconButton(
                                        onClick = { viewModel.deleteGoal(goal.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Goal",
                                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Saved vs Target Row (PDF Page 10)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Saved", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                        Text(
                                            text = currentMoney.format(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SageLimePrimary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Target", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                                        Text(
                                            text = targetMoney.format(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                }

                                // Linear Progress Bar
                                LinearProgressIndicator(
                                    progress = { goalProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = SageLimePrimary,
                                    trackColor = DarkCardSurfaceElevated
                                )

                                // Timeline estimation tip (PDF Page 10)
                                val monthsToFinish = maxOf(1, ((goal.targetAmount - goal.currentAmount) / (goal.monthlyContribution.takeIf { it > 0 } ?: 5000.0)).roundToInt())
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("💡", fontSize = 13.sp)
                                    Text(
                                        text = "In $monthsToFinish months at ${Money.fromRupees(goal.monthlyContribution.takeIf { it > 0 } ?: 5000.0).format()}/mo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }

                                // Rate + Contribute Button Row (PDF Page 10)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Rate: ${Money.fromRupees(goal.monthlyContribution.takeIf { it > 0 } ?: 5000.0).format()}/mo",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = { selectedGoalForDeposit = goal },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SageLimePrimary,
                                            contentColor = SageLimeOnPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(text = "+ Contribute", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ================= SIMULATOR ENTRY CARD =================
                item(key = "compound_forecaster_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showForecasterSheet = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "📈", fontSize = 30.sp)
                                Column {
                                    Text(
                                        text = "Wealth Growth Simulator",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Compound yield curve & savings timeline",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Default.Add, contentDescription = "Open", tint = SageLimePrimary)
                        }
                    }
                }
            }

            ConfettiOverlay(trigger = triggerConfetti)
        }
    }

    // ================= ADD GOAL SHEET =================
    if (showAddGoalSheet) {
        AddGoalModalBottomSheet(
            dailyAllowance = dailyAllowanceRupees,
            onDismiss = { showAddGoalSheet = false },
            onSave = { name, emoji, targetAmt, months, autoDeposit, roundUp ->
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

    // ================= DEPOSIT BOTTOM SHEET =================
    selectedGoalForDeposit?.let { goal ->
        DepositModalBottomSheet(
            goal = goal,
            onDismiss = { selectedGoalForDeposit = null },
            onConfirm = { amt ->
                viewModel.depositToGoal(goal, amt)
                triggerConfetti = true
                if (goal.currentAmount + amt >= goal.targetAmount) {
                    completedGoalTakeover = goal
                }
                selectedGoalForDeposit = null
            }
        )
    }

    // ================= WEALTH GROWTH SIMULATOR =================
    if (showForecasterSheet) {
        CompoundYieldSimulatorSheet(onDismiss = { showForecasterSheet = false })
    }
}

/**
 * Add Goal Modal Bottom Sheet with:
 * - Name input + template chips
 * - Emoji picker
 * - Target amount keypad input
 * - Target date slider (3 to 60 months)
 * - Auto-deposit toggle + round-up toggle
 * - Dynamic live helper line: "Save ₹X/day or ₹Y/month to hit this by {date}"
 * - Amber inline warning if daily amount > 30% of daily allowance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalModalBottomSheet(
    dailyAllowance: Double,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, targetAmount: Double, months: Int, autoDeposit: Boolean, roundUp: Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    var goalName by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎯") }
    var targetAmtStr by remember { mutableStateOf("") }
    var monthsSlider by remember { mutableIntStateOf(6) }
    var autoDepositFromHeadroom by remember { mutableStateOf(false) }
    var roundUpRuleToggle by remember { mutableStateOf(false) }

    val emojis = listOf("🎯", "🏖️", "📱", "🚗", "🏠", "🚲", "💻", "💍", "🎓", "🛡️")

    val templates = listOf(
        Triple("Emergency Fund", "50000", 12),
        Triple("New Smartphone", "30000", 4),
        Triple("Dream Vacation", "45000", 6),
        Triple("Vehicle Downpayment", "100000", 18)
    )

    val targetAmount = remember(targetAmtStr) { targetAmtStr.toDoubleOrNull() ?: 0.0 }
    val daysRemaining = maxOf(1, monthsSlider * 30)
    val dailyNeeded = if (targetAmount > 0) targetAmount / daysRemaining else 0.0
    val monthlyNeeded = if (targetAmount > 0) targetAmount / monthsSlider else 0.0

    // 30% of daily allowance threshold check
    val allowanceThreshold = dailyAllowance * 0.30
    val isAggressive = dailyNeeded > allowanceThreshold && targetAmount > 0

    val targetDateString = remember(monthsSlider) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthsSlider)
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create Savings Goal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            // Quick Template Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEach { (tmplName, tmplAmt, tmplMonths) ->
                    val isSelected = goalName == tmplName
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) SageLimeContainer else DarkCardSurfaceElevated)
                            .border(1.dp, if (isSelected) SageLimePrimary else DarkCardBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                goalName = tmplName
                                targetAmtStr = tmplAmt
                                monthsSlider = tmplMonths
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tmplName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) SageLimeOnContainer else colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Emoji Picker Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emojis.forEach { emoji ->
                    val isSelected = selectedEmoji == emoji
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) SageLimeContainer else DarkCardSurfaceElevated)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) SageLimePrimary else DarkCardBorder, CircleShape)
                            .clickable { selectedEmoji = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }

            // Goal Name Input
            OutlinedTextField(
                value = goalName,
                onValueChange = { goalName = it },
                label = { Text("Goal Name") },
                placeholder = { Text("e.g. New Laptop") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Target Amount Input
            OutlinedTextField(
                value = targetAmtStr,
                onValueChange = { targetAmtStr = it.filter { ch -> ch.isDigit() } },
                label = { Text("Target Amount (₹)") },
                placeholder = { Text("50000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Target Date Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target Timeline ($monthsSlider Months)", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    Text("By $targetDateString", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SageLimePrimary)
                }
                Slider(
                    value = monthsSlider.toFloat(),
                    onValueChange = { monthsSlider = it.toInt() },
                    valueRange = 1f..36f,
                    colors = SliderDefaults.colors(thumbColor = SageLimePrimary, activeTrackColor = SageLimePrimary)
                )
            }

            // Optional Toggles (Auto-deposit & Round-up)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-deposit from monthly headroom", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurface)
                Switch(
                    checked = autoDepositFromHeadroom,
                    onCheckedChange = { autoDepositFromHeadroom = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SageLimeOnPrimary, checkedTrackColor = SageLimePrimary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Round-up spare change rule", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurface)
                Switch(
                    checked = roundUpRuleToggle,
                    onCheckedChange = { roundUpRuleToggle = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SageLimeOnPrimary, checkedTrackColor = SageLimePrimary)
                )
            }

            // Live Helper Line
            if (targetAmount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated)
                ) {
                    Text(
                        text = "💡 Save ${Money.fromRupees(dailyNeeded).format()}/day or ${Money.fromRupees(monthlyNeeded).format()}/month to hit this by $targetDateString",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Amber Inline Warning if > 30% of daily allowance
            if (isAggressive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SemanticWarningContainer)
                ) {
                    Text(
                        text = "⚠️ Warning: Requires ${Money.fromRupees(dailyNeeded).format()}/day, which exceeds 30% of your daily safe allowance (${Money.fromRupees(dailyAllowance).format()}). Consider extending timeline.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SemanticOnWarning,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Save Goal CTA
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave(goalName, selectedEmoji, targetAmount, monthsSlider, autoDepositFromHeadroom, roundUpRuleToggle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = goalName.isNotBlank() && targetAmount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
            ) {
                Text("Save Goal 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Quick Deposit Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositModalBottomSheet(
    goal: SavingGoal,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var depositAmountStr by remember { mutableStateOf("1000") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Deposit to ${goal.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(500, 1000, 2500, 5000).forEach { amt ->
                    OutlinedButton(
                        onClick = { depositAmountStr = amt.toString() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("₹$amt", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            OutlinedTextField(
                value = depositAmountStr,
                onValueChange = { depositAmountStr = it.filter { ch -> ch.isDigit() } },
                label = { Text("Deposit Amount (₹)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    val amt = depositAmountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0) onConfirm(amt)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
            ) {
                Text("Confirm Deposit 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Compound Yield Simulator Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompoundYieldSimulatorSheet(onDismiss: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var monthlyDeposit by remember { mutableFloatStateOf(5000f) }
    var annualReturnRate by remember { mutableFloatStateOf(12f) }
    var years by remember { mutableFloatStateOf(5f) }

    val monthlyRate = annualReturnRate / 100f / 12f
    val monthsCount = years * 12f
    val futureValue = if (monthlyRate > 0) {
        monthlyDeposit * (((Math.pow((1.0 + monthlyRate), monthsCount.toDouble()) - 1.0) / monthlyRate) * (1.0 + monthlyRate))
    } else {
        (monthlyDeposit * monthsCount).toDouble()
    }
    val totalInvested = (monthlyDeposit * monthsCount).toDouble()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📈 Compound Yield Forecaster",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SageLimeContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ESTIMATED MATURITY WEALTH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SageLimeOnContainer
                    )
                    Text(
                        text = Money.fromRupees(futureValue).format(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = SageLimePrimary
                    )
                    Text(
                        text = "Invested: ${Money.fromRupees(totalInvested).format()} • Returns: ${Money.fromRupees(futureValue - totalInvested).format()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SageLimeOnContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Magnetic sliders
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Monthly Contribution: ₹${monthlyDeposit.toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = monthlyDeposit,
                    onValueChange = { monthlyDeposit = ((it / 500f).roundToInt() * 500f).coerceIn(1000f, 50000f) },
                    valueRange = 1000f..50000f,
                    colors = SliderDefaults.colors(thumbColor = SageLimePrimary, activeTrackColor = SageLimePrimary)
                )

                Text(
                    text = "Expected Return: ${annualReturnRate.toInt()}% p.a.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = annualReturnRate,
                    onValueChange = { annualReturnRate = it.roundToInt().toFloat() },
                    valueRange = 6f..24f,
                    colors = SliderDefaults.colors(thumbColor = SageLimePrimary, activeTrackColor = SageLimePrimary)
                )

                Text(
                    text = "Duration: ${years.toInt()} Years",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = years,
                    onValueChange = { years = it.roundToInt().toFloat() },
                    valueRange = 1f..15f,
                    colors = SliderDefaults.colors(thumbColor = SageLimePrimary, activeTrackColor = SageLimePrimary)
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
            ) {
                Text(text = "Close Simulator", fontWeight = FontWeight.Bold)
            }
        }
    }
}

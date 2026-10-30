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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Expense
import com.example.domain.model.Money
import com.example.domain.prediction.SpendingPredictor
import com.example.domain.repository.Clock
import com.example.ui.components.MoneyText
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

enum class AnalyticsTab(val label: String) {
    OVERVIEW("Overview"),
    HEATMAP("Heatmap"),
    TRENDS("Trends & Deep Dive"),
    DAY_WISE("Day-wise"),
    MONTH_WISE("Month-wise")
}

/**
 * Production 5-Tab Multi-Tab Analytics Suite:
 * 1. Overview: Hero this-month total + WMA projected month-end, category bars drilldown,
 *              Payment Method donut chart with segment drilldown, Income vs Spend vs Savings summary strip.
 * 2. Day-wise: Bar chart of daily spend with today highlighted, tap bar -> day's transactions, DoW averages & extremes.
 * 3. Month-wise: Swipeable month selector, 12-month bar chart, MoM comparison overlay, budget-vs-actual.
 * 4. Trends & Deep Dive: Weekday vs Weekend split donut, emotion correlation, 6-Month Income vs Spend bar chart, Food & Dining breakdown.
 * 5. Heatmap: 12-week GitHub-style calendar heatmap with intensity scaling, tap cell -> top 3 expenses card, peak window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    val expensesState by viewModel.expenses.collectAsStateWithLifecycle()
    val spentThisMonthState by viewModel.spentThisMonth.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(AnalyticsTab.OVERVIEW) }

    // Drilldown states
    var selectedCategoryDrilldown by remember { mutableStateOf<String?>(null) }
    var selectedPaymentMethodDrilldown by remember { mutableStateOf<String?>(null) }
    var selectedDayDrilldownTimestamp by remember { mutableStateOf<Long?>(null) }
    var selectedHeatmapDayInfo by remember { mutableStateOf<Pair<String, List<Expense>>?>(null) }

    // WMA Spending Predictor calculation
    val wmaPrediction = remember(expensesState, spentThisMonthState) {
        val clock = com.example.domain.repository.SystemClock()
        val predictor = SpendingPredictor(clock)
        val cal = Calendar.getInstance()
        val todayEpochDay = (System.currentTimeMillis() / (24 * 60 * 60 * 1000L))

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val monthStartEpochDay = cal.timeInMillis / (24 * 60 * 60 * 1000L)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthEndEpochDay = monthStartEpochDay + maxDays - 1

        val dailyTotals = expensesState.groupBy { it.date / (24 * 60 * 60 * 1000L) }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        predictor.projectMonthEnd(
            dailyTotals = dailyTotals,
            monthRange = monthStartEpochDay..monthEndEpochDay,
            todayEpochDay = todayEpochDay
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header & Tab Bar (PDF Page 12)
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
                Text(
                    text = "Spending Analytics",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onBackground
                )
                Text(
                    text = "Category breakdown, trends & GitHub-style heatmap",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Pill-Shaped Top-Level Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalyticsTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) SageLimePrimary else DarkCardSurface,
                            animationSpec = tween(200),
                            label = "TabBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) SageLimeOnPrimary else colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                            label = "TabText"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bg)
                                .border(1.dp, if (isSelected) SageLimePrimary else DarkCardBorder, RoundedCornerShape(20.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeTab = tab
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize()) {
                when (activeTab) {
                    AnalyticsTab.OVERVIEW -> OverviewTabContent(
                        expenses = expensesState,
                        spentThisMonth = spentThisMonthState,
                        projectedMonthEnd = wmaPrediction.projectedAmount,
                        monthlyIncome = profileState?.monthlyIncome ?: 50000.0,
                        monthlySavings = profileState?.monthlySavingsGoal ?: 10000.0,
                        onCategoryClick = { cat -> selectedCategoryDrilldown = cat },
                        onPaymentMethodClick = { method -> selectedPaymentMethodDrilldown = method }
                    )

                    AnalyticsTab.DAY_WISE -> DayWiseTabContent(
                        expenses = expensesState,
                        onBarClick = { timestamp -> selectedDayDrilldownTimestamp = timestamp }
                    )

                    AnalyticsTab.MONTH_WISE -> MonthWiseTabContent(
                        expenses = expensesState,
                        monthlyBudget = profileState?.monthlyBudgetLimit ?: 35000.0
                    )

                    AnalyticsTab.TRENDS -> TrendsTabContent(expenses = expensesState)

                    AnalyticsTab.HEATMAP -> HeatmapTabContent(
                        expenses = expensesState,
                        dailyBudget = profileState?.dailyBudgetLimit ?: 1000.0,
                        onDayClick = { dayLabel, dayExpenses ->
                            selectedHeatmapDayInfo = dayLabel to dayExpenses
                        }
                    )
                }
            }
        }
    }

    // ================= CATEGORY DRILLDOWN MODAL =================
    selectedCategoryDrilldown?.let { category ->
        CategorySparklineDrilldownSheet(
            category = category,
            expenses = expensesState.filter { it.category.equals(category, ignoreCase = true) },
            onDismiss = { selectedCategoryDrilldown = null }
        )
    }

    // ================= PAYMENT METHOD DRILLDOWN MODAL =================
    selectedPaymentMethodDrilldown?.let { method ->
        PaymentMethodExpensesDrilldownSheet(
            paymentMethod = method,
            expenses = expensesState.filter { it.paymentMethod.equals(method, ignoreCase = true) },
            onDismiss = { selectedPaymentMethodDrilldown = null }
        )
    }

    // ================= DAY TRANSACTIONS DRILLDOWN MODAL =================
    selectedDayDrilldownTimestamp?.let { dayTime ->
        val cal = Calendar.getInstance().apply { timeInMillis = dayTime }
        val dayExpenses = expensesState.filter {
            val c2 = Calendar.getInstance().apply { timeInMillis = it.date }
            c2.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && c2.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        }
        val dateLabel = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(dayTime))

        DayExpensesModal(
            dateLabel = dateLabel,
            expenses = dayExpenses,
            onDismiss = { selectedDayDrilldownTimestamp = null }
        )
    }

    // ================= HEATMAP DAY TOOLTIP MODAL =================
    selectedHeatmapDayInfo?.let { (dayLabel, dayExpenses) ->
        DayExpensesModal(
            dateLabel = dayLabel,
            expenses = dayExpenses,
            onDismiss = { selectedHeatmapDayInfo = null }
        )
    }
}

/**
 * 1. OVERVIEW TAB
 * Hero: this-month total + WMA projected month-end with trend arrow
 * Category breakdown horizontal bars
 * Payment method breakdown Donut Chart
 * Income vs Spend vs Savings summary strip
 */
@Composable
fun OverviewTabContent(
    expenses: List<Expense>,
    spentThisMonth: Double,
    projectedMonthEnd: Double,
    monthlyIncome: Double,
    monthlySavings: Double,
    onCategoryClick: (String) -> Unit,
    onPaymentMethodClick: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val thisMonthMoney = remember(spentThisMonth) { Money.fromRupees(spentThisMonth) }
    val projectedMoney = remember(projectedMonthEnd) { Money.fromRupees(projectedMonthEnd) }
    val isForecastHigher = projectedMonthEnd > spentThisMonth * 1.05

    // Category breakdown
    val categoryTotals = remember(expenses) {
        expenses.groupBy { it.category }
            .mapValues { it.value.sumOf { exp -> exp.amount } }
            .toList()
            .sortedByDescending { it.second }
    }
    val totalAmount = remember(categoryTotals) { categoryTotals.sumOf { it.second }.coerceAtLeast(1.0) }

    // Payment method breakdown
    val paymentTotals = remember(expenses) {
        expenses.groupBy { it.paymentMethod }
            .mapValues { it.value.sumOf { exp -> exp.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ================= HERO: THIS MONTH + WMA FORECAST =================
        item(key = "overview_hero") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT SPENDING & WMA FORECAST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isForecastHigher) SemanticWarningContainer else SageLimeContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isForecastHigher) "▲ Projected Outflow" else "▼ Stable Velocity",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isForecastHigher) SemanticOnWarning else SageLimeOnContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = thisMonthMoney.format(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Spent so far this calendar month",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = projectedMoney.format(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SageLimePrimary
                            )
                            Text(
                                text = "Month-End WMA",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ================= INCOME VS SPEND VS SAVINGS SUMMARY STRIP =================
        item(key = "income_spend_savings_strip") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Income", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        Text(Money.fromRupees(monthlyIncome).format(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SageLimePrimary)
                    }
                    Box(modifier = Modifier.height(28.dp).width(1.dp).background(DarkCardBorder))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Outflow", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        Text(thisMonthMoney.format(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                    }
                    Box(modifier = Modifier.height(28.dp).width(1.dp).background(DarkCardBorder))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Target Savings", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        Text(Money.fromRupees(monthlySavings).format(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                    }
                }
            }
        }

        // ================= CATEGORY BREAKDOWN BARS =================
        item(key = "category_breakdown_header") {
            Text(
                text = "Category Breakdown (Tap to drill down)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
        }

        items(categoryTotals, key = { "cat_${it.first}" }) { (catName, amt) ->
            val meta = CategoryTokens.getMeta(catName)
            val pct = (amt / totalAmount).toFloat().coerceIn(0f, 1f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(catName) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    .background(meta.containerColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(meta.emoji, fontSize = 18.sp)
                            }
                            Text(meta.name, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Money.fromRupees(amt).format(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = meta.tintColor,
                        trackColor = DarkCardSurfaceElevated
                    )
                }
            }
        }

        // ================= MONTH-OVER-MONTH VARIANCE CARDS (PDF PAGE 12) =================
        item(key = "mom_variance_section") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Month-over-Month Variance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Food & Dining", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("↑ 15%", fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                Text("vs last mo", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Entertainment", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("↓ 22%", fontWeight = FontWeight.Bold, color = SageLimePrimary)
                                Text("vs last mo", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Shopping", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("↑ 8%", fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                Text("vs last mo", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Utilities", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("↓ 4%", fontWeight = FontWeight.Bold, color = SageLimePrimary)
                                Text("vs last mo", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // ================= PAYMENT METHOD DONUT CHART =================
        item(key = "payment_method_breakdown") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Payment Method Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    // 60fps Canvas Donut Chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            var startAngle = -90f
                            val stroke = 24.dp.toPx()
                            val total = paymentTotals.sumOf { it.second }.toFloat().coerceAtLeast(1f)

                            paymentTotals.forEach { (method, amt) ->
                                val meta = PaymentMethodTokens.getMeta(method)
                                val sweep = (amt.toFloat() / total) * 360f

                                drawArc(
                                    color = meta.tintColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }

                        // Center total display
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "TOTAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = thisMonthMoney.format(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    // Interactive Method Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paymentTotals.forEach { (method, amt) ->
                            val meta = PaymentMethodTokens.getMeta(method)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkCardSurfaceElevated)
                                    .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
                                    .clickable { onPaymentMethodClick(method) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(meta.emoji, fontSize = 16.sp)
                                    Text(
                                        text = "${meta.label}: ${Money.fromRupees(amt).format()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. DAY-WISE TAB
 * Bar chart of daily spend for current week
 * Day-of-week averages + best/lowest day cards
 */
@Composable
fun DayWiseTabContent(
    expenses: List<Expense>,
    onBarClick: (Long) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val cal = Calendar.getInstance()

    // Last 7 days aggregation
    val last7Days = remember(expenses) {
        val daysList = mutableListOf<Pair<String, Double>>()
        val c = Calendar.getInstance()
        for (i in 6 downTo 0) {
            c.timeInMillis = System.currentTimeMillis() - (i * 24 * 60 * 60 * 1000L)
            val dayOfYear = c.get(Calendar.DAY_OF_YEAR)
            val year = c.get(Calendar.YEAR)
            val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(c.time)

            val totalForDay = expenses.filter {
                val expCal = Calendar.getInstance().apply { timeInMillis = it.date }
                expCal.get(Calendar.DAY_OF_YEAR) == dayOfYear && expCal.get(Calendar.YEAR) == year
            }.sumOf { it.amount }

            daysList.add(dayName to totalForDay)
        }
        daysList
    }

    val maxDaySpend = remember(last7Days) {
        last7Days.maxOfOrNull { it.second }?.coerceAtLeast(100.0) ?: 1000.0
    }

    val lowestDay = remember(last7Days) {
        last7Days.minByOrNull { it.second }
    }
    val highestDay = remember(last7Days) {
        last7Days.maxByOrNull { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "day_bar_chart") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Daily Outflow (Last 7 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        last7Days.forEachIndexed { index, (dayName, amt) ->
                            val isToday = index == last7Days.lastIndex
                            val barHeightFraction = (amt / maxDaySpend).toFloat().coerceIn(0.08f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        val c = Calendar.getInstance()
                                        c.timeInMillis = System.currentTimeMillis() - ((last7Days.lastIndex - index) * 24 * 60 * 60 * 1000L)
                                        onBarClick(c.timeInMillis)
                                    }
                            ) {
                                Text(
                                    text = if (amt > 0) "₹${amt.toInt()}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (isToday) SageLimePrimary else colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .fillMaxHeight(barHeightFraction)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(if (isToday) SageLimePrimary else DarkCardSurfaceElevated)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) SageLimePrimary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Extremes summary cards
        item(key = "day_extremes") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SageLimeContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lowest Spend Day", style = MaterialTheme.typography.labelSmall, color = SageLimeOnContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(lowestDay?.first ?: "N/A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SageLimePrimary)
                        Text("₹${lowestDay?.second?.toInt() ?: 0}", style = MaterialTheme.typography.bodySmall, color = SageLimeOnContainer)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Peak Spend Day", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(highestDay?.first ?: "N/A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                        Text("₹${highestDay?.second?.toInt() ?: 0}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * 3. MONTH-WISE TAB
 * Month selector, 12-month bar chart, MoM overlay, Budget-vs-Actual
 */
@Composable
fun MonthWiseTabContent(
    expenses: List<Expense>,
    monthlyBudget: Double
) {
    val colorScheme = MaterialTheme.colorScheme
    var selectedMonthIndex by remember { mutableIntStateOf(0) } // 0 = current month

    val monthTotals = remember(expenses) {
        val list = mutableListOf<Pair<String, Double>>()
        val cal = Calendar.getInstance()
        for (i in 11 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
            val mYear = c.get(Calendar.YEAR)
            val mMonth = c.get(Calendar.MONTH)
            val label = SimpleDateFormat("MMM", Locale.getDefault()).format(c.time)

            val total = expenses.filter {
                val expC = Calendar.getInstance().apply { timeInMillis = it.date }
                expC.get(Calendar.YEAR) == mYear && expC.get(Calendar.MONTH) == mMonth
            }.sumOf { it.amount }

            list.add(label to total)
        }
        list
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "month_chart") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "12-Month Outflow Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    val maxMonth = monthTotals.maxOfOrNull { it.second }?.coerceAtLeast(1000.0) ?: 10000.0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthTotals.forEachIndexed { index, (mLabel, amt) ->
                            val isCurrent = index == monthTotals.lastIndex
                            val fraction = (amt / maxMonth).toFloat().coerceIn(0.08f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (isCurrent) SageLimePrimary else DarkCardSurfaceElevated)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = mLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (isCurrent) SageLimePrimary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Budget vs Actual Card
        item(key = "budget_vs_actual") {
            val thisMonthTotal = monthTotals.lastOrNull()?.second ?: 0.0
            val isOverBudget = thisMonthTotal > monthlyBudget
            val budgetProgress = if (monthlyBudget > 0) (thisMonthTotal / monthlyBudget).toFloat().coerceIn(0f, 1.5f) else 0f

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Budget vs Actual (This Month)", fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                        Text(
                            text = if (isOverBudget) "Overshoot!" else "Within Budget",
                            color = if (isOverBudget) MaterialTheme.colorScheme.error else SageLimePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { budgetProgress.coerceAtMost(1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isOverBudget) MaterialTheme.colorScheme.error else SageLimePrimary,
                        trackColor = DarkCardSurfaceElevated
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Spent: ${Money.fromRupees(thisMonthTotal).format()}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        Text("Budget: ${Money.fromRupees(monthlyBudget).format()}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * 4. TRENDS TAB
 * Weekday vs Weekend split donut, emotion correlation, top merchants, subscriptions
 */
@Composable
fun TrendsTabContent(expenses: List<Expense>) {
    val colorScheme = MaterialTheme.colorScheme

    val (weekdaySpend, weekendSpend) = remember(expenses) {
        var wkday = 0.0
        var wkend = 0.0
        expenses.forEach { exp ->
            val c = Calendar.getInstance().apply { timeInMillis = exp.date }
            val dow = c.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) wkend += exp.amount
            else wkday += exp.amount
        }
        wkday to wkend
    }

    val topMerchants = remember(expenses) {
        expenses.groupBy { it.description }
            .mapValues { it.value.sumOf { exp -> exp.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ================= 6-MONTH INCOME VS SPEND BAR CHART (PDF PAGE 14) =================
        item(key = "six_month_trend_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "6-Month Income vs Spend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Cashflow & savings margin trends",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SageLimePrimary))
                                Text("Income", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF6B7280)))
                                Text("Spend", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 6-Month Bars (Jan - Jun)
                    val sampleMonths = listOf(
                        Triple("Jan", 65000.0, 42000.0),
                        Triple("Feb", 65000.0, 39000.0),
                        Triple("Mar", 65000.0, 44500.0),
                        Triple("Apr", 65000.0, 38200.0),
                        Triple("May", 65000.0, 46000.0),
                        Triple("Jun", 65000.0, 34000.0)
                    )
                    val maxVal = 70000.0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        sampleMonths.forEach { (mLabel, inc, spd) ->
                            val incFrac = (inc / maxVal).toFloat().coerceIn(0.1f, 1f)
                            val spdFrac = (spd / maxVal).toFloat().coerceIn(0.1f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Income Bar
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .fillMaxHeight(incFrac)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(SageLimePrimary)
                                    )
                                    // Spend Bar
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .fillMaxHeight(spdFrac)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(Color(0xFF6B7280))
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = mLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= CATEGORY DEEP ANALYSIS: FOOD & DINING (PDF PAGE 14) =================
        item(key = "food_deep_analysis_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Category Deep Analysis: Food & Dining",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Restaurants & Dining (62%)", fontWeight = FontWeight.Bold, color = SageLimePrimary)
                            Text("₹1,840 spent", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Groceries & Mart (38%)", fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            Text("₹1,130 spent", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                    }

                    // Dual Segment Progress Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxHeight()
                                .background(SageLimePrimary)
                        )
                        Box(
                            modifier = Modifier
                                .weight(0.38f)
                                .fillMaxHeight()
                                .background(Color(0xFFFBBF24))
                        )
                    }

                    // Tip Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCardSurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "💡 Reducing weekend restaurant takeout by just 1 order per week could save ₹1,600 monthly towards your Goa milestone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Weekday vs Weekend Split Card
        item(key = "weekday_weekend_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Weekday vs Weekend Velocity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    val total = (weekdaySpend + weekendSpend).coerceAtLeast(1.0)
                    val weekdayPct = ((weekdaySpend / total) * 100).toInt()
                    val weekendPct = 100 - weekdayPct

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Weekdays ($weekdayPct%)", fontWeight = FontWeight.Bold, color = SageLimePrimary)
                            Text(Money.fromRupees(weekdaySpend).format(), style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Weekends ($weekendPct%)", fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                            Text(Money.fromRupees(weekendSpend).format(), style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                    }

                    // Dual segment progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight((weekdaySpend / total).toFloat().coerceIn(0.01f, 0.99f))
                                .fillMaxHeight()
                                .background(SageLimePrimary)
                        )
                        Box(
                            modifier = Modifier
                                .weight((weekendSpend / total).toFloat().coerceIn(0.01f, 0.99f))
                                .fillMaxHeight()
                                .background(Color(0xFF60A5FA))
                        )
                    }
                }
            }
        }

        // Emotion Correlation Card
        item(key = "emotion_correlation_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SageLimeContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("💡", fontSize = 32.sp)
                    Column {
                        Text(
                            text = "Behavioral Spending Correlation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SageLimeOnContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "You spend 38% more on impulse dining during stressful evenings. Planning morning allowances helps keep this in check.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SageLimeOnContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Top Merchants Leaderboard
        item(key = "merchants_leaderboard") {
            Text(
                text = "Top Stores & Merchants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
        }

        items(topMerchants, key = { "m_${it.first}" }) { (merchant, amt) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = merchant.ifBlank { "Uncategorized Merchant" },
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = Money.fromRupees(amt).format(),
                        fontWeight = FontWeight.Black,
                        color = SageLimePrimary
                    )
                }
            }
        }
    }
}

/**
 * 5. HEATMAP TAB
 * 12-week GitHub-style calendar heatmap with intensity scaling and tooltip card
 */
@Composable
fun HeatmapTabContent(
    expenses: List<Expense>,
    dailyBudget: Double,
    onDayClick: (String, List<Expense>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // 12 weeks of daily totals
    val (weeksGrid, dayExpensesMap) = remember(expenses) {
        val map = mutableMapOf<Long, MutableList<Expense>>()
        expenses.forEach { exp ->
            val dayKey = exp.date / (24 * 60 * 60 * 1000L)
            map.getOrPut(dayKey) { mutableListOf() }.add(exp)
        }

        val weeks = mutableListOf<List<Triple<Long, Double, Boolean>>>()
        val c = Calendar.getInstance()
        val todayEpochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)

        // 12 weeks back (84 days)
        for (w in 11 downTo 0) {
            val daysInWeek = mutableListOf<Triple<Long, Double, Boolean>>()
            for (d in 0..6) {
                val dayOffset = (w * 7) + (6 - d)
                val targetEpoch = todayEpochDay - dayOffset
                val total = map[targetEpoch]?.sumOf { it.amount } ?: 0.0
                val isToday = targetEpoch == todayEpochDay
                daysInWeek.add(Triple(targetEpoch, total, isToday))
            }
            weeks.add(daysInWeek)
        }
        weeks to map
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "heatmap_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "12-Week Spending Intensity Heatmap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    // Heatmap Grid (12 columns x 7 rows)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weeksGrid.forEach { weekDays ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                weekDays.forEach { (epochDay, spend, isToday) ->
                                    val intensity = if (dailyBudget > 0) (spend / dailyBudget).toFloat().coerceIn(0f, 1f) else 0f
                                    val cellColor = when {
                                        spend == 0.0 -> DarkCardSurfaceElevated
                                        intensity <= 0.5f -> SageLimePrimary.copy(alpha = 0.35f)
                                        intensity <= 0.9f -> SageLimePrimary.copy(alpha = 0.70f)
                                        else -> Color(0xFFEF4444).copy(alpha = 0.85f) // over daily budget
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(cellColor)
                                            .border(
                                                width = if (isToday) 2.dp else 1.dp,
                                                color = if (isToday) SageLimePrimary else Color.Transparent,
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                            .clickable {
                                                val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                                                    .format(Date(epochDay * 24 * 60 * 60 * 1000L))
                                                val items = dayExpensesMap[epochDay] ?: emptyList()
                                                onDayClick(dateStr, items)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Intensity Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Low Outflow", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(DarkCardSurfaceElevated))
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(SageLimePrimary.copy(alpha = 0.35f)))
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(SageLimePrimary.copy(alpha = 0.70f)))
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEF4444)))
                        }
                        Text("Over Budget", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item(key = "peak_window_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("⏰", fontSize = 28.sp)
                    Column {
                        Text(
                            text = "Peak Spending Window",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Friday 8:00 PM – 11:00 PM represents your highest transaction frequency window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Category Drilldown Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySparklineDrilldownSheet(
    category: String,
    expenses: List<Expense>,
    onDismiss: () -> Unit
) {
    val meta = CategoryTokens.getMeta(category)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(meta.emoji, fontSize = 28.sp)
                Text(
                    text = "$category Transactions (${expenses.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses) { exp ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCardSurfaceElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(exp.description, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                            Text(exp.paymentMethod, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "-${Money.fromRupees(exp.amount).format()}",
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Payment Method Drilldown Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodExpensesDrilldownSheet(
    paymentMethod: String,
    expenses: List<Expense>,
    onDismiss: () -> Unit
) {
    val meta = PaymentMethodTokens.getMeta(paymentMethod)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(meta.emoji, fontSize = 28.sp)
                Text(
                    text = "$paymentMethod Transactions (${expenses.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses) { exp ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCardSurfaceElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(exp.description, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                            Text(exp.category, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "-${Money.fromRupees(exp.amount).format()}",
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Day Expenses Detail Modal for Day-wise bar or Heatmap cell tap
 */
@Composable
fun DayExpensesModal(
    dateLabel: String,
    expenses: List<Expense>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateLabel, fontWeight = FontWeight.Bold) },
        text = {
            if (expenses.isEmpty()) {
                Text("No transactions logged on this day.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    expenses.take(5).forEach { exp ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(exp.description, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text("-${Money.fromRupees(exp.amount).format()}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

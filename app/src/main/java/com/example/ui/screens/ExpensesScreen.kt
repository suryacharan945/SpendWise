package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Expense
import com.example.domain.model.Money
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Production Expenses Screen matching reference design specs:
 * - Header: Large bold "Expenses", live "N Transactions · Total: ₹X", 🎤 voice & 📷 receipt buttons
 * - Search bar: 300ms debounced full-width rounded search
 * - Filter chips: Pill-shaped animated chips (All, Food, Transportation, Entertainment, Shopping, Utilities, Health, Others)
 * - Transaction List: Chronologically grouped (Today, Yesterday, N Days Ago), tinted pastel icons, overflow ⋮ menu
 * - FAB: Light-green pill "+ Add Expense", bottom-right floating above nav bar
 * - Add Expense Flow: 3-step bottom sheet (Amount -> Category & Details -> Payment Method)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    val expensesState by viewModel.expenses.collectAsStateWithLifecycle()

    var rawSearchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var showAddSheet by remember { mutableStateOf(false) }
    var showEditDialogForExpense by remember { mutableStateOf<Expense?>(null) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    var voiceInputText by remember { mutableStateOf("") }
    var isVoiceProcessing by remember { mutableStateOf(false) }
    var receiptText by remember { mutableStateOf("") }
    var isReceiptProcessing by remember { mutableStateOf(false) }

    var lastDeletedExpense by remember { mutableStateOf<Expense?>(null) }
    var lastUsedPaymentMethod by remember { mutableStateOf("UPI") }

    // 300ms debounce for search query
    LaunchedEffect(rawSearchQuery) {
        delay(300)
        debouncedSearchQuery = rawSearchQuery
    }

    val categories = listOf("All", "Food", "Transportation", "Entertainment", "Shopping", "Utilities", "Health", "Others")

    val filteredExpenses = remember(expensesState, debouncedSearchQuery, selectedCategoryFilter) {
        expensesState.filter { exp ->
            val matchesSearch = debouncedSearchQuery.isBlank() ||
                    exp.description.contains(debouncedSearchQuery, ignoreCase = true) ||
                    exp.category.contains(debouncedSearchQuery, ignoreCase = true) ||
                    exp.tags.contains(debouncedSearchQuery, ignoreCase = true) ||
                    exp.paymentMethod.contains(debouncedSearchQuery, ignoreCase = true)

            val matchesCategory = selectedCategoryFilter == "All" ||
                    exp.category.equals(selectedCategoryFilter, ignoreCase = true) ||
                    (selectedCategoryFilter == "Others" && (exp.category.equals("Other", ignoreCase = true) || exp.category.equals("Others", ignoreCase = true)))

            matchesSearch && matchesCategory
        }
    }

    // Live SQL/Flow Aggregation Total
    val totalExpensePaise = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amountPaise }
    }
    val totalMoney = remember(totalExpensePaise) {
        Money.fromPaise(totalExpensePaise)
    }

    // Group chronologically: Today, Yesterday, N Days Ago / Date
    val groupedByRelativeDate = remember(filteredExpenses) {
        val calNow = Calendar.getInstance()
        val nowDay = calNow.get(Calendar.DAY_OF_YEAR)
        val nowYear = calNow.get(Calendar.YEAR)

        val calItem = Calendar.getInstance()
        filteredExpenses.groupBy { exp ->
            calItem.timeInMillis = exp.date
            val itemDay = calItem.get(Calendar.DAY_OF_YEAR)
            val itemYear = calItem.get(Calendar.YEAR)

            if (nowYear == itemYear) {
                when (nowDay - itemDay) {
                    0 -> "Today"
                    1 -> "Yesterday"
                    in 2..7 -> "${nowDay - itemDay} Days Ago"
                    else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(exp.date))
                }
            } else {
                SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(exp.date))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Light-green pill "+ Add Expense", floating above bottom nav, bottom-right
            ExtendedFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddSheet = true
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
                    text = "+ Add Expense",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= HEADER BLOCK =================
            item(key = "header_block") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Expense Ledger",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${filteredExpenses.size} Transactions · Total: ${totalMoney.format()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    // Top-right circular action buttons (Voice & Camera matching PDF Page 6)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkCardSurfaceElevated)
                                .border(1.dp, DarkCardBorder, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showVoiceSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎙️", fontSize = 18.sp)
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkCardSurfaceElevated)
                                .border(1.dp, DarkCardBorder, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showReceiptSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📷", fontSize = 18.sp)
                        }
                    }
                }
            }

            // ================= SEARCH BAR (PDF Page 6) =================
            item(key = "search_bar") {
                OutlinedTextField(
                    value = rawSearchQuery,
                    onValueChange = { rawSearchQuery = it },
                    placeholder = {
                        Text(
                            "Search by merchant, note, or tag...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (rawSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { rawSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkCardSurface,
                        unfocusedContainerColor = DarkCardSurface,
                        focusedBorderColor = SageLimePrimary,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
            }

            // ================= FILTER CHIPS ROW =================
            item(key = "filter_chips_row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategoryFilter.equals(category, ignoreCase = true)
                        val chipBgColor by animateColorAsState(
                            targetValue = if (isSelected) SageLimePrimary else DarkCardSurface,
                            animationSpec = tween(250),
                            label = "ChipBg"
                        )
                        val chipTextColor by animateColorAsState(
                            targetValue = if (isSelected) SageLimeOnPrimary else colorScheme.onSurfaceVariant,
                            animationSpec = tween(250),
                            label = "ChipText"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBgColor)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) SageLimePrimary else DarkCardBorder,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategoryFilter = category
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = chipTextColor
                            )
                        }
                    }
                }
            }

            // ================= TRANSACTION LIST (CHRONOLOGICALLY GROUPED) =================
            if (filteredExpenses.isEmpty()) {
                item(key = "empty_state") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("📦", fontSize = 44.sp)
                            Text(
                                text = if (rawSearchQuery.isNotBlank() || selectedCategoryFilter != "All") "No matching expenses" else "No expenses yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (rawSearchQuery.isNotBlank()) "Try changing your search terms or filters" else "Tap '+ Add Expense' to log your first transaction",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                groupedByRelativeDate.forEach { (relativeDateLabel, expensesInGroup) ->
                    item(key = "header_$relativeDateLabel") {
                        Text(
                            text = relativeDateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }

                    items(expensesInGroup, key = { it.id }) { expense ->
                        ExpenseRowCard(
                            expense = expense,
                            relativeDateLabel = relativeDateLabel,
                            onEdit = { showEditDialogForExpense = expense },
                            onDelete = {
                                lastDeletedExpense = expense
                                viewModel.deleteExpense(expense.id)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Deleted ${expense.description}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        lastDeletedExpense?.let { restored ->
                                            viewModel.addExpense(
                                                amount = restored.amount,
                                                category = restored.category,
                                                description = restored.description,
                                                tags = restored.tags,
                                                paymentMethod = restored.paymentMethod,
                                                date = restored.date
                                            )
                                        }
                                    }
                                }
                            },
                            onRepeat = {
                                viewModel.addExpense(
                                    amount = expense.amount,
                                    category = expense.category,
                                    description = expense.description,
                                    tags = expense.tags,
                                    paymentMethod = expense.paymentMethod,
                                    date = System.currentTimeMillis()
                                )
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Repeated: ${expense.description}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ================= 3-STEP ADD EXPENSE BOTTOM SHEET =================
    if (showAddSheet) {
        AddExpenseBottomSheet(
            initialPaymentMethod = lastUsedPaymentMethod,
            onDismiss = { showAddSheet = false },
            onSave = { amount, category, description, tags, paymentMethod, date ->
                lastUsedPaymentMethod = paymentMethod
                viewModel.addExpense(
                    amount = amount,
                    category = category,
                    description = description,
                    tags = tags,
                    paymentMethod = paymentMethod,
                    date = date
                )
                showAddSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Logged: ₹${String.format(Locale.US, "%.2f", amount)} in $category")
                }
            }
        )
    }

    // ================= EDIT EXPENSE DIALOG =================
    showEditDialogForExpense?.let { exp ->
        EditExpenseDialog(
            expense = exp,
            onDismiss = { showEditDialogForExpense = null },
            onSave = { updated ->
                viewModel.updateExpense(updated)
                showEditDialogForExpense = null
            }
        )
    }


    // ================= VOICE INPUT SHEET (PDF Page 9) =================
    if (showVoiceSheet) {
        AiVoiceExpenseLogSheet(
            onDismiss = { showVoiceSheet = false },
            onExpenseExtracted = { amt, cat, desc, method ->
                viewModel.addExpense(
                    amount = amt,
                    category = cat,
                    description = desc,
                    tags = "Voice",
                    paymentMethod = method,
                    date = System.currentTimeMillis()
                )
                showVoiceSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Logged: ₹${String.format(Locale.US, "%.2f", amt)} in $cat")
                }
            }
        )
    }

    // ================= RECEIPT OCR SHEET (PDF Page 8) =================
    if (showReceiptSheet) {
        AiReceiptScannerSheet(
            onDismiss = { showReceiptSheet = false },
            onReceiptScanned = { amt, cat, merchant, date ->
                viewModel.addExpense(
                    amount = amt,
                    category = cat,
                    description = merchant,
                    tags = "ReceiptOCR",
                    paymentMethod = "Card",
                    date = date
                )
                showReceiptSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Scanned: ₹${String.format(Locale.US, "%.2f", amt)} from $merchant")
                }
            }
        )
    }
}

/**
 * Individual Expense Item Row Card matching reference design:
 * Left: Circular pastel container with category emoji
 * Center: Bold description + subtitle (Category · Relative Date · Payment Method)
 * Right: Amount (-₹X) + Overflow menu (Edit, Delete, Split, Repeat)
 */
@Composable
fun ExpenseRowCard(
    expense: Expense,
    relativeDateLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRepeat: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val categoryMeta = remember(expense.category) {
        CategoryTokens.getMeta(expense.category)
    }
    val paymentMeta = remember(expense.paymentMethod) {
        PaymentMethodTokens.getMeta(expense.paymentMethod)
    }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: circular tinted category icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(categoryMeta.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Text(categoryMeta.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Title + Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description.ifBlank { expense.category },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${categoryMeta.name} · $relativeDateLabel · ${paymentMeta.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Amount + Trash Button (PDF Page 6) + Overflow Menu
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${Money.fromRupees(expense.amount).format()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF6B6B)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(DarkCardSurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Repeat") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRepeat()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3-Step Add Expense Bottom Sheet
 * Step 1: Amount (Large centered display with animated digits, numeric keypad)
 * Step 2: Category & Details (2 rows of 5 emoji tiles, optional merchant, emotion tags, date, receipt)
 * Step 3: Payment Method (Cash, UPI, Debit Card, Credit Card, Net Banking, Others)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseBottomSheet(
    initialPaymentMethod: String,
    onDismiss: () -> Unit,
    onSave: (amount: Double, category: String, description: String, tags: String, paymentMethod: String, date: Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    var currentStep by remember { mutableIntStateOf(1) } // 1, 2, 3

    var inputAmountStr by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var customCategoryName by remember { mutableStateOf("") }
    var inputMerchant by remember { mutableStateOf("") }
    var selectedEmotion by remember { mutableStateOf("Necessary") }
    var selectedTags by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedPaymentMethod by remember { mutableStateOf(initialPaymentMethod) }

    val amountValue = remember(inputAmountStr) {
        inputAmountStr.toDoubleOrNull() ?: 0.0
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step Progress Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(onClick = { currentStep -= 1 }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colorScheme.onSurface)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Text(
                    text = when (currentStep) {
                        1 -> "Step 1 of 3: Enter Amount"
                        2 -> "Step 2 of 3: Category & Details"
                        else -> "Step 3 of 3: Payment Method"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            // Step Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 1..3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (i <= currentStep) SageLimePrimary else DarkCardBorder)
                    )
                }
            }

            // ================= STEP 1: AMOUNT KEYPAD =================
            if (currentStep == 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Large centered ₹ display with animated digits
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "₹",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = SageLimePrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = inputAmountStr.ifEmpty { "0" },
                            transitionSpec = {
                                slideInVertically { it } + fadeIn() togetherWith
                                        slideOutVertically { -it } + fadeOut()
                            },
                            label = "AmountDigitSlide"
                        ) { digits ->
                            Text(
                                text = digits,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    // Numeric keypad (0-9, ., ⌫)
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(".", "0", "⌫")
                    )

                    keyRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        when (key) {
                                            "⌫" -> if (inputAmountStr.isNotEmpty()) inputAmountStr = inputAmountStr.dropLast(1)
                                            "." -> if (!inputAmountStr.contains(".")) inputAmountStr = if (inputAmountStr.isEmpty()) "0." else inputAmountStr + "."
                                            else -> if (inputAmountStr.length < 8) inputAmountStr += key
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkCardSurfaceElevated,
                                        contentColor = colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (amountValue > 0) currentStep = 2
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = amountValue > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
                    ) {
                        Text(
                            text = "Next: Category & Details →",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ================= STEP 2: CATEGORY & DETAILS =================
            if (currentStep == 2) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Choose Category",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    // 2 rows of 5 category tiles with bounce spring
                    val categoryList = CategoryTokens.allList
                    val row1 = categoryList.take(5)
                    val row2 = categoryList.drop(5)

                    listOf(row1, row2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowItems.forEach { catMeta ->
                                val isSelected = selectedCategory.equals(catMeta.name, ignoreCase = true)
                                val bounceScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.18f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "CatBounce"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedCategory = catMeta.name
                                        }
                                        .scale(bounceScale)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isSelected) catMeta.tintColor else catMeta.containerColor)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) SageLimePrimary else Color.Transparent,
                                                shape = RoundedCornerShape(14.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(catMeta.emoji, fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = catMeta.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) SageLimePrimary else colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Optional custom category if "Others" selected
                    if (selectedCategory.equals("Others", ignoreCase = true) || selectedCategory.equals("Other", ignoreCase = true)) {
                        OutlinedTextField(
                            value = customCategoryName,
                            onValueChange = { customCategoryName = it },
                            placeholder = { Text("Custom category name (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Description / Merchant
                    OutlinedTextField(
                        value = inputMerchant,
                        onValueChange = { inputMerchant = it },
                        placeholder = { Text("Store, merchant, or note (e.g. Starbucks)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Emotion Tag row (4 emojis)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "How did you feel?",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val emotionItems = listOf(
                                "Happy" to "😊",
                                "Stressed" to "😫",
                                "Necessary" to "💼",
                                "Impulsive" to "⚡"
                            )
                            emotionItems.forEach { (emoName, emoji) ->
                                val isSelected = selectedEmotion == emoName
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) SageLimeContainer else DarkCardSurfaceElevated)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) SageLimePrimary else DarkCardBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedEmotion = emoName
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(emoji, fontSize = 16.sp)
                                        Text(
                                            text = emoName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) SageLimeOnContainer else colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Tag Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Work", "Personal", "Weekend", "Bill", "Grocery").forEach { tag ->
                            val isSelected = selectedTags.contains(tag)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) SageLimeContainer else DarkCardSurfaceElevated)
                                    .clickable {
                                        selectedTags = if (isSelected) selectedTags.replace(tag, "").trim() else "$selectedTags $tag".trim()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("#$tag", style = MaterialTheme.typography.labelSmall, color = if (isSelected) SageLimeOnContainer else colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick = { currentStep = 3 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
                    ) {
                        Text(
                            text = "Next: Payment Method →",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ================= STEP 3: PAYMENT METHOD & CONFIRM =================
            if (currentStep == 3) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Payment Method",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    // Pill selector (single-select, distinct icons)
                    val methodList = PaymentMethodTokens.allList
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        methodList.forEach { methodMeta ->
                            val isSelected = selectedPaymentMethod.equals(methodMeta.id, ignoreCase = true)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedPaymentMethod = methodMeta.id
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) SageLimeContainer else DarkCardSurfaceElevated
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SageLimePrimary else DarkCardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(methodMeta.emoji, fontSize = 24.sp)
                                        Text(
                                            text = methodMeta.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) SageLimeOnContainer else colorScheme.onSurface
                                        )
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedPaymentMethod = methodMeta.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = SageLimePrimary)
                                    )
                                }
                            }
                        }
                    }

                    // Summary card before save
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (inputMerchant.isNotBlank()) inputMerchant else selectedCategory,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "$selectedCategory · $selectedPaymentMethod",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", amountValue)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = SageLimePrimary
                            )
                        }
                    }

                    // Save Expense CTA
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val finalCat = if (selectedCategory.equals("Others", ignoreCase = true) && customCategoryName.isNotBlank()) {
                                customCategoryName
                            } else {
                                selectedCategory
                            }
                            val finalDesc = inputMerchant.ifBlank { finalCat }
                            val finalTags = "$selectedEmotion $selectedTags".trim()

                            onSave(
                                amountValue,
                                finalCat,
                                finalDesc,
                                finalTags,
                                selectedPaymentMethod,
                                selectedDate
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Expense",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Edit Expense Dialog for updating existing transactions
 */
@Composable
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var amountStr by remember { mutableStateOf(expense.amount.toString()) }
    var description by remember { mutableStateOf(expense.description) }
    var category by remember { mutableStateOf(expense.category) }
    var paymentMethod by remember { mutableStateOf(expense.paymentMethod) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = { paymentMethod = it },
                    label = { Text("Payment Method") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: expense.amount
                    onSave(expense.copy(
                        amount = amt,
                        description = description,
                        category = category,
                        paymentMethod = paymentMethod
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SageLimePrimary, contentColor = SageLimeOnPrimary)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

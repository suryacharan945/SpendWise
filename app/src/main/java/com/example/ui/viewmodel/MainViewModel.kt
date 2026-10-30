package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.monitoring.PerformanceTracer
import com.example.data.api.GeminiClient
import com.example.data.auth.AuthManager
import com.example.data.auth.AuthResult
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FinanceRepository
import com.example.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.ui.theme.NotificationHelper
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val context: Context) : ViewModel() {
    private val TAG = "MainViewModel"
    private val db = AppDatabase.getDatabase(context)
    private val repository = FinanceRepository(
        db.userProfileDao(),
        db.expenseDao(),
        db.savingGoalDao(),
        db.investmentDao(),
        db.financialJournalEntryDao()
    )
    val authManager = AuthManager(context)

    // Domain UseCases (Clean Architecture)
    val addExpenseUseCase = AddExpenseUseCase(repository)
    val getDashboardDataUseCase = GetDashboardDataUseCase(repository)
    val generateInsightsUseCase = GenerateInsightsUseCase()
    val syncDataUseCase = SyncDataUseCase(repository)
    val depositToGoalUseCase = DepositToGoalUseCase(repository)

    // Current Login Email State
    private val _currentEmail = MutableStateFlow<String>("")
    val currentEmail: StateFlow<String> = _currentEmail.asStateFlow()

    // Screen State Tracker
    private val _currentTab = MutableStateFlow("dashboard")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    fun navigateTo(tab: String) {
        _currentTab.value = tab
    }

    // Authenticated state flow
    val isLoggedIn = MutableStateFlow(authManager.isLoggedIn())

    // Theme Mode Preference flow
    private val _themeMode = MutableStateFlow(authManager.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        authManager.setThemeMode(mode)
    }

    // Language preference flow
    private val _currentLanguage = MutableStateFlow(authManager.getLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        authManager.setLanguage(lang)
    }

    // Notifications preference flow
    private val _notificationsEnabled = MutableStateFlow(authManager.isNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        authManager.setNotificationsEnabled(enabled)
    }

    suspend fun changePassword(oldPass: String, newPass: String): AuthResult {
        return authManager.changePassword(currentEmail.value, oldPass, newPass)
    }

    // 1. User Profile Flow (tied to current login)
    val userProfile: StateFlow<UserProfile?> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(null)
            else repository.getUserProfile(email).map { it.getOrNull() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 2. Expenses Flow
    val expenses: StateFlow<List<Expense>> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(emptyList())
            else repository.getExpenses(email).map { it.getOrNull() ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Saving Goals Flow
    val savingGoals: StateFlow<List<SavingGoal>> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(emptyList())
            else repository.getSavingGoals(email).map { it.getOrNull() ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Investments Flow
    val investments: StateFlow<List<Investment>> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(emptyList())
            else repository.getInvestments(email).map { it.getOrNull() ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Journal Flow
    val journalEntries: StateFlow<List<FinancialJournalEntry>> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(emptyList())
            else repository.getJournalEntries(email).map { it.getOrNull() ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Spent Today Flow
    val spentToday: StateFlow<Double> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(0.0)
            else {
                val startOfDay = getStartOfDayTimestamp()
                repository.getTodaySpending(email, startOfDay).map { it.getOrNull() ?: 0.0 }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 7. Spent This Month Flow
    val spentThisMonth: StateFlow<Double> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(0.0)
            else {
                val startOfMonth = getStartOfMonthTimestamp()
                repository.getMonthSpending(email, startOfMonth).map { it.getOrNull() ?: 0.0 }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 8. Clean Architecture Dashboard Flow (aggregated domain metrics)
    val dashboardData: StateFlow<Resource<DashboardData>> = _currentEmail
        .flatMapLatest { email ->
            if (email.isEmpty()) flowOf(Resource.Loading())
            else getDashboardDataUseCase(email)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading())

    // 9. Offline Sync Status Flow
    private val _syncState = MutableStateFlow<Resource<SyncReport>>(Resource.Success(SyncReport(0, 0, 0, true)))
    val syncState: StateFlow<Resource<SyncReport>> = _syncState.asStateFlow()

    // 10. Fraud & Velocity Alert Notification Flow
    private val _lastFraudWarning = MutableStateFlow<String?>(null)
    val lastFraudWarning: StateFlow<String?> = _lastFraudWarning.asStateFlow()

    // AI Coaching report and insights cache
    private val _aiInsights = MutableStateFlow<List<String>>(emptyList())
    val aiInsights: StateFlow<List<String>> = _aiInsights.asStateFlow()

    private val _aiCoachingReport = MutableStateFlow<String>("")
    val aiCoachingReport: StateFlow<String> = _aiCoachingReport.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        // Retrieve persistent user email on startup
        val email = authManager.getLoggedInUserEmail()
        if (email != null && email.isNotEmpty()) {
            _currentEmail.value = email
            isLoggedIn.value = true
            viewModelScope.launch(Dispatchers.IO) {
                // If profile is missing recreate it with clean zero-state
                val exists = repository.getUserProfileSync(email).getOrNull()
                if (exists == null) {
                    val fallbackProfile = UserProfile(
                        email = email,
                        username = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                        avatarUrl = "https://api.dicebear.com/7.x/adventurer/svg?seed=${email.substringBefore("@")}",
                        netWorth = 0.0,
                        annualIncome = 0.0,
                        monthlyIncome = 0.0,
                        monthlySavingsGoal = 0.0,
                        createdAt = System.currentTimeMillis()
                    )
                    repository.saveUserProfile(fallbackProfile)
                }
                refreshAiDashboard()
            }
        }
    }

    // Refresh coaching summaries and insights via hybrid UseCase
    fun refreshAiDashboard() {
        val email = _currentEmail.value
        if (email.isEmpty()) return

        viewModelScope.launch {
            _isAiLoading.value = true
            try {
                val recentExpenses = expenses.value.take(15)
                val targetGoals = savingGoals.value
                val limit = userProfile.value?.monthlyBudgetLimit ?: 30000.0

                when (val result = generateInsightsUseCase(recentExpenses, targetGoals, limit)) {
                    is Resource.Success -> {
                        _aiInsights.value = result.data.quickInsights
                        _aiCoachingReport.value = result.data.comprehensiveReport
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Insights generation returned error: ${result.message}")
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch AI coaching insights", e)
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    // Expense Management via Clean Architecture AddExpenseUseCase
    fun addExpense(amount: Double, category: String, description: String, tags: String, paymentMethod: String, date: Long = System.currentTimeMillis()) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            when (val result = addExpenseUseCase(
                userEmail = email,
                amount = amount,
                category = category,
                description = description,
                tags = tags,
                paymentMethod = paymentMethod,
                date = date
            )) {
                is Resource.Success -> {
                    PerformanceTracer.recordMetric("addExpense", System.currentTimeMillis() - start)
                    awardXp(15) // award XP for logging expense!

                    val fraudEval = result.data.fraudEvaluation
                    val reason = fraudEval.reasons.firstOrNull()
                    if (fraudEval.isSuspicious && reason != null) {
                        _lastFraudWarning.value = reason
                        Log.w(TAG, "Suspicious transaction flagged [Score=${fraudEval.riskScore}]: $reason")
                    } else {
                        _lastFraudWarning.value = null
                    }

                        // Smart Alert Checks
                        val newSpentToday = spentToday.value + amount
                        val limit = userProfile.value?.monthlyBudgetLimit ?: 30000.0
                        val dailyLimit = limit / 30.0
                        if (newSpentToday > dailyLimit) {
                            NotificationHelper.triggerOverspendingAlert(context, newSpentToday, dailyLimit)
                        }

                        val userLvl = userProfile.value?.level ?: 1
                        if (userLvl >= 1) {
                            NotificationHelper.triggerStreakAlert(context, maxOf(7, userLvl * 3))
                        }

                        refreshAiDashboard()
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Add expense rejected: ${result.message}")
                    }
                    is Resource.Loading -> {}
                }
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateExpense(expense)
            refreshAiDashboard()
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteExpense(id)
            refreshAiDashboard()
        }
    }

    // Goal Management
    fun addGoal(name: String, goalType: String, targetAmount: Double, currentAmount: Double, monthlyContribution: Double) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addGoal(SavingGoal(
                name = name,
                goalType = goalType,
                targetAmount = targetAmount,
                currentAmount = currentAmount,
                monthlyContribution = monthlyContribution,
                userEmail = email
            ))
            awardXp(40) // award XP
            refreshAiDashboard()
        }
    }

    fun depositToGoal(goal: SavingGoal, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = depositToGoalUseCase(goal, amount)) {
                is Resource.Success -> {
                    awardXp(result.data.earnedXp)
                    result.data.milestoneReached?.let { pct ->
                        NotificationHelper.triggerGoalMilestoneAlert(context, goal.name, pct)
                    }
                    refreshAiDashboard()
                }
                is Resource.Error -> {
                    Log.e(TAG, "Goal deposit failed: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun triggerSync(remoteSnapshot: List<Expense> = emptyList()) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = Resource.Loading()
            val report = syncDataUseCase(email, remoteSnapshot)
            _syncState.value = report
        }
    }

    fun updateGoal(goal: SavingGoal) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateGoal(goal)
            refreshAiDashboard()
        }
    }

    fun deleteGoal(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGoal(id)
            refreshAiDashboard()
        }
    }

    fun recordInsightFeedback(insightId: String, isHelpful: Boolean) {
        Log.d(TAG, "Insight feedback: id=$insightId, helpful=$isHelpful")
        viewModelScope.launch {
            awardXp(if (isHelpful) 10 else 2)
        }
    }

    fun executeInsightAction(actionId: String) {
        Log.d(TAG, "Executing insight action: $actionId")
        viewModelScope.launch {
            awardXp(25)
        }
    }

    // Investment Management
    fun addInvestment(name: String, type: String, invested: Double, currentVal: Double) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addInvestment(Investment(
                name = name,
                type = type,
                investedAmount = invested,
                currentValue = currentVal,
                userEmail = email
            ))
            awardXp(30)
            refreshAiDashboard()
        }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateInvestment(investment)
        }
    }

    fun deleteInvestment(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteInvestment(id)
        }
    }

    // Journal Entry Management
    fun addJournalEntry(content: String) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addJournalEntry(FinancialJournalEntry(
                content = content,
                userEmail = email
            ))
            awardXp(20)
        }
    }

    fun deleteJournal(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteJournalEntry(id)
        }
    }

    // Voice Input Extraction Action
    suspend fun processVoicePrompt(speech: String): Triple<Double, String, String>? {
        return GeminiClient.parseVoiceExpense(speech)
    }

    // Receipt OCR Scanning Extraction Action
    suspend fun processReceiptOcr(simulatedReceiptInfo: String): Triple<String, Double, String>? {
        return GeminiClient.parseReceipt(
            merchantNameSimulated = "Coffee House Cafe",
            totalAmountSimulated = 350.0,
            categorySimulated = "Food",
            rawReceiptText = simulatedReceiptInfo
        )
    }

    // XP & Gamification Logic
    private suspend fun awardXp(amount: Int) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        val currentProfile = repository.getUserProfileSync(email).getOrNull() ?: return

        var newXp = currentProfile.xp + amount
        var newLevel = currentProfile.level

        // Broad level formula: Level up every 100 XP
        while (newXp >= 100) {
            newXp -= 100
            newLevel += 1
        }

        val updatedProfile = currentProfile.copy(xp = newXp, level = newLevel)
        repository.saveUserProfile(updatedProfile)
    }

    // Auth actions proxy
    suspend fun handleRegister(email: String, username: String, pass: String): String {
        val res = authManager.register(email, username, pass)
        return res.message
    }

    suspend fun handleLogin(email: String, pass: String): Boolean {
        val res = authManager.login(email, pass)
        if (res.success && res.profile != null) {
            _currentEmail.value = email
            isLoggedIn.value = true
            refreshAiDashboard()
            return true
        }
        return false
    }

    suspend fun handleGoogleSignIn(email: String, name: String) {
        val res = authManager.googleSignInMock(email, name)
        if (res.success && res.profile != null) {
            _currentEmail.value = email
            isLoggedIn.value = true
            refreshAiDashboard()
        }
    }

    fun handleBiometricLogin(): Boolean {
        val email = authManager.getLoggedInUserEmail()
        if (email != null && email.isNotEmpty()) {
            _currentEmail.value = email
            isLoggedIn.value = true
            refreshAiDashboard()
            return true
        }
        return false
    }

    fun handleLogout() {
        authManager.logout()
        _currentEmail.value = ""
        isLoggedIn.value = false
        _aiInsights.value = emptyList()
        _aiCoachingReport.value = ""
    }

    fun updateBudgetSettings(monthlyLimit: Double, dailyLimit: Double) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val prof = repository.getUserProfileSync(email).getOrNull()
            if (prof != null) {
                repository.saveUserProfile(prof.copy(
                    monthlyBudgetLimit = monthlyLimit,
                    dailyBudgetLimit = dailyLimit
                ))
            }
        }
    }

    fun updateUserProfile(username: String) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val prof = repository.getUserProfileSync(email).getOrNull()
            if (prof != null) {
                repository.saveUserProfile(prof.copy(username = username))
            }
        }
    }

    fun updateFinancialProfile(netWorth: Double, annualIncome: Double, monthlySavingsGoal: Double) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val prof = repository.getUserProfileSync(email).getOrNull()
            if (prof != null) {
                val monthlyIncomeCalculated = annualIncome / 12.0
                val monthlyBudgetCalculated = monthlyIncomeCalculated - monthlySavingsGoal
                repository.saveUserProfile(prof.copy(
                    netWorth = netWorth,
                    annualIncome = annualIncome,
                    monthlyIncome = monthlyIncomeCalculated,
                    monthlySavingsGoal = monthlySavingsGoal,
                    monthlyBudgetLimit = if (monthlyBudgetCalculated > 0) monthlyBudgetCalculated else 0.0
                ))
                refreshAiDashboard()
            }
        }
    }

    fun getRemainingDaysInMonth(): Int {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return maxOf(1, totalDays - today + 1)
    }

    // Recurring Subscriptions Manager
    private val _recurringRules = MutableStateFlow<List<com.example.domain.usecase.feature.RecurringExpenseRule>>(emptyList())
    val recurringRules: StateFlow<List<com.example.domain.usecase.feature.RecurringExpenseRule>> = _recurringRules.asStateFlow()

    fun addRecurringRule(title: String, category: String, amount: Double, frequency: com.example.domain.usecase.feature.RecurringFrequency = com.example.domain.usecase.feature.RecurringFrequency.MONTHLY) {
        val email = _currentEmail.value
        if (email.isEmpty()) return
        val newRule = com.example.domain.usecase.feature.RecurringExpenseRule(
            userEmail = email,
            title = title,
            category = category,
            money = com.example.domain.model.Money.fromRupees(amount),
            frequency = frequency,
            nextDueEpoch = System.currentTimeMillis() + (30L * 86_400_000L),
            isActive = true
        )
        _recurringRules.value = _recurringRules.value + newRule
    }

    fun toggleRecurringRule(ruleId: String) {
        _recurringRules.value = _recurringRules.value.map {
            if (it.id == ruleId) it.copy(isActive = !it.isActive) else it
        }
    }

    fun deleteRecurringRule(ruleId: String) {
        _recurringRules.value = _recurringRules.value.filter { it.id != ruleId }
    }

    // Helper functions for calendar bounds
    private fun getStartOfDayTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfMonthTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

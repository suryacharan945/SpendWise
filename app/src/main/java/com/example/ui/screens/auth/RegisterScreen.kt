package com.example.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Money
import com.example.ui.components.ConfettiOverlay
import com.example.ui.theme.AnimatedAuthBackground
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase 6 Registration Stepper:
 * Step 1: Email & Password (4-segment animated strength bar)
 * Step 2: Name & Avatar (12 gradient-backed emoji/initial avatars)
 * Step 3: Money Setup (Monthly income slider, salary day, live auto-generated category budget chips)
 * Step 4: Completion (Confetti + Welcome Card + Computed daily allowance hero)
 */
@Composable
fun RegisterScreen(
    viewModel: MainViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var currentStep by remember { mutableIntStateOf(1) } // 1..4

    // Step 1 State
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Step 2 State
    var username by remember { mutableStateOf("") }
    var selectedAvatarIndex by remember { mutableIntStateOf(0) }

    // Step 3 State (Money Setup)
    var monthlyIncome by remember { mutableDoubleStateOf(65000.0) }
    var salaryDay by remember { mutableIntStateOf(1) }
    var savingsPercent by remember { mutableIntStateOf(20) }

    var errorMessage by remember { mutableStateOf("") }
    var triggerConfetti by remember { mutableStateOf(false) }

    val avatars = listOf(
        "🦁", "🚀", "💎", "⚡", "🦊", "🎯",
        "🦉", "🌟", "🔥", "🍀", "💼", "👑"
    )

    // Animated progress bar
    val progressAnim by animateFloatAsState(
        targetValue = (currentStep - 1) / 3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "StepperProgress"
    )

    // Password strength score (0..4)
    val passwordStrength = remember(password) {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }

    val strengthLabel = when (passwordStrength) {
        0 -> "Too Short"
        1 -> "Weak"
        2 -> "Fair"
        3 -> "Good"
        4 -> "Solid 💪"
        else -> ""
    }

    val strengthColor = when (passwordStrength) {
        0 -> colorScheme.outline
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF59E0B)
        3 -> Color(0xFF3B82F6)
        4 -> Color(0xFF10B981)
        else -> colorScheme.primary
    }

    AnimatedAuthBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header & Progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Step $currentStep of 4",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        if (currentStep < 4) {
                            TextButton(onClick = onNavigateToLogin) {
                                Text("Back to Login", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progressAnim },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMessage.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Shared-Axis Animated Step Switcher
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                        slideOutHorizontally { -it } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith
                                        slideOutHorizontally { it } + fadeOut()
                            }
                        },
                        label = "RegisterStepAnimation"
                    ) { step ->
                        when (step) {
                            1 -> {
                                // Step 1: Credentials
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Create Your Account", fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    Text("Enter your email and a secure password", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 16.dp))

                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it; errorMessage = "" },
                                        label = { Text("Email Address") },
                                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it; errorMessage = "" },
                                        label = { Text("Password") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    // Password Strength Meter (4-segment animated bar)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        repeat(4) { idx ->
                                            val isActive = idx < passwordStrength
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isActive) strengthColor else colorScheme.surfaceVariant)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Strength: $strengthLabel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = strengthColor)
                                        Text("Min 8 chars, 1 upper, 1 num", fontSize = 10.sp, color = colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            2 -> {
                                // Step 2: Name & Avatar
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Pick Your Identity", fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    Text("How should your coach greet you?", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 16.dp))

                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it; errorMessage = "" },
                                        label = { Text("Your First Name") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("Choose Avatar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.Start))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 12 Avatar Grid
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(6),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(avatars.indices.toList()) { index ->
                                            val isSelected = selectedAvatarIndex == index
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant
                                                    )
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.dp,
                                                        color = if (isSelected) colorScheme.primary else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { selectedAvatarIndex = index },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(avatars[index], fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            3 -> {
                                // Step 3: Money Setup (The signature moment!)
                                val incomeMoney = Money.fromRupees(monthlyIncome)
                                val savingsPaise = (incomeMoney.paise * savingsPercent) / 100L
                                val suggestedFood = Money.fromRupees(monthlyIncome * 0.22)
                                val suggestedRent = Money.fromRupees(monthlyIncome * 0.28)
                                val suggestedLeisure = Money.fromRupees(monthlyIncome * 0.15)
                                val dailySafe = maxOf(0.0, (monthlyIncome - (monthlyIncome * savingsPercent / 100)) / 30.0)

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Money Setup", fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    Text("Tailor your spending boundaries instantly", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 12.dp))

                                    // Income Live Count-Up
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Estimated Monthly Income", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurfaceVariant)
                                            Text(
                                                text = incomeMoney.formatWithoutDecimals(),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Black,
                                                color = colorScheme.primary
                                            )
                                            Slider(
                                                value = monthlyIncome.toFloat(),
                                                onValueChange = { monthlyIncome = (it / 1000).toInt() * 1000.0 },
                                                valueRange = 15000f..250000f,
                                                steps = 46,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Suggested Category Chips (auto-generated as income drags)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SuggestionChip(
                                            title = "Food (22%)",
                                            amount = suggestedFood.formatWithoutDecimals(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        SuggestionChip(
                                            title = "Bills (28%)",
                                            amount = suggestedRent.formatWithoutDecimals(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        SuggestionChip(
                                            title = "Fun (15%)",
                                            amount = suggestedLeisure.formatWithoutDecimals(),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Salary Day Picker
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Salary Credit Day", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { if (salaryDay > 1) salaryDay-- },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Minus")
                                            }
                                            Text("Day $salaryDay", fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
                                            IconButton(
                                                onClick = { if (salaryDay < 31) salaryDay++ },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Plus")
                                            }
                                        }
                                    }

                                    // Live Daily Headroom Preview
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "💡 Computed Safe Daily Allowance: ${Money.fromRupees(dailySafe).format()}/day",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(8.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            4 -> {
                                // Step 4: Celebration
                                val dailyAllowance = Money.fromRupees((monthlyIncome * 0.7) / 30.0)

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(avatars[selectedAvatarIndex], fontSize = 64.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Welcome, $username! 🎉", fontSize = 22.sp, fontWeight = FontWeight.Black)
                                    Text("Your personalized budget engine is calibrated.", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 16.dp))

                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("YOUR STARTING SAFE-TO-SPEND", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${dailyAllowance.format()}/day",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Black,
                                                color = colorScheme.primary
                                            )
                                            Text("Zero guilt. Exact tracking starts now.", fontSize = 11.sp, color = colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Next / Submit Stepper Button
                    Button(
                        onClick = {
                            when (currentStep) {
                                1 -> {
                                    if (email.isBlank() || !email.contains("@")) {
                                        errorMessage = "Please enter a valid email address."
                                        return@Button
                                    }
                                    if (password.length < 8) {
                                        errorMessage = "Password must be at least 8 characters."
                                        return@Button
                                    }
                                    currentStep = 2
                                    errorMessage = ""
                                }
                                2 -> {
                                    if (username.isBlank()) {
                                        errorMessage = "Please enter your name."
                                        return@Button
                                    }
                                    currentStep = 3
                                    errorMessage = ""
                                }
                                3 -> {
                                    coroutineScope.launch {
                                        viewModel.handleRegister(email, username, password)
                                        viewModel.updateFinancialProfile(
                                            netWorth = 0.0,
                                            annualIncome = monthlyIncome * 12,
                                            monthlySavingsGoal = monthlyIncome * (savingsPercent / 100.0)
                                        )
                                        currentStep = 4
                                        triggerConfetti = true
                                    }
                                }
                                4 -> {
                                    onRegisterSuccess()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = when (currentStep) {
                                1 -> "Continue to Identity →"
                                2 -> "Configure Budget →"
                                3 -> "Finish Setup 🚀"
                                else -> "Enter Dashboard →"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            ConfettiOverlay(trigger = triggerConfetti)
        }
    }
}

@Composable
private fun SuggestionChip(title: String, amount: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

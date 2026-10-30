package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.screens.auth.*
import com.example.ui.viewmodel.MainViewModel

enum class AuthStage {
    SPLASH,
    ONBOARDING,
    LOGIN,
    REGISTER
}

/**
 * Phase 6 Unified Auth Coordinator:
 * Splash (draw-in <= 1.2s) -> Onboarding (first launch) -> Login / Register -> Dashboard
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("smart_budget_app_pref", android.content.Context.MODE_PRIVATE)
    val hasSeenOnboarding = remember { prefs.getBoolean("has_seen_onboarding_v2", false) }

    var authStage by remember { mutableStateOf(AuthStage.SPLASH) }

    AnimatedContent(
        targetState = authStage,
        transitionSpec = {
            fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
        },
        label = "AuthStageTransition"
    ) { stage ->
        when (stage) {
            AuthStage.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        authStage = AuthStage.LOGIN
                    }
                )
            }
            AuthStage.ONBOARDING -> {
                // Kept for backward compatibility but bypassed for fast direct auth
                authStage = AuthStage.LOGIN
            }
            AuthStage.LOGIN -> {
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToRegister = { authStage = AuthStage.REGISTER },
                    onNavigateToForgotPassword = { /* Handled inline */ },
                    onLoginSuccess = { /* MainViewModel.isLoggedIn StateFlow updates automatically */ }
                )
            }
            AuthStage.REGISTER -> {
                RegisterScreen(
                    viewModel = viewModel,
                    onNavigateToLogin = { authStage = AuthStage.LOGIN },
                    onRegisterSuccess = { /* MainViewModel.isLoggedIn StateFlow updates automatically */ }
                )
            }
        }
    }
}

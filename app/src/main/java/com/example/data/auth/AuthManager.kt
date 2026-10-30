package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class AuthResult(val success: Boolean, val message: String, val profile: UserProfile? = null)

class AuthManager(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("smart_budget_tracker_auth_pref", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(context)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LOGGED_IN_EMAIL = "logged_in_email"
        private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        private const val KEY_VERIFICATION_SENT = "verification_sent_email"
    }

    fun isLoggedIn(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getLoggedInUserEmail(): String? {
        return sharedPrefs.getString(KEY_LOGGED_IN_EMAIL, null)
    }

    fun isBiometricsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_BIOMETRICS_ENABLED, false)
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_BIOMETRICS_ENABLED, enabled).apply()
    }

    suspend fun register(email: String, username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            return@withContext AuthResult(false, "Please fill in all registration fields.")
        }

        try {
            val existing = db.userProfileDao().getUserProfileSync(email)
            if (existing != null) {
                return@withContext AuthResult(false, "An account with this email already exists.")
            }

            // Create user profile
            val newProfile = UserProfile(
                email = email,
                username = username,
                avatarUrl = "https://api.dicebear.com/7.x/adventurer/svg?seed=$username",
                monthlyBudgetLimit = 0.0,
                dailyBudgetLimit = 0.0,
                netWorth = 0.0,
                annualIncome = 0.0,
                monthlyIncome = 0.0,
                monthlySavingsGoal = 0.0,
                createdAt = System.currentTimeMillis()
            )
            db.userProfileDao().insertOrUpdateProfile(newProfile)

            // Save credentials in password table or simple shared preference
            sharedPrefs.edit()
                .putString("pass_$email", password)
                .apply()

            return@withContext AuthResult(true, "Registration successful!", newProfile)
        } catch (e: Exception) {
            Log.e("AuthManager", "Registration error", e)
            return@withContext AuthResult(false, "Database registration error: ${e.message}")
        }
    }

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (email.isEmpty() || password.isEmpty()) {
            return@withContext AuthResult(false, "Please enter both email and password.")
        }

        try {
            val savedPass = sharedPrefs.getString("pass_$email", null)
            val profile = db.userProfileDao().getUserProfileSync(email)

            if (profile != null && savedPass == password) {
                sharedPrefs.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .putString(KEY_LOGGED_IN_EMAIL, email)
                    .apply()
                return@withContext AuthResult(true, "Login successful", profile)
            } else if (profile == null && email == "demo@smartbudget.com" && (password == "password" || password == "password123")) {
                // Prepopulate clean demo profile on first demo login
                val demoProfile = UserProfile(
                    email = "demo@smartbudget.com",
                    username = "Demo User",
                    avatarUrl = "https://api.dicebear.com/7.x/adventurer/svg?seed=Demo",
                    monthlyBudgetLimit = 35000.0,
                    dailyBudgetLimit = 1166.0,
                    netWorth = 0.0,
                    annualIncome = 600000.0,
                    monthlyIncome = 50000.0,
                    monthlySavingsGoal = 15000.0,
                    createdAt = System.currentTimeMillis()
                )
                db.userProfileDao().insertOrUpdateProfile(demoProfile)
                sharedPrefs.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .putString(KEY_LOGGED_IN_EMAIL, email)
                    .putString("pass_$email", password)
                    .apply()
                return@withContext AuthResult(true, "Demo login successful", demoProfile)
            } else {
                return@withContext AuthResult(false, "Invalid email or password.")
            }
        } catch (e: Exception) {
            return@withContext AuthResult(false, "Login database error: ${e.message}")
        }
    }

    suspend fun forgotPassword(email: String): AuthResult = withContext(Dispatchers.IO) {
        if (email.isEmpty()) {
            return@withContext AuthResult(false, "Please enter your email.")
        }
        val pass = sharedPrefs.getString("pass_$email", null)
        val profile = db.userProfileDao().getUserProfileSync(email)
        if (profile != null || email == "demo@smartbudget.com") {
            return@withContext AuthResult(true, "Password reset code sent to your email. (Hint: password is '$pass')")
        } else {
            return@withContext AuthResult(false, "No account matches this email address.")
        }
    }

    suspend fun verifyEmailSimulated(email: String): AuthResult = withContext(Dispatchers.IO) {
        sharedPrefs.edit().putBoolean("verified_$email", true).apply()
        return@withContext AuthResult(true, "Email $email verified successfully!")
    }

    suspend fun googleSignInMock(email: String, name: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            var profile = db.userProfileDao().getUserProfileSync(email)
            if (profile == null) {
                profile = UserProfile(
                    email = email,
                    username = name,
                    avatarUrl = "https://api.dicebear.com/7.x/adventurer/svg?seed=$name"
                )
                db.userProfileDao().insertOrUpdateProfile(profile)
            }
            sharedPrefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_LOGGED_IN_EMAIL, email)
                .apply()
            return@withContext AuthResult(true, "Google Sign-In successful!", profile)
        } catch (e: Exception) {
            return@withContext AuthResult(false, "Google association error: ${e.message}")
        }
    }

    fun logout() {
        sharedPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_LOGGED_IN_EMAIL)
            .apply()
    }

    fun getThemeMode(): String {
        return sharedPrefs.getString("theme_mode", "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun getLanguage(): String {
        return sharedPrefs.getString("app_language", "en") ?: "en"
    }

    fun setLanguage(lang: String) {
        sharedPrefs.edit().putString("app_language", lang).apply()
    }

    fun isNotificationsEnabled(): Boolean {
        return sharedPrefs.getBoolean("notifications_enabled", true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    suspend fun changePassword(email: String, oldPass: String, newPass: String): AuthResult = withContext(Dispatchers.IO) {
        if (email.isEmpty() || oldPass.isEmpty() || newPass.isEmpty()) {
            return@withContext AuthResult(false, "Please fill in all details.")
        }
        val savedPass = sharedPrefs.getString("pass_$email", null)
        if (savedPass != oldPass) {
            return@withContext AuthResult(false, "Current password does not match.")
        }
        sharedPrefs.edit().putString("pass_$email", newPass).apply()
        return@withContext AuthResult(true, "Password changed successfully!")
    }
}

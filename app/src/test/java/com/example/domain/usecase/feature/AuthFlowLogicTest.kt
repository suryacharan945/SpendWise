package com.example.domain.usecase.feature

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthFlowLogicTest {

    private fun calculatePasswordStrength(password: String): Int {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score
    }

    private fun calculateLockoutSeconds(failedAttempts: Int): Long {
        if (failedAttempts < 3) return 0L
        // Exponential backoff: 2^(failedAttempts - 3) * 5 seconds, capped at 300s
        val exp = (failedAttempts - 3).toDouble()
        val seconds = (2.0.pow(exp) * 5.0).toLong()
        return minOf(300L, seconds)
    }

    private fun isValidEmail(email: String): Boolean {
        val regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return regex.matches(email)
    }

    @Test
    fun `password strength scoring correctly identifies weak to solid passwords`() {
        assertEquals(0, calculatePasswordStrength("short"))
        assertEquals(1, calculatePasswordStrength("longpasswordwithoutothers"))
        assertEquals(2, calculatePasswordStrength("LongPasswordNoNum"))
        assertEquals(3, calculatePasswordStrength("LongPassword123"))
        assertEquals(4, calculatePasswordStrength("SolidPassword123!"))
    }

    @Test
    fun `email validation accepts standard emails and rejects malformed inputs`() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("surya.dev@smartbudget.in"))
        assertFalse(isValidEmail("not-an-email"))
        assertFalse(isValidEmail("user@domain"))
        assertFalse(isValidEmail("@domain.com"))
    }

    @Test
    fun `exponential backoff enforces lockout after repeated failures`() {
        assertEquals(0L, calculateLockoutSeconds(0))
        assertEquals(0L, calculateLockoutSeconds(2))
        assertEquals(5L, calculateLockoutSeconds(3))  // 2^0 * 5 = 5s
        assertEquals(10L, calculateLockoutSeconds(4)) // 2^1 * 5 = 10s
        assertEquals(20L, calculateLockoutSeconds(5)) // 2^2 * 5 = 20s
        assertEquals(300L, calculateLockoutSeconds(15)) // Capped at 300s
    }
}

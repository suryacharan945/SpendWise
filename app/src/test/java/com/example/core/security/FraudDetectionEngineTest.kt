package com.example.core.security

import com.example.data.model.Expense
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FraudDetectionEngineTest {

    @Test
    fun `normal transaction scores low risk and is allowed`() {
        val result = FraudDetectionEngine.evaluateTransaction(
            newAmount = 150.0,
            newCategory = "Food",
            newDescription = "Coffee",
            recentExpenses = emptyList(),
            monthlyBudget = 30000.0
        )

        assertFalse(result.isSuspicious)
        assertEquals(FraudRiskLevel.LOW, result.riskLevel)
        assertEquals(FraudAction.ALLOW, result.actionRecommended)
        assertEquals(0, result.riskScore)
    }

    @Test
    fun `detects duplicate debit within two minutes`() {
        val now = System.currentTimeMillis()
        val recent = listOf(
            Expense(
                id = 1,
                amount = 250.0,
                category = "Food",
                description = "Burger",
                date = now - 30_000L,
                userEmail = "test@domain.com"
            )
        )

        val result = FraudDetectionEngine.evaluateTransaction(
            newAmount = 250.0,
            newCategory = "Food",
            newDescription = "Burger again",
            recentExpenses = recent,
            monthlyBudget = 30000.0,
            timestamp = now
        )

        assertEquals(30, result.riskScore)
        assertEquals(FraudRiskLevel.MEDIUM, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("Potential duplicate debit") })
    }

    @Test
    fun `detects outlier expense consuming over 50 percent of monthly budget`() {
        val result = FraudDetectionEngine.evaluateTransaction(
            newAmount = 18000.0,
            newCategory = "Electronics",
            newDescription = "Laptop payment",
            recentExpenses = emptyList(),
            monthlyBudget = 30000.0
        )

        assertTrue(result.isSuspicious)
        assertEquals(FraudRiskLevel.HIGH, result.riskLevel)
        assertTrue(result.reasons.any { it.contains("Unusual outlier: single transaction consumes >50%") })
    }

    @Test
    fun `detects high velocity bursts in short window`() {
        val now = System.currentTimeMillis()
        val recent = listOf(
            Expense(id = 1, amount = 50.0, category = "Misc", description = "t1", date = now - 60_000L, userEmail = "u@t.com"),
            Expense(id = 2, amount = 60.0, category = "Misc", description = "t2", date = now - 120_000L, userEmail = "u@t.com"),
            Expense(id = 3, amount = 70.0, category = "Misc", description = "t3", date = now - 180_000L, userEmail = "u@t.com")
        )

        val result = FraudDetectionEngine.evaluateTransaction(
            newAmount = 80.0,
            newCategory = "Misc",
            newDescription = "t4 rapid",
            recentExpenses = recent,
            monthlyBudget = 30000.0,
            timestamp = now
        )

        assertEquals(35, result.riskScore)
        assertTrue(result.reasons.any { it.contains("High transaction velocity") })
    }

    @Test
    fun `detects unusual off-hours midnight burst between 1 AM and 5 AM`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2) // 2:30 AM
            set(Calendar.MINUTE, 30)
        }

        val result = FraudDetectionEngine.evaluateTransaction(
            newAmount = 5000.0,
            newCategory = "Shopping",
            newDescription = "Midnight impulse",
            recentExpenses = emptyList(),
            monthlyBudget = 30000.0,
            timestamp = cal.timeInMillis
        )

        assertTrue(result.reasons.any { it.contains("Unusual off-hours transaction") })
        assertTrue(result.riskScore >= 20)
    }
}

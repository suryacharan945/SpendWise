package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.Expense
import com.example.data.model.SavingGoal
import com.example.domain.model.Money
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeatureUseCasesTest {

    @Test
    fun `RecurringExpenseUseCase processes due expenses and advances next due date`() {
        val useCase = RecurringExpenseUseCase()
        val now = 1_000_000L
        val ruleDue = RecurringExpenseRule(
            id = "rule-1",
            userEmail = "user@test.com",
            title = "Netflix",
            category = "Entertainment",
            money = Money.fromRupees(649.0),
            frequency = RecurringFrequency.MONTHLY,
            nextDueEpoch = now - 100L
        )
        val ruleFuture = RecurringExpenseRule(
            id = "rule-2",
            userEmail = "user@test.com",
            title = "Gym",
            category = "Fitness",
            money = Money.fromRupees(1500.0),
            frequency = RecurringFrequency.MONTHLY,
            nextDueEpoch = now + 10_000L
        )

        val result = useCase.processDueRules(listOf(ruleDue, ruleFuture), now)
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(1, data.createdExpenses.size)
        assertEquals("Netflix", data.createdExpenses[0].description.replace("Recurring: ", ""))
        assertEquals(64900L, data.createdExpenses[0].amountPaise)
        assertEquals(2, data.updatedRules.size)
        assertTrue(data.updatedRules[0].nextDueEpoch > now)
    }

    @Test
    fun `SplitExpenseUseCase divides money in integer paise with zero remainder loss`() {
        val useCase = SplitExpenseUseCase()
        val total = Money.fromRupees(100.0) // 10,000 paise
        val participants = listOf("Alice", "Bob", "Charlie") // 3 people

        val result = useCase.invoke(total, participants)
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(3, data.participantShares.size)

        // 10000 / 3 = 3333 with remainder 1. First person gets 3334, others get 3333.
        assertEquals(3334L, data.participantShares[0].share.paise)
        assertEquals(3333L, data.participantShares[1].share.paise)
        assertEquals(3333L, data.participantShares[2].share.paise)

        // Sum must equal exactly 10,000 paise
        val sumPaise = data.participantShares.sumOf { it.share.paise }
        assertEquals(total.paise, sumPaise)
    }

    @Test
    fun `RoundUpRuleUseCase computes correct spare change to nearest 10 and 50`() {
        val useCase = RoundUpRuleUseCase()

        // Test ₹234 -> nearest 10 is ₹240 (delta ₹6 = 600 paise)
        val expense1 = Money.fromRupees(234.0)
        val res1 = useCase.invoke(expense1, RoundUpTarget.NEAREST_10)
        assertTrue(res1 is Resource.Success)
        val data1 = (res1 as Resource.Success).data
        assertEquals(24000L, data1.roundedMoney.paise)
        assertEquals(600L, data1.spareChangeMoney.paise)

        // Test ₹234 -> nearest 50 is ₹250 (delta ₹16 = 1600 paise)
        val res2 = useCase.invoke(expense1, RoundUpTarget.NEAREST_50)
        assertTrue(res2 is Resource.Success)
        val data2 = (res2 as Resource.Success).data
        assertEquals(25000L, data2.roundedMoney.paise)
        assertEquals(1600L, data2.spareChangeMoney.paise)

        // Exact match ₹250 -> 0 spare change
        val exact = Money.fromRupees(250.0)
        val res3 = useCase.invoke(exact, RoundUpTarget.NEAREST_50)
        assertTrue(res3 is Resource.Success)
        val data3 = (res3 as Resource.Success).data
        assertEquals(0L, data3.spareChangeMoney.paise)
    }

    @Test
    fun `ImportExpensesUseCase correctly parses CSV and skips duplicates`() {
        val useCase = ImportExpensesUseCase()
        val csv = """
            id,description,category,amount,emotion,date
            e-1,"Team Dinner",Food,450.0,Happy,1700000000000
            e-2,"Coffee",Food,120.0,Stressed,1700000100000
            e-3,"Groceries",Groceries,1200.0,Necessary,1700000200000
        """.trimIndent()

        val existingIds = setOf("e-1") // e-1 already exists

        val result = useCase.invoke(csv, "user@test.com", existingIds)
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(2, data.importedCount)
        assertEquals(1, data.skippedDuplicatesCount)
        assertEquals("Coffee", data.newExpenses[0].description)
        assertEquals(12000L, data.newExpenses[0].amountPaise)
    }

    @Test
    fun `BackupRestoreUseCase creates encrypted snapshot and restores entities cleanly`() {
        val useCase = BackupRestoreUseCase()
        val expenses = listOf(
            Expense(id = 1, userEmail = "u@test.com", amount = 500.0, amountPaise = 50000L, category = "Food", description = "Lunch", date = 1000L),
            Expense(id = 2, userEmail = "u@test.com", amount = 1200.0, amountPaise = 120000L, category = "Travel", description = "Train", date = 2000L)
        )
        val goals = listOf(
            SavingGoal(id = 1, userEmail = "u@test.com", name = "MacBook", goalType = "Tech", targetAmount = 100000.0, currentAmount = 25000.0)
        )

        val backupRes = useCase.createBackup(expenses, goals)
        assertTrue(backupRes is Resource.Success)
        val cipherText = (backupRes as Resource.Success).data.encryptedPayload
        assertNotEquals("", cipherText)

        val restoreRes = useCase.restoreBackup(cipherText)
        assertTrue(restoreRes is Resource.Success)
        val restoredData = (restoreRes as Resource.Success).data
        assertEquals(2, restoredData.expenses.size)
        assertEquals(1, restoredData.goals.size)
        assertEquals(50000L, restoredData.expenses[0].amountPaise)
        assertEquals("MacBook", restoredData.goals[0].name)
    }
}

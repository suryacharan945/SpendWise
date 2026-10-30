package com.example.core.sync

import com.example.data.model.Expense
import com.example.data.model.SavingGoal
import org.junit.Assert.*
import org.junit.Test

class SyncConflictResolverTest {

    @Test
    fun `resolveExpenseConflict returns OVERWRITE_WITH_REMOTE when remote timestamp is newer`() {
        val local = Expense(
            id = 1,
            amount = 150.0,
            category = "Food",
            description = "Lunch",
            updatedAt = 1000L,
            userEmail = "user@test.com"
        )
        val remote = Expense(
            id = 1,
            amount = 180.0,
            category = "Food",
            description = "Lunch Buffet",
            updatedAt = 2000L,
            userEmail = "user@test.com"
        )

        val result = SyncConflictResolver.resolveExpenseConflict(local, remote)

        assertEquals(SyncAction.OVERWRITE_WITH_REMOTE, result.action)
        assertEquals(180.0, result.resolvedEntity.amount, 0.001)
        assertEquals("Lunch Buffet", result.resolvedEntity.description)
        assertEquals("SYNCED", result.resolvedEntity.syncStatus)
    }

    @Test
    fun `resolveExpenseConflict returns USE_LOCAL when local timestamp is newer or equal`() {
        val local = Expense(
            id = 2,
            amount = 300.0,
            category = "Shopping",
            description = "Book",
            updatedAt = 5000L,
            userEmail = "user@test.com"
        )
        val remote = Expense(
            id = 2,
            amount = 250.0,
            category = "Shopping",
            description = "Old Book",
            updatedAt = 4000L,
            userEmail = "user@test.com"
        )

        val result = SyncConflictResolver.resolveExpenseConflict(local, remote)

        assertEquals(SyncAction.USE_LOCAL, result.action)
        assertEquals(300.0, result.resolvedEntity.amount, 0.001)
        assertEquals("Book", result.resolvedEntity.description)
        assertEquals("PENDING_PUSH", result.resolvedEntity.syncStatus)
    }

    @Test
    fun `resolveGoalConflict merges currentAmount to high-water mark when remote is newer`() {
        val localGoal = SavingGoal(
            id = 10,
            name = "Emergency Fund",
            goalType = "Savings",
            targetAmount = 50000.0,
            currentAmount = 15000.0, // Offline deposit made locally
            updatedAt = 1000L,
            userEmail = "user@test.com"
        )
        val remoteGoal = SavingGoal(
            id = 10,
            name = "Emergency Fund Updated",
            goalType = "Savings",
            targetAmount = 60000.0,
            currentAmount = 10000.0,
            updatedAt = 2000L,
            userEmail = "user@test.com"
        )

        val result = SyncConflictResolver.resolveGoalConflict(localGoal, remoteGoal)

        assertEquals(SyncAction.MERGED, result.action)
        assertEquals(15000.0, result.resolvedEntity.currentAmount, 0.001) // Preserved higher offline deposit!
        assertEquals("Emergency Fund Updated", result.resolvedEntity.name)
        assertEquals(60000.0, result.resolvedEntity.targetAmount, 0.001)
        assertEquals("SYNCED", result.resolvedEntity.syncStatus)
    }

    @Test
    fun `resolveGoalConflict respects newer soft delete`() {
        val localGoal = SavingGoal(
            id = 11,
            name = "Vacation",
            goalType = "Travel",
            targetAmount = 20000.0,
            currentAmount = 5000.0,
            isDeleted = false,
            updatedAt = 1000L,
            userEmail = "user@test.com"
        )
        val remoteGoal = SavingGoal(
            id = 11,
            name = "Vacation",
            goalType = "Travel",
            targetAmount = 20000.0,
            currentAmount = 5000.0,
            isDeleted = true, // Deleted on another device
            updatedAt = 3000L,
            userEmail = "user@test.com"
        )

        val result = SyncConflictResolver.resolveGoalConflict(localGoal, remoteGoal)

        assertTrue(result.resolvedEntity.isDeleted)
        assertEquals(SyncAction.OVERWRITE_WITH_REMOTE, result.action)
    }
}

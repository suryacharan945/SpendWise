package com.example.domain.usecase

import com.example.core.common.Resource
import com.example.core.sync.SyncAction
import com.example.core.sync.SyncConflictResolver
import com.example.data.model.Expense
import com.example.domain.repository.IFinanceRepository
import kotlinx.coroutines.flow.first

data class SyncReport(
    val pushedExpenses: Int,
    val pulledExpenses: Int,
    val conflictsResolved: Int,
    val isSuccess: Boolean
)

/**
 * UseCase executing bidirectional offline-to-cloud data reconciliation.
 */
class SyncDataUseCase(
    private val repository: IFinanceRepository
) {
    suspend operator fun invoke(
        userEmail: String,
        remoteExpensesSnapshot: List<Expense> = emptyList()
    ): Resource<SyncReport> {
        return try {
            // 1. Identify local dirty/unsynced records for push
            val unsyncedRes = repository.getUnsyncedExpenses(userEmail)
            val dirtyExpenses = unsyncedRes.getOrNull() ?: emptyList()
            var pushedCount = 0

            for (expense in dirtyExpenses) {
                // Mark record as synced after remote push simulation
                repository.updateExpense(expense.copy(syncStatus = "SYNCED"))
                pushedCount++
            }

            // 2. Reconcile incoming remote delta records using LWW
            val currentLocalExpenses = repository.getExpenses(userEmail).first().getOrNull() ?: emptyList()
            val localMap = currentLocalExpenses.associateBy { it.id }
            var pulledCount = 0
            var conflictCount = 0

            for (remoteExp in remoteExpensesSnapshot) {
                val localExp = localMap[remoteExp.id]
                if (localExp == null) {
                    // New record from remote
                    repository.addExpense(remoteExp.copy(syncStatus = "SYNCED"))
                    pulledCount++
                } else {
                    // Conflict check
                    val resolution = SyncConflictResolver.resolveExpenseConflict(localExp, remoteExp)
                    if (resolution.action == SyncAction.OVERWRITE_WITH_REMOTE) {
                        repository.updateExpense(resolution.resolvedEntity)
                        conflictCount++
                    }
                }
            }

            Resource.Success(
                SyncReport(
                    pushedExpenses = pushedCount,
                    pulledExpenses = pulledCount,
                    conflictsResolved = conflictCount,
                    isSuccess = true
                )
            )
        } catch (e: Exception) {
            Resource.Error("Sync failed: ${e.localizedMessage}", cause = e)
        }
    }
}

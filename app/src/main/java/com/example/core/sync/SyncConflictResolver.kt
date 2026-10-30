package com.example.core.sync

import com.example.data.model.Expense
import com.example.data.model.SavingGoal

enum class SyncAction {
    USE_LOCAL,
    OVERWRITE_WITH_REMOTE,
    MERGED
}

data class ConflictResolutionResult<T>(
    val resolvedEntity: T,
    val action: SyncAction
)

/**
 * Deterministic Conflict Resolution Engine for Offline-First FinTech Sync.
 * Implements Last-Write-Wins (LWW) with timestamp comparison, soft-delete propagation,
 * and intelligent balance merges.
 */
object SyncConflictResolver {

    /**
     * Determines whether remote wins over local using LWW with syncVersion tie-breaking.
     */
    fun isRemoteWinner(localUpdated: Long, localVersion: Long, remoteUpdated: Long, remoteVersion: Long): Boolean {
        return if (remoteUpdated != localUpdated) {
            remoteUpdated > localUpdated
        } else {
            remoteVersion > localVersion
        }
    }

    /**
     * Resolves conflict between local and remote Expense record using Last-Write-Wins (LWW)
     * with monotonic syncVersion tie-breaking to guard against clock skew.
     */
    fun resolveExpenseConflict(local: Expense, remote: Expense): ConflictResolutionResult<Expense> {
        val remoteWins = isRemoteWinner(local.updatedAt, local.syncVersion, remote.updatedAt, remote.syncVersion)
        return if (remoteWins) {
            ConflictResolutionResult(
                resolvedEntity = remote.copy(syncStatus = "SYNCED"),
                action = SyncAction.OVERWRITE_WITH_REMOTE
            )
        } else {
            ConflictResolutionResult(
                resolvedEntity = local.copy(syncStatus = "PENDING_PUSH"),
                action = SyncAction.USE_LOCAL
            )
        }
    }

    /**
     * Resolves conflict between local and remote SavingGoal records.
     * Incorporates high-water mark merge for saved amounts so offline deposits are preserved.
     */
    fun resolveGoalConflict(local: SavingGoal, remote: SavingGoal): ConflictResolutionResult<SavingGoal> {
        // If one is soft-deleted, honor the later timestamp / syncVersion
        if (local.isDeleted || remote.isDeleted) {
            val remoteWins = isRemoteWinner(local.updatedAt, local.syncVersion, remote.updatedAt, remote.syncVersion)
            val latest = if (remoteWins) remote else local
            return ConflictResolutionResult(
                resolvedEntity = latest.copy(syncStatus = "SYNCED"),
                action = if (latest == remote) SyncAction.OVERWRITE_WITH_REMOTE else SyncAction.USE_LOCAL
            )
        }

        val remoteWins = isRemoteWinner(local.updatedAt, local.syncVersion, remote.updatedAt, remote.syncVersion)
        return if (remoteWins) {
            // Remote has newer edits, but ensure we don't accidentally lose offline deposits
            val mergedAmount = maxOf(local.currentAmount, remote.currentAmount)
            val merged = remote.copy(
                currentAmount = mergedAmount,
                syncStatus = "SYNCED"
            )
            ConflictResolutionResult(
                resolvedEntity = merged,
                action = SyncAction.MERGED
            )
        } else {
            val mergedAmount = maxOf(local.currentAmount, remote.currentAmount)
            val merged = local.copy(
                currentAmount = mergedAmount,
                syncStatus = "PENDING_PUSH"
            )
            ConflictResolutionResult(
                resolvedEntity = merged,
                action = SyncAction.USE_LOCAL
            )
        }
    }
}

package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.core.security.FintechSecurityManager
import com.example.data.model.Expense
import com.example.data.model.SavingGoal
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val exportedEpoch: Long,
    val expensesCount: Int,
    val goalsCount: Int,
    val encryptedPayload: String
)

data class RestoredData(
    val expenses: List<Expense>,
    val goals: List<SavingGoal>
)

/**
 * UseCase for creating and restoring encrypted local financial backups using AES-256-GCM.
 */
class BackupRestoreUseCase {

    fun createBackup(
        expenses: List<Expense>,
        goals: List<SavingGoal>
    ): Resource<BackupSnapshot> {
        return try {
            val root = JSONObject()
            root.put("version", 2)
            root.put("timestamp", System.currentTimeMillis())

            val expArray = JSONArray()
            expenses.forEach { e ->
                val obj = JSONObject()
                obj.put("id", e.id)
                obj.put("userEmail", e.userEmail)
                obj.put("amount", e.amount)
                obj.put("amountPaise", e.amountPaise)
                obj.put("category", e.category)
                obj.put("date", e.date)
                obj.put("description", e.description)
                obj.put("paymentMethod", e.paymentMethod)
                obj.put("tags", e.tags)
                expArray.put(obj)
            }
            root.put("expenses", expArray)

            val goalArray = JSONArray()
            goals.forEach { g ->
                val obj = JSONObject()
                obj.put("id", g.id)
                obj.put("userEmail", g.userEmail)
                obj.put("name", g.name)
                obj.put("goalType", g.goalType)
                obj.put("targetAmount", g.targetAmount)
                obj.put("currentAmount", g.currentAmount)
                obj.put("monthlyContribution", g.monthlyContribution)
                goalArray.put(obj)
            }
            root.put("goals", goalArray)

            val plainJson = root.toString()
            val cipherText = FintechSecurityManager.encrypt(plainJson)

            Resource.Success(
                BackupSnapshot(
                    exportedEpoch = System.currentTimeMillis(),
                    expensesCount = expenses.size,
                    goalsCount = goals.size,
                    encryptedPayload = cipherText
                )
            )
        } catch (e: Exception) {
            Resource.Error("Backup failed: ${e.localizedMessage}")
        }
    }

    fun restoreBackup(
        encryptedPayload: String
    ): Resource<RestoredData> {
        return try {
            val plainJson = FintechSecurityManager.decrypt(encryptedPayload)
            val root = JSONObject(plainJson)

            val expenses = mutableListOf<Expense>()
            val expArray = root.optJSONArray("expenses") ?: JSONArray()
            for (i in 0 until expArray.length()) {
                val obj = expArray.getJSONObject(i)
                expenses.add(
                    Expense(
                        id = obj.optInt("id", 0),
                        userEmail = obj.optString("userEmail", ""),
                        amount = obj.getDouble("amount"),
                        amountPaise = obj.optLong("amountPaise", Math.round(obj.getDouble("amount") * 100)),
                        category = obj.getString("category"),
                        date = obj.getLong("date"),
                        description = obj.optString("description", obj.optString("note", "")),
                        paymentMethod = obj.optString("paymentMethod", "Cash"),
                        tags = obj.optString("tags", ""),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            val goals = mutableListOf<SavingGoal>()
            val goalArray = root.optJSONArray("goals") ?: JSONArray()
            for (i in 0 until goalArray.length()) {
                val obj = goalArray.getJSONObject(i)
                goals.add(
                    SavingGoal(
                        id = obj.optInt("id", 0),
                        userEmail = obj.optString("userEmail", ""),
                        name = obj.getString("name"),
                        goalType = obj.optString("goalType", "General"),
                        targetAmount = obj.getDouble("targetAmount"),
                        currentAmount = obj.getDouble("currentAmount"),
                        monthlyContribution = obj.optDouble("monthlyContribution", 0.0),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            Resource.Success(
                RestoredData(
                    expenses = expenses,
                    goals = goals
                )
            )
        } catch (e: Exception) {
            Resource.Error("Restore failed or corrupted payload: ${e.localizedMessage}")
        }
    }
}

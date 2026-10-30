package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.Expense
import com.example.domain.model.Money

data class ImportResult(
    val importedCount: Int,
    val skippedDuplicatesCount: Int,
    val newExpenses: List<Expense>
)

/**
 * UseCase for importing expenses from external CSV files with deduplication.
 */
class ImportExpensesUseCase {

    operator fun invoke(
        csvContent: String,
        userEmail: String,
        existingIdentifiers: Set<String>
    ): Resource<ImportResult> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return Resource.Error("CSV file is empty.")
        }

        val newExpenses = mutableListOf<Expense>()
        var skippedCount = 0

        // Determine if first line is header
        val hasHeader = lines.first().contains("amount", ignoreCase = true) ||
                lines.first().contains("category", ignoreCase = true)
        val dataLines = if (hasHeader) lines.drop(1) else lines

        for (line in dataLines) {
            val tokens = line.split(",").map { it.trim().removeSurrounding("\"") }
            if (tokens.size < 4) continue // Require at least identifier/Desc, Cat, Amount, Date

            val identifier = tokens[0]
            if (existingIdentifiers.contains(identifier)) {
                skippedCount++
                continue
            }

            val desc = if (tokens.size > 1) tokens[1] else "Imported Expense"
            val category = if (tokens.size > 2) tokens[2] else "General"
            val amountDouble = tokens.getOrNull(3)?.toDoubleOrNull() ?: continue
            val emotion = tokens.getOrNull(4) ?: "Neutral"
            val date = tokens.getOrNull(5)?.toLongOrNull() ?: System.currentTimeMillis()

            val money = Money.fromRupees(amountDouble)
            val expense = Expense(
                id = 0,
                userEmail = userEmail,
                amount = money.rupees(),
                amountPaise = money.paise,
                category = category,
                date = date,
                description = desc,
                paymentMethod = "Import",
                tags = emotion,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING_PUSH"
            )
            newExpenses.add(expense)
        }

        return Resource.Success(
            ImportResult(
                importedCount = newExpenses.size,
                skippedDuplicatesCount = skippedCount,
                newExpenses = newExpenses
            )
        )
    }
}

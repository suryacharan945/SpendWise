package com.example.util

import java.util.Locale

data class ParsedExpense(
    val amount: Double,
    val category: String,
    val description: String
)

object VoiceExpenseParser {
    private val categories = mapOf(
        "food|lunch|dinner|breakfast|coffee|restaurant|snack|burger|pizza|tea|cafe|biryani|meal|swiggy|zomato" to "Food",
        "uber|taxi|bus|metro|fuel|petrol|auto|cab|train|flight|ola|rapido|toll|parking" to "Transportation",
        "movie|netflix|game|concert|spotify|show|cinema|prime|theatre|ticket|hotstar" to "Entertainment",
        "shop|amazon|cloth|shirt|shoes|mall|flipkart|myntra|zara|jeans|dress|bag" to "Shopping",
        "electricity|bill|water|gas|wifi|recharge|broadband|current|rent|maintenance" to "Utilities",
        "medicine|doctor|pharmacy|hospital|tablet|clinic|health|apollo|medical" to "Health"
    )

    fun parse(text: String): ParsedExpense? {
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.getDefault())

        // Match amounts formatted with ₹, Rs, rupees, or standalone digits
        val amountRegex = Regex("""(?:rs\.?|rupees?|₹)?\s*(\d+(?:\.\d{1,2})?)\s*(?:rs\.?|rupees?|₹)?""")
        val match = amountRegex.find(lower)
        val amount = match?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

        var detectedCategory = "Other"
        for ((pattern, cat) in categories) {
            val words = pattern.split("|")
            if (words.any { lower.contains(it) }) {
                detectedCategory = cat
                break
            }
        }

        return ParsedExpense(
            amount = amount,
            category = detectedCategory,
            description = text.trim()
        )
    }
}

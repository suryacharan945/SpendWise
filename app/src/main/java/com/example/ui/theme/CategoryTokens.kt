package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class CategoryMeta(
    val name: String,
    val emoji: String,
    val tintColor: Color,
    val containerColor: Color
)

object CategoryTokens {
    val Food = CategoryMeta("Food", "🍔", Color(0xFFFF9F43), Color(0x33FF9F43))
    val Transportation = CategoryMeta("Transportation", "🚗", Color(0xFF54A0FF), Color(0x3354A0FF))
    val Shopping = CategoryMeta("Shopping", "🛍️", Color(0xFFFF6B6B), Color(0x33FF6B6B))
    val Entertainment = CategoryMeta("Entertainment", "🎬", Color(0xFFA55EEA), Color(0x33A55EEA))
    val Utilities = CategoryMeta("Utilities", "💡", Color(0xFFFEDA53), Color(0x33FEDA53))
    val Health = CategoryMeta("Health", "⚕️", Color(0xFF26DE81), Color(0x3326DE81))
    val Education = CategoryMeta("Education", "🎓", Color(0xFF4B7BEC), Color(0x334B7BEC))
    val Subscriptions = CategoryMeta("Subscriptions", "🔄", Color(0xFF2BCBBA), Color(0x332BCBBA))
    val Travel = CategoryMeta("Travel", "✈️", Color(0xFF45AAF2), Color(0x3345AAF2))
    val Others = CategoryMeta("Others", "📦", Color(0xFF778CA3), Color(0x33778CA3))

    val allList = listOf(
        Food,
        Transportation,
        Entertainment,
        Shopping,
        Utilities,
        Health,
        Education,
        Subscriptions,
        Travel,
        Others
    )

    fun getMeta(category: String): CategoryMeta {
        return allList.firstOrNull { it.name.equals(category, ignoreCase = true) }
            ?: if (category.equals("Other", ignoreCase = true)) Others
            else Others.copy(name = category)
    }
}

data class PaymentMethodMeta(
    val id: String,
    val label: String,
    val emoji: String,
    val tintColor: Color
)

object PaymentMethodTokens {
    val Cash = PaymentMethodMeta("Cash", "Cash", "💵", Color(0xFF10B981))
    val UPI = PaymentMethodMeta("UPI", "UPI", "📱", Color(0xFF54A0FF))
    val DebitCard = PaymentMethodMeta("Debit Card", "Debit Card", "💳", Color(0xFFF59E0B))
    val CreditCard = PaymentMethodMeta("Credit Card", "Credit Card", "🏦", Color(0xFFEC4899))
    val NetBanking = PaymentMethodMeta("Net Banking", "Net Banking", "🏛️", Color(0xFFA55EEA))
    val Others = PaymentMethodMeta("Others", "Others", "🧾", Color(0xFF778CA3))

    val allList = listOf(Cash, UPI, DebitCard, CreditCard, NetBanking, Others)

    fun getMeta(method: String): PaymentMethodMeta {
        return allList.firstOrNull { it.id.equals(method, ignoreCase = true) || it.label.equals(method, ignoreCase = true) }
            ?: if (method.contains("Card", ignoreCase = true)) DebitCard
            else if (method.contains("UPI", ignoreCase = true)) UPI
            else Others
    }
}

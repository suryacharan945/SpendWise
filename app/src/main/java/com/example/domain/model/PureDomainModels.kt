package com.example.domain.model

/**
 * Pure Kotlin Domain Models with zero Android or Room dependencies.
 */

enum class Category(val label: String) {
    FOOD("Food & Dining"),
    TRANSPORTATION("Transportation"),
    ENTERTAINMENT("Entertainment"),
    SHOPPING("Shopping"),
    UTILITIES("Bills & Utilities"),
    HEALTH("Healthcare"),
    OTHER("Miscellaneous")
}

enum class PaymentMethod {
    UPI,
    CARD,
    CASH,
    NET_BANKING
}

interface SyncableEntity {
    val updatedAt: Long
    val isDeleted: Boolean
    val syncVersion: Long        // monotonic per-device counter for tie-breaking LWW
}

data class PureExpense(
    val id: String,
    val userId: String,
    val amount: Double,
    val category: Category,
    val subCategory: String? = null,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val createdAt: Long,
    override var updatedAt: Long = createdAt,     // LWW conflict resolution key
    var deleted: Boolean = false,                 // soft delete
    override val syncVersion: Long = 1L,
) : SyncableEntity {
    override val isDeleted: Boolean get() = deleted
    val money: Money get() = Money.fromRupees(amount)
}

data class FraudAlert(
    val type: Type,
    val message: String
) {
    enum class Type {
        VELOCITY_SPIKE,
        DUPLICATE_CHARGE,
        ODD_HOUR_LARGE_AMOUNT,
        CATEGORY_ANOMALY
    }
}

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "🏆"
)

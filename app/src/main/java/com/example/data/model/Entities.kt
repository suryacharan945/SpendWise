package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.SyncableEntity

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val email: String,
    val username: String = "User",
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val monthlyBudgetLimit: Double = 30000.0,
    val dailyBudgetLimit: Double = 1000.0,
    val isBiometricsEnabled: Boolean = false,
    val themeMode: String = "System",
    val level: Int = 1,
    val xp: Int = 0,
    val netWorth: Double = 0.0,
    val annualIncome: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlySavingsGoal: Double = 0.0,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    override val syncVersion: Long = 1L
) : SyncableEntity

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["userEmail", "date"], name = "idx_expenses_user_timestamp")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String, // "Food", "Transportation", "Entertainment", "Shopping", "Utilities", "Health", "Other"
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Cash", // "Cash", "Card", "UPI", "UPI / Wallet", "Other"
    val tags: String = "", // comma-separated
    val userEmail: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    override val syncVersion: Long = 1L,
    val amountPaise: Long = Math.round(amount * 100)
) : SyncableEntity

@Entity(tableName = "saving_goals")
data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val goalType: String, // "Emergency Fund", "New Phone", "Vacation", "Bike", "House", "Custom Goal"
    val targetAmount: Double,
    val currentAmount: Double,
    val monthlyContribution: Double = 0.0,
    val userEmail: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    override val syncVersion: Long = 1L
) : SyncableEntity

@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "Stocks", "Mutual Funds", "SIPs", "Crypto"
    val name: String,
    val investedAmount: Double,
    val currentValue: Double,
    val userEmail: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    override val syncVersion: Long = 1L
) : SyncableEntity

@Entity(tableName = "financial_journal_entries")
data class FinancialJournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userEmail: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    override val syncVersion: Long = 1L
) : SyncableEntity

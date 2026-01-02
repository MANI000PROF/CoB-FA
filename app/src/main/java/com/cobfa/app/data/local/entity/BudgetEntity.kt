package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cobfa.app.domain.model.ExpenseCategory

/**
 * Represents a monthly budget for a specific category.
 *
 * Example:
 * - category = FOOD
 * - amount = 5000.0
 * - monthStart = timestamp for 1 Dec 2025 00:00:00
 */
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["category", "monthStart"], unique = true)
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val category: ExpenseCategory,

    // Target amount for this category in this month
    val amount: Double,

    // Timestamp representing the first day of the month (normalized at 00:00:00)
    val monthStart: Long,

    // Whether alerts (80% / 100%) should be triggered for this budget
    val alertsEnabled: Boolean = true,

    // Audit fields
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

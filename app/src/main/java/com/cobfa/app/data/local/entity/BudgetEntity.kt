package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cobfa.app.domain.model.ExpenseCategory

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
    val amount: Double,
    val monthStart: Long,
    val alertsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

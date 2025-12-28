package com.cobfa.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseSource
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["smsHash"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amount: Double,
    val type: ExpenseType,
    val category: ExpenseCategory?,
    val merchant: String?,
    val timestamp: Long,
    val source: ExpenseSource,
    val status: ExpenseStatus,
    val createdAt: Long,
    val smsHash: String?
)

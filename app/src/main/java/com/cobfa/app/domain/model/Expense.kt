package com.cobfa.app.domain.model

data class Expense(
    val id: Long = 0L,
    val amount: Double,
    val type: ExpenseType,
    val category: ExpenseCategory?,
    val merchant: String?,
    val timestamp: Long,
    val source: ExpenseSource,
    val status: ExpenseStatus,
    val createdAt: Long
)

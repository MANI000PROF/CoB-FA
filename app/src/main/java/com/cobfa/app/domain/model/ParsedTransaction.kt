package com.cobfa.app.domain.model

data class ParsedTransaction(
    val amount: Double,
    val type: ExpenseType,
    val merchant: String?
)

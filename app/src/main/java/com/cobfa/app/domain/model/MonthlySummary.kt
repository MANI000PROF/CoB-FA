package com.cobfa.app.domain.model

data class MonthlySummary(
    val monthStart: Long,
    val income: Double,
    val expense: Double
) {
    val balance: Double
        get() = income - expense
}

package com.cobfa.app.data.repository

import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.domain.model.MonthlySummary

class AnalyticsRepository(
    private val expenseDao: ExpenseDao
) {

    suspend fun getMonthlySummary(
        start: Long,
        end: Long
    ): MonthlySummary {

        val expenses = expenseDao.getExpensesBetween(start, end)

        val income = expenses
            .filter { it.type == ExpenseType.CREDIT }
            .sumOf { it.amount }

        val expense = expenses
            .filter { it.type == ExpenseType.DEBIT }
            .sumOf { it.amount }

        return MonthlySummary(
            monthStart = start,
            income = income,
            expense = expense
        )
    }
}

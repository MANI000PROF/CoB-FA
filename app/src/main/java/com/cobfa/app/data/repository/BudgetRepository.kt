package com.cobfa.app.data.repository

import com.cobfa.app.data.local.dao.BudgetDao
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

class BudgetRepository(
    private val budgetDao: BudgetDao
) {

    fun observeBudgetsForMonth(monthStart: Long): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(monthStart)

    suspend fun upsertBudget(
        category: ExpenseCategory,
        amount: Double,
        monthStart: Long,
        alertsEnabled: Boolean = true,
        syncManager: SyncManager? = null
    ): Long {
        val now = System.currentTimeMillis()
        val existing = budgetDao.getBudgetForCategory(category, monthStart)

        val entity = BudgetEntity(
            id = existing?.id ?: 0L,
            category = category,
            amount = amount,
            monthStart = monthStart,
            alertsEnabled = alertsEnabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        val id = budgetDao.upsertBudget(entity)
        syncManager?.syncBudgetsForMonth(monthStart)
        return id
    }

    suspend fun deleteBudget(
        category: ExpenseCategory,
        monthStart: Long,
        syncManager: SyncManager? = null
    ) {
        budgetDao.deleteBudget(category, monthStart)
        syncManager?.syncBudgetsForMonth(monthStart)
    }

    private fun normalizeMonthStart(timestamp: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    data class BudgetUsage(
        val category: ExpenseCategory,
        val budgetAmount: Double,
        val spentAmount: Double,
        val percentageUsed: Int,
        val alertsEnabled: Boolean
    )

    suspend fun getBudgetUsageForMonth(
        monthStart: Long,
        expenseDao: ExpenseDao
    ): List<BudgetUsage> {
        val normalizedMonthStart = normalizeMonthStart(monthStart)
        val budgets = budgetDao.getCurrentBudgetsForMonth(normalizedMonthStart)
        val monthEnd = getMonthEnd(normalizedMonthStart)

        return budgets.map { budget ->
            val spent = expenseDao.getSpentAmountByCategory(
                budget.category,
                normalizedMonthStart,
                monthEnd
            )
            val percentage = if (budget.amount > 0) {
                (spent / budget.amount * 100).toInt().coerceAtLeast(0)
            } else 0

            BudgetUsage(
                category = budget.category,
                budgetAmount = budget.amount,
                spentAmount = spent,
                percentageUsed = percentage,
                alertsEnabled = budget.alertsEnabled
            )
        }
    }

    private fun getMonthEnd(monthStart: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = monthStart
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}

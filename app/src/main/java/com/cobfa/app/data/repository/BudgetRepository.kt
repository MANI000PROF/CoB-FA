package com.cobfa.app.data.repository

import android.util.Log
import com.cobfa.app.data.local.dao.BudgetDao
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

class BudgetRepository(
    private val budgetDao: BudgetDao
) {

    /**
     * Save or update budget for category + month.
     */
    suspend fun upsertBudget(
        category: ExpenseCategory,
        amount: Double,
        monthStart: Long,
        syncManager: SyncManager? = null  // NEW: optional sync
    ): Long {
        val normalizedMonthStart = normalizeMonthStart(monthStart)

        val budget = BudgetEntity(
            category = category,
            amount = amount,
            monthStart = normalizedMonthStart,
            alertsEnabled = true
        )

        val id = budgetDao.upsertBudget(budget)

        // NEW: Auto-sync to Firestore
        syncManager?.syncBudgetsForMonth(normalizedMonthStart)

        return id
    }

    /**
     * Delete budget for category + month.
     */
    suspend fun deleteBudget(category: ExpenseCategory, monthStart: Long) {
        val normalizedMonthStart = normalizeMonthStart(monthStart)
        budgetDao.deleteBudget(category, normalizedMonthStart)
    }

    /**
     * Get budgets for a specific month.
     */
    fun getBudgetsForMonth(monthStart: Long): Flow<List<BudgetEntity>> {
        val normalizedMonthStart = normalizeMonthStart(monthStart)
        return budgetDao.getBudgetsForMonth(normalizedMonthStart)
    }

    /**
     * Get budget for specific category + month.
     */
    suspend fun getBudgetForCategory(
        category: ExpenseCategory,
        monthStart: Long
    ): BudgetEntity? {
        val normalizedMonthStart = normalizeMonthStart(monthStart)
        return budgetDao.getBudgetForCategory(category, normalizedMonthStart)
    }

    /**
     * Normalize monthStart timestamp to 1st of month at 00:00:00 UTC.
     * Ensures consistent querying regardless of timezone/milliseconds.
     */
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

    /**
     * Get budget usage summary for all categories in a month.
     * Returns categories with budgets + their spent % (0-200+ for overspend).
     */
    suspend fun getBudgetUsageForMonth(
        monthStart: Long,
        expenseDao: ExpenseDao
    ): List<BudgetUsage> {
        val normalizedMonthStart = normalizeMonthStart(monthStart)
        Log.d("BUDGET_DEBUG", "ALERT query monthStart: $normalizedMonthStart")
        val budgets = budgetDao.getCurrentBudgetsForMonth(normalizedMonthStart)
        Log.d("BUDGET_DEBUG", "Found ${budgets.size} budgets for alerts")
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

    data class BudgetUsage(
        val category: ExpenseCategory,
        val budgetAmount: Double,
        val spentAmount: Double,
        val percentageUsed: Int,
        val alertsEnabled: Boolean
    )

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

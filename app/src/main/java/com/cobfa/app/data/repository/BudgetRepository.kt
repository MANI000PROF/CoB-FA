package com.cobfa.app.data.repository

import com.cobfa.app.data.local.dao.BudgetDao
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

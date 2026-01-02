package com.cobfa.app.data.repository

import android.util.Log
import androidx.room.Transaction
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.utils.ExpenseLogger
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val syncManager: SyncManager? = null
) {

    suspend fun insertExpense(expense: ExpenseEntity): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }

    fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getAllExpenses()
    }

    fun getPendingExpenses(): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByStatus(ExpenseStatus.PENDING)

    suspend fun confirmExpense(id: Long, category: ExpenseCategory) {
        ExpenseLogger.logConfirmationStart(id)

        try {
            // Validate category
            if (category == null) {
                throw IllegalArgumentException("Category cannot be null for confirmation")
            }

            // ✅ Confirm atomically (single transaction)
            confirmExpenseAtomic(id, category)

            ExpenseLogger.logConfirmationComplete(id, category.name, ExpenseStatus.CONFIRMED.name)

            // ✅ NEW: Sync to Firestore immediately after confirmation
            syncManager?.syncConfirmedExpense(id)

        } catch (e: Exception) {
            ExpenseLogger.logConfirmationError(id, e.message ?: "Unknown error")
            throw e
        }
    }


    /**
     * Atomically confirm an expense.
     * Single database transaction ensures no partial updates.
     */
    @Transaction
    private suspend fun confirmExpenseAtomic(id: Long, category: ExpenseCategory) {
        // ✅ INLINE the query - keeps transaction context intact
        // Step 1: Get current expense (MUST be inside @Transaction block)
        val expense = expenseDao.getExpenseById(id)
            ?: throw IllegalArgumentException("Expense with id=$id not found")

        // Step 2: Validate current state (prevent re-confirmation)
        if (expense.status == ExpenseStatus.CONFIRMED) {
            ExpenseLogger.logConfirmationAlreadyDone(id)
            return
        }

        // Step 3: Update atomically
        // ✅ This is now in the SAME transaction as the read
        expenseDao.confirmExpense(id, category, ExpenseStatus.CONFIRMED)
    }

    // ✅ NEW: Helper method for validation
    suspend fun getExpenseById(id: Long): ExpenseEntity? {
        return try {
            expenseDao.getExpenseById(id)
        } catch (e: Exception) {
            ExpenseLogger.logDatabaseError("getExpenseById($id)", e.message ?: "Unknown")
            null
        }
    }

    suspend fun existsBySmsHash(hash: String): Boolean {
        return expenseDao.countBySmsHash(hash) > 0
    }

    fun getConfirmedExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getExpensesByStatus(ExpenseStatus.CONFIRMED)
    }

    /**
     * Get total DEBIT spending for a category within date range.
     * Used for budget progress calculation.
     */
    suspend fun getSpentAmountByCategory(
        category: ExpenseCategory,
        start: Long,
        end: Long
    ): Double {
        return expenseDao.getSpentAmountByCategory(category, start, end)
    }


}

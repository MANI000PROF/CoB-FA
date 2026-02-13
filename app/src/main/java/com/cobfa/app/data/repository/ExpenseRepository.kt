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
            val updated = expenseDao.confirmExpenseSafe(id, category)
            if (updated <= 0) {
                ExpenseLogger.logConfirmationError(id, "DB update affected 0 rows (missing id or mismatch)")
                return
            }

            ExpenseLogger.logConfirmationComplete(id, category.name, ExpenseStatus.CONFIRMED.name)
            syncManager?.syncConfirmedExpense(id)

        } catch (e: Exception) {
            ExpenseLogger.logConfirmationError(id, e.message ?: "Unknown error")
            throw e
        }
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

    suspend fun getSpentAmountByCategory(
        category: ExpenseCategory,
        start: Long,
        end: Long
    ): Double {
        return expenseDao.getSpentAmountByCategory(category, start, end)
    }

    suspend fun confirmExpenseBySmsHash(smsHash: String, category: ExpenseCategory) {
        ExpenseLogger.logConfirmationStart(-1)

        val updated = expenseDao.confirmExpenseBySmsHash(
            smsHash = smsHash,
            category = category,
            status = ExpenseStatus.CONFIRMED
        )

        if (updated <= 0) {
            ExpenseLogger.logConfirmationError(-1, "No row updated for smsHash=$smsHash")
            return
        }

        val confirmed = expenseDao.getExpenseBySmsHash(smsHash)
        if (confirmed != null) {
            ExpenseLogger.logConfirmationComplete(confirmed.id, category.name, ExpenseStatus.CONFIRMED.name)
            syncManager?.syncConfirmedExpense(confirmed.id)
        } else {
            ExpenseLogger.logConfirmationError(-1, "Confirmed row not found after update for smsHash=$smsHash")
        }
    }
}

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

    fun getPendingExpenses(): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByStatus(ExpenseStatus.PENDING)


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

    suspend fun updateExpenseCategory(id: Long, category: ExpenseCategory) {
        val updated = expenseDao.updateCategory(id, category)
        if (updated <= 0) {
            ExpenseLogger.logDatabaseError("updateExpenseCategory($id)", "DB update affected 0 rows")
            return
        }
        // Optional: if you want Firestore to reflect category changes
        syncManager?.syncConfirmedExpense(id)
    }

    suspend fun softDeleteExpense(id: Long) {
        val updated = expenseDao.updateStatus(id, ExpenseStatus.DELETED)
        if (updated > 0) {
            syncManager?.syncConfirmedExpense(id)
        }
    }

    suspend fun restoreExpense(id: Long) {
        val updated = expenseDao.updateStatus(id, ExpenseStatus.CONFIRMED)
        if (updated > 0) {
            syncManager?.syncConfirmedExpense(id)
        }
    }

    suspend fun editExpense(id: Long, merchant: String?, amount: Double, timestamp: Long) {
        val updated = expenseDao.updateExpenseCore(
            id = id,
            merchant = merchant?.trim()?.ifBlank { null },
            amount = amount,
            timestamp = timestamp,
            editedAt = System.currentTimeMillis()
        )
        if (updated > 0) {
            syncManager?.syncConfirmedExpense(id)
        } else {
            ExpenseLogger.logDatabaseError("editExpense($id)", "DB update affected 0 rows")
        }
    }

    fun observeConfirmedActiveNewest(): Flow<List<ExpenseEntity>> =
        expenseDao.observeConfirmedNewest()

    fun observeConfirmedActiveOldest(): Flow<List<ExpenseEntity>> =
        expenseDao.observeConfirmedOldest()

    fun observeConfirmedAllNewest(): Flow<List<ExpenseEntity>> =
        expenseDao.observeConfirmedIncludingDeletedNewest()

    fun observeConfirmedAllOldest(): Flow<List<ExpenseEntity>> =
        expenseDao.observeConfirmedIncludingDeletedOldest()

    fun observeDeletedNewest(): Flow<List<ExpenseEntity>> =
        expenseDao.observeDeletedNewest()

    fun observeDeletedOldest(): Flow<List<ExpenseEntity>> =
        expenseDao.observeDeletedOldest()
}

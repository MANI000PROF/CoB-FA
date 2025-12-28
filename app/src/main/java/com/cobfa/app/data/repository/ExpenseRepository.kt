package com.cobfa.app.data.repository

import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseStatus
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao
) {

    suspend fun insertExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
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
        expenseDao.confirmExpense(id = id, category = category.name, status = ExpenseStatus.CONFIRMED.name)
    }

    suspend fun existsBySmsHash(hash: String): Boolean {
        return expenseDao.countBySmsHash(hash) > 0
    }

    fun getConfirmedExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getExpensesByStatus(ExpenseStatus.CONFIRMED)
    }


}

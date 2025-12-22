package com.cobfa.app.data.repository

import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.ExpenseEntity
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

    fun getPendingExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getPendingExpenses()
    }
}

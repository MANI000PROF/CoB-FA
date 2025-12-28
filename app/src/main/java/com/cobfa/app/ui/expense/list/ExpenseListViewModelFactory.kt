package com.cobfa.app.ui.expense.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.repository.ExpenseRepository

class ExpenseListViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseListViewModel::class.java)) {

            val db = ExpenseDatabase.getInstance(context.applicationContext)
            val repo = ExpenseRepository(db.expenseDao())

            @Suppress("UNCHECKED_CAST")
            return ExpenseListViewModel(repo) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

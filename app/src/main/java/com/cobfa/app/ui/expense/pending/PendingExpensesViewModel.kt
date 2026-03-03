package com.cobfa.app.ui.expense.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.launch

class PendingExpensesViewModel(
    private val repo: ExpenseRepository
) : ViewModel() {

    val pendingExpenses = repo.getPendingExpenses()

    fun confirmBySmsHash(smsHash: String, category: ExpenseCategory) {
        viewModelScope.launch {
            repo.confirmExpenseBySmsHash(smsHash, category)
        }
    }

    fun ignoreById(id: Long) {
        viewModelScope.launch {
            repo.ignoreExpenseById(id)
        }
    }
}


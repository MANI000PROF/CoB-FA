package com.cobfa.app.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.repository.AnalyticsRepository

class DashboardViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = ExpenseDatabase.getInstance(context)
        val repo = AnalyticsRepository(db.expenseDao())

        return DashboardViewModel(repo) as T
    }
}

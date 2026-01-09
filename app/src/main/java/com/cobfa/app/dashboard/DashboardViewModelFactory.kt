package com.cobfa.app.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.AnalyticsRepository
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager

class DashboardViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = ExpenseDatabase.getInstance(context)
        val analyticsRepo = AnalyticsRepository(db.expenseDao())
        val firestoreService = FirestoreService()
        val syncManager = SyncManager(db, firestoreService)

        return DashboardViewModel(analyticsRepo, syncManager, db, context) as T
    }
}

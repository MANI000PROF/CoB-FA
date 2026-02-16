package com.cobfa.app.data.repository

import android.util.Log
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncManager(
    private val expenseDb: ExpenseDatabase,
    private val firestoreService: FirestoreService
) {

    suspend fun syncConfirmedExpense(expenseId: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d("SYNC_MANAGER", "Syncing expense: $expenseId")

            val expense = expenseDb.expenseDao().getExpenseById(expenseId) ?: return@withContext

            // For ongoing changes (category/status), use partial update.
            val upd = firestoreService.updateExpense(expense)
            if (upd.isSuccess) {
                Log.d("SYNC_MANAGER", "updateExpense success: $expenseId")
                return@withContext
            }

            // Fallback: if doc doesn’t exist yet (or update path fails), upsert full doc.
            firestoreService.backupExpense(expense)
            Log.d("SYNC_MANAGER", "backupExpense fallback success: $expenseId")
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Error syncing expense: ${e.message}", e)
        }
    }

    suspend fun restoreFromFirestore() = withContext(Dispatchers.IO) {
        try {
            Log.d("SYNC_MANAGER", "Starting Firestore restore...")

            val result = firestoreService.fetchAllConfirmedExpenses()

            result.onSuccess { expenses ->
                Log.d("SYNC_MANAGER", "Restoring ${expenses.size} expenses to local DB")

                // Insert all expenses that aren't already in local DB
                for (expense in expenses) {
                    try {
                        val existing = expenseDb.expenseDao().getExpenseById(expense.id)
                        if (existing == null) {
                            expenseDb.expenseDao().insertExpense(expense)
                            Log.d("SYNC_MANAGER", "Restored expense: ${expense.id}")
                        }
                    } catch (e: Exception) {
                        Log.e("SYNC_MANAGER", "Error restoring expense: ${e.message}")
                    }
                }

                Log.d("SYNC_MANAGER", "Firestore restore completed")
            }

            result.onFailure { error ->
                Log.e("SYNC_MANAGER", "Error restoring from Firestore: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Fatal error in restore: ${e.message}")
        }
    }

    suspend fun getProcessedSmsHashes(): Set<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("SYNC_MANAGER", "Fetching processed SMS hashes...")

            val result = firestoreService.getProcessedSmsHashes()

            result.onSuccess { hashes ->
                Log.d("SYNC_MANAGER", "Found ${hashes.size} processed SMS hashes")
            }

            result.onFailure { error ->
                Log.e("SYNC_MANAGER", "Error fetching SMS hashes: ${error.message}")
            }

            result.getOrNull() ?: emptySet()
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Fatal error getting SMS hashes: ${e.message}")
            emptySet()
        }
    }

    suspend fun isSmSProcessed(smsHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val hashes = getProcessedSmsHashes()
            hashes.contains(smsHash)
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Error checking SMS: ${e.message}")
            false
        }
    }

    suspend fun syncBudgetsForMonth(monthStart: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d("SYNC_MANAGER", "Syncing budgets for month: $monthStart")

            val budgets = expenseDb.budgetDao().getBudgetsForMonth(monthStart).first()

            if (budgets.isEmpty()) {
                Log.d("SYNC_MANAGER", "No budgets to sync for month $monthStart")
                return@withContext
            }

            val result = firestoreService.backupBudgetsForMonth(monthStart, budgets)

            result.onSuccess {
                Log.d("BUDGET_SYNC", "Successfully synced ${budgets.size} budgets")
            }
            result.onFailure { e ->
                Log.e("BUDGET_SYNC", "Error syncing budgets: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Error syncing budgets for month $monthStart: ${e.message}")
        }
    }

    suspend fun restoreBudgetsFromFirestore() = withContext(Dispatchers.IO) {
        try {
            Log.d("SYNC_MANAGER", "Starting budget restore from Firestore...")

            val result = firestoreService.fetchAllBudgets()

            result.onSuccess { budgetsByMonth ->
                Log.d("SYNC_MANAGER", "Restoring budgets for ${budgetsByMonth.size} months")

                for ((_, budgets) in budgetsByMonth) {
                    for (budget in budgets) {
                        try {
                            expenseDb.budgetDao().upsertBudget(budget)
                        } catch (e: Exception) {
                            Log.e("SYNC_MANAGER", "Error restoring budget ${budget.category}: ${e.message}")
                        }
                    }
                }

                Log.d("SYNC_MANAGER", "Budget restore completed")
            }

            result.onFailure { error ->
                Log.e("SYNC_MANAGER", "Error restoring budgets: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Fatal error in budget restore: ${e.message}")
        }
    }

}

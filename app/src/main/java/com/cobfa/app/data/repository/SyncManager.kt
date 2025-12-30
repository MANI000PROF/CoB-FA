package com.cobfa.app.data.repository

import android.util.Log
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages sync between local DB and Firestore
 *
 * Responsibilities:
 * - Backup new confirmed expenses
 * - Restore expenses on app reinstall
 * - Calculate which SMS are already processed
 */
class SyncManager(
    private val expenseDb: ExpenseDatabase,
    private val firestoreService: FirestoreService
) {

    /**
     * Called when user confirms an expense
     * Syncs immediately to Firestore for backup
     */
    suspend fun syncConfirmedExpense(expenseId: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d("SYNC_MANAGER", "Syncing confirmed expense: $expenseId")

            val expense = expenseDb.expenseDao().getExpenseById(expenseId)
                ?: return@withContext

            firestoreService.backupExpense(expense)
            Log.d("SYNC_MANAGER", "Successfully synced expense: $expenseId")
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Error syncing expense: ${e.message}")
        }
    }

    /**
     * Called on app launch (after login)
     * Restores all confirmed expenses from Firestore and saves to local DB
     */
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

    /**
     * Get set of SMS hashes that are already processed
     * Used to filter SMS inbox to show only NEW expenses
     */
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

    /**
     * Check if a specific SMS is already processed
     */
    suspend fun isSmSProcessed(smsHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val hashes = getProcessedSmsHashes()
            hashes.contains(smsHash)
        } catch (e: Exception) {
            Log.e("SYNC_MANAGER", "Error checking SMS: ${e.message}")
            false
        }
    }
}

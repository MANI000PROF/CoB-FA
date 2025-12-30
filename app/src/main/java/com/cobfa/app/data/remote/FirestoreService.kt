package com.cobfa.app.data.remote

import android.util.Log
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseSource
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Manages Firestore operations for expense backup and recovery
 *
 * Collection structure:
 * users/{uid}/confirmed_expenses/{expenseId}
 * - Each document = one confirmed expense
 */
class FirestoreService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val collectionPath = "users"
    private val subCollectionPath = "confirmed_expenses"

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Backup a confirmed expense to Firestore
     * Called immediately after user confirms an expense
     */
    suspend fun backupExpense(expense: ExpenseEntity): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Backing up expense: ${expense.id}")

            val expenseData = mapOf(
                "id" to expense.id,
                "amount" to expense.amount,
                "type" to expense.type.name,
                "source" to expense.source.name,
                "merchant" to expense.merchant,
                "timestamp" to expense.timestamp,
                "smsHash" to expense.smsHash,
                "category" to (expense.category?.name ?: "General"),
                "status" to expense.status.name,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection(collectionPath)
                .document(userId)
                .collection(subCollectionPath)
                .document(expense.id.toString())
                .set(expenseData)
                .await()

            Log.d("FIRESTORE_SYNC", "Successfully backed up expense: ${expense.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error backing up expense: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetch all confirmed expenses from Firestore
     * Called on app launch to restore expense history
     */
    suspend fun fetchAllConfirmedExpenses(): Result<List<ExpenseEntity>> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Fetching confirmed expenses from Firestore")

            val snapshot = db.collection(collectionPath)
                .document(userId)
                .collection(subCollectionPath)
                .get()
                .await()

            val expenses = snapshot.documents.mapNotNull { doc ->
                try {
                    ExpenseEntity(
                        id = doc.getLong("id") ?: return@mapNotNull null,
                        amount = doc.getDouble("amount") ?: 0.0,
                        type = ExpenseType.valueOf(doc.getString("type") ?: "DEBIT"),
                        category = doc.getString("category")?.let {
                            try {
                                ExpenseCategory.valueOf(it)
                            } catch (e: Exception) {
                                null
                            }
                        },
                        merchant = doc.getString("merchant"),
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        source = ExpenseSource.valueOf(doc.getString("source") ?: "SMS"),
                        status = ExpenseStatus.valueOf(doc.getString("status") ?: "CONFIRMED"),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        smsHash = doc.getString("smsHash")
                    )
                } catch (e: Exception) {
                    Log.e("FIRESTORE_SYNC", "Error parsing expense document: ${e.message}")
                    null
                }
            }

            Log.d("FIRESTORE_SYNC", "Fetched ${expenses.size} confirmed expenses from Firestore")
            Result.success(expenses)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error fetching expenses: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all SMS hashes of already-processed expenses
     * Used to filter out duplicates from SMS inbox
     */
    suspend fun getProcessedSmsHashes(): Result<Set<String>> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Fetching processed SMS hashes")

            val snapshot = db.collection(collectionPath)
                .document(userId)
                .collection(subCollectionPath)
                .get()
                .await()

            val hashes = snapshot.documents.mapNotNull { doc ->
                doc.getString("smsHash")
            }.toSet()

            Log.d("FIRESTORE_SYNC", "Found ${hashes.size} processed SMS hashes")
            Result.success(hashes)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error fetching SMS hashes: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update an expense in Firestore
     * (If category changes after confirmation)
     */
    suspend fun updateExpense(expense: ExpenseEntity): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Updating expense: ${expense.id}")

            val expenseData = mapOf(
                "category" to expense.category?.name,
                "status" to expense.status.name,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection(collectionPath)
                .document(userId)
                .collection(subCollectionPath)
                .document(expense.id.toString())
                .update(expenseData)
                .await()

            Log.d("FIRESTORE_SYNC", "Successfully updated expense: ${expense.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error updating expense: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete an expense from Firestore
     * (If user wants to discard a confirmed expense)
     */
    suspend fun deleteExpense(expenseId: Long): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Deleting expense: $expenseId")

            db.collection(collectionPath)
                .document(userId)
                .collection(subCollectionPath)
                .document(expenseId.toString())
                .delete()
                .await()

            Log.d("FIRESTORE_SYNC", "Successfully deleted expense: $expenseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error deleting expense: ${e.message}")
            Result.failure(e)
        }
    }
}

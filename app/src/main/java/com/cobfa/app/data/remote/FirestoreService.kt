package com.cobfa.app.data.remote

import android.util.Log
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseSource
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val collectionPath = "users"
    private val subCollectionPath = "confirmed_expenses"

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // Public leaderboard collection (safe fields only)
    private val publicUsersCollection = "users_public"

    data class PublicUser(
        val uid: String = "",
        val username: String = "",
        val city: String = "",
        val state: String = "",
        val country: String = "India",
        val pointsBalance: Int = 0,
        val updatedAt: Long = 0L
    )

    suspend fun upsertPublicUser(
        username: String,
        city: String,
        state: String,
        country: String = "India",
        pointsBalance: Int
    ): Result<Unit> {
        return try {
            val uid = currentUserId ?: return Result.failure(Exception("User not logged in"))

            val data = mapOf(
                "uid" to uid,
                "username" to username,
                "city" to city.trim(),
                "state" to state.trim(),
                "country" to country.trim(),
                "pointsBalance" to pointsBalance,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection(publicUsersCollection)
                .document(uid)
                .set(data)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCityLeaderboard(
        city: String,
        state: String,
        limit: Long = 50
    ): Result<List<PublicUser>> {
        return try {
            val snap = db.collection(publicUsersCollection)
                .whereEqualTo("city", city.trim())
                .whereEqualTo("state", state.trim())
                .orderBy("pointsBalance", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            Result.success(snap.toObjects(PublicUser::class.java))
        } catch (e: Exception) {
            // If Firestore asks for an index, it will throw an error with a link to create it. [web:357]
            Result.failure(e)
        }
    }

    suspend fun fetchStateLeaderboard(
        state: String,
        limit: Long = 50
    ): Result<List<PublicUser>> {
        return try {
            val snap = db.collection(publicUsersCollection)
                .whereEqualTo("state", state.trim())
                .orderBy("pointsBalance", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            Result.success(snap.toObjects(PublicUser::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun incrementPublicPoints(delta: Int): Result<Unit> {
        return try {
            val uid = currentUserId ?: return Result.failure(Exception("User not logged in"))
            db.collection(publicUsersCollection)
                .document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "pointsBalance" to FieldValue.increment(delta.toLong()),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun claimUsername(username: String): Result<Unit> {
        return try {
            val uid = currentUserId ?: return Result.failure(Exception("User not logged in"))
            val key = username.trim().lowercase()
            val ref = db.collection("usernames").document(key)

            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (snap.exists()) {
                    val ownerUid = snap.getString("uid")
                    if (ownerUid == uid) {
                        // Already claimed by this user -> idempotent success
                        return@runTransaction null
                    } else {
                        throw IllegalStateException("Username taken")
                    }
                }
                tx.set(
                    ref,
                    mapOf(
                        "uid" to uid,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "claimUsername($username) failed", e)
            Result.failure(e)
        }
    }

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
                "createdAt" to expense.createdAt,
                "updatedAt" to System.currentTimeMillis(),
                "editedAt" to expense.editedAt,
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

    suspend fun updateExpense(expense: ExpenseEntity): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            val expenseData = mapOf(
                "merchant" to expense.merchant,
                "amount" to expense.amount,
                "timestamp" to expense.timestamp,
                "category" to expense.category?.name,
                "status" to expense.status.name,
                "editedAt" to expense.editedAt,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection(collectionPath)
                .document(userId)
                .collection(subCollectionPath)
                .document(expense.id.toString())
                .update(expenseData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun backupBudgetsForMonth(
        monthStart: Long,
        budgets: List<BudgetEntity>
    ): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Backing up ${budgets.size} budgets for month $monthStart")

            val budgetData = budgets.map { budget ->
                mapOf(
                    "category" to budget.category.name,
                    "amount" to budget.amount,
                    "alertsEnabled" to budget.alertsEnabled
                )
            }

            val monthDocData = mapOf(
                "monthStart" to monthStart,
                "budgets" to budgetData,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection(collectionPath)
                .document(userId)
                .collection("budgets")
                .document(monthStart.toString())
                .set(monthDocData)
                .await()

            Log.d("FIRESTORE_SYNC", "Successfully backed up budgets for month $monthStart")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error backing up budgets: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchAllBudgets(): Result<Map<Long, List<BudgetEntity>>> {
        return try {
            val userId = currentUserId ?: return Result.failure(Exception("User not logged in"))

            Log.d("FIRESTORE_SYNC", "Fetching all budgets from Firestore")

            val snapshot = db.collection(collectionPath)
                .document(userId)
                .collection("budgets")
                .get()
                .await()

            val budgetsByMonth = mutableMapOf<Long, List<BudgetEntity>>()

            for (doc in snapshot.documents) {
                try {
                    val monthStart = doc.getLong("monthStart") ?: continue
                    val budgetArray = doc.get("budgets") as? List<Map<String, Any>> ?: continue

                    val budgets = budgetArray.mapNotNull { budgetMap ->
                        try {
                            BudgetEntity(
                                category = ExpenseCategory.valueOf(budgetMap["category"] as String),
                                amount = (budgetMap["amount"] as? Number)?.toDouble() ?: 0.0,
                                monthStart = monthStart,
                                alertsEnabled = budgetMap["alertsEnabled"] as? Boolean ?: true,
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                updatedAt = doc.getLong("updatedAt") ?: 0L
                            )
                        } catch (e: Exception) {
                            Log.e("FIRESTORE_SYNC", "Error parsing budget: ${e.message}")
                            null
                        }
                    }

                    if (budgets.isNotEmpty()) {
                        budgetsByMonth[monthStart] = budgets
                    }
                } catch (e: Exception) {
                    Log.e("FIRESTORE_SYNC", "Error processing budget document ${doc.id}: ${e.message}")
                }
            }

            Log.d("FIRESTORE_SYNC", "Fetched ${budgetsByMonth.size} budget months")
            Result.success(budgetsByMonth)
        } catch (e: Exception) {
            Log.e("FIRESTORE_SYNC", "Error fetching budgets: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchMyPublicUser(): Result<PublicUser> {
        return try {
            val uid = currentUserId ?: return Result.failure(Exception("User not logged in"))
            val snap = db.collection(publicUsersCollection).document(uid).get().await()
            val u = snap.toObject(PublicUser::class.java) ?: PublicUser(uid = uid)
            Result.success(u.copy(uid = uid))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}

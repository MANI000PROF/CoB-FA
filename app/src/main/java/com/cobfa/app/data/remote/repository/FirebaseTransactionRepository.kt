package com.cobfa.app.data.remote.repository

import com.cobfa.app.data.local.entity.ExpenseEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseTransactionRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser!!.uid

    suspend fun fetchAllTransactions(): List<ExpenseEntity> {
        val snapshot = firestore
            .collection("users")
            .document(uid)
            .collection("transactions")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(ExpenseEntity::class.java)
        }
    }
}

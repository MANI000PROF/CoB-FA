package com.cobfa.app.sms

import android.content.Context
import android.util.Log
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmsProcessor {

    private const val TAG = "SMS_PROCESSOR"

    fun process(
        context: Context,
        sender: String?,
        body: String,
        timestamp: Long
    ) {
        val expense = TransactionDetector.detect(sender, body, timestamp)
            ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val db = ExpenseDatabase.getInstance(context)
            val repo = ExpenseRepository(db.expenseDao())

            val finalExpense =
                if (expense.type == ExpenseType.CREDIT) {
                    expense.copy(
                        status = ExpenseStatus.CONFIRMED,
                        category = null
                    )
                } else {
                    expense.copy(
                        status = ExpenseStatus.PENDING
                    )
                }

            try {
                repo.insertExpense(finalExpense)
                Log.d(TAG, "Expense saved to DB")
            } catch (e: Exception) {
                Log.d(TAG, "Duplicate SMS ignored by DB")
            }
        }
    }
}

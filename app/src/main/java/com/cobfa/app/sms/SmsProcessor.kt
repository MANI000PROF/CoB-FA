package com.cobfa.app.sms

import android.util.Log
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.utils.ExpenseLogger

object SmsProcessor {

    suspend fun processWithDedup(
        sender: String?,
        body: String,
        timestamp: Long,
        repo: ExpenseRepository,
        syncManager: SyncManager? = null
    ): Boolean {

        val expense = TransactionDetector.detect(sender, body, timestamp)
            ?: return false

        val smsHash = expense.smsHash ?: return false

        // ✅ Pre-flight check
        if (repo.existsBySmsHash(smsHash)) {
            ExpenseLogger.logSmsDuplicate(smsHash)
            return false
        }

        // Check Firestore (for restored expenses)
        if (syncManager != null && syncManager.isSmSProcessed(smsHash)) {
            ExpenseLogger.logValidationFailed("sms", "duplicate_firestore", "SMS already processed (Firestore): $smsHash")
            Log.d("SMS_FILTER", "SMS already processed (from Firestore): $smsHash")
            return false
        }

        // ✅ Prepare final expense
        val finalExpense = if (expense.type == ExpenseType.CREDIT) {
            expense.copy(status = ExpenseStatus.CONFIRMED, category = null)
        } else {
            expense.copy(status = ExpenseStatus.PENDING)
        }

        // ✅ Insert with proper logging
        return try {
            val insertedId = repo.insertExpense(finalExpense)
            if (insertedId == -1L) {
                ExpenseLogger.logSmsDuplicate(smsHash)
                return false
            }

            ExpenseLogger.logSmsInserted(
                smsHash,
                finalExpense.amount,
                finalExpense.type.name,
                finalExpense.status.name
            )

            if (finalExpense.status == ExpenseStatus.CONFIRMED) {
                syncManager?.syncConfirmedExpense(insertedId)
            }
            true
        } catch (e: Exception) {
            ExpenseLogger.logSmsInsertError(smsHash, e.message ?: "Unknown error")
            false
        }
    }
}
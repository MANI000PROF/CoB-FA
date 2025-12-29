package com.cobfa.app.utils

import android.util.Log

/**
 * Centralized logging for expense tracking and SMS processing.
 * All data integrity events logged here for debugging and auditing.
 */
object ExpenseLogger {

    private const val TAG = "COBFA"

    // ===== SMS Reading =====
    fun logSmsRead(count: Int) {
        Log.d(TAG, "[SMS_READ] Read $count messages from inbox")
    }

    fun logSmsFiltered(reason: String) {
        Log.d(TAG, "[SMS_FILTERED] $reason")
    }

    // ===== SMS Hashing & Validation =====
    fun logSmsHashComputed(hash: String, sender: String) {
        Log.d(TAG, "[SMS_HASH] sender=$sender hash=${hash.take(8)}...")
    }

    fun logTransactionDetected(amount: Double, type: String, merchant: String?) {
        Log.d(TAG, "[TRANSACTION] amount=₹$amount type=$type merchant=${merchant ?: "unknown"}")
    }

    // ===== SMS Processing & Insertion =====
    fun logSmsInserted(hash: String, amount: Double, type: String, status: String) {
        Log.d(TAG, "[SMS_INSERT] ✓ hash=${hash.take(8)}... amount=₹$amount type=$type status=$status")
    }

    fun logSmsDuplicate(hash: String) {
        Log.d(TAG, "[SMS_DUPLICATE] Skipped hash=${hash.take(8)}... (already in DB)")
    }

    fun logSmsInvalid(reason: String) {
        Log.d(TAG, "[SMS_INVALID] $reason")
    }

    fun logSmsInsertError(hash: String, error: String) {
        Log.e(TAG, "[SMS_INSERT_ERROR] hash=${hash.take(8)}... error=$error")
    }

    // ===== Scanning =====
    fun logScanStart(source: String) {
        Log.d(TAG, "[SCAN_START] $source")
    }

    fun logScanComplete(processed: Int, skipped: Int, source: String) {
        Log.d(TAG, "[SCAN_COMPLETE] $source processed=$processed skipped=$skipped")
    }

    // ===== Confirmation & Updates =====
    fun logConfirmationStart(id: Long) {
        Log.d(TAG, "[CONFIRM_START] Expense id=$id")
    }

    fun logConfirmationComplete(id: Long, category: String, status: String) {
        Log.d(TAG, "[CONFIRM_COMPLETE] id=$id category=$category status=$status")
    }

    fun logConfirmationError(id: Long, error: String) {
        Log.e(TAG, "[CONFIRM_ERROR] id=$id error=$error")
    }

    fun logConfirmationAlreadyDone(id: Long) {
        Log.w(TAG, "[CONFIRM_ALREADY] id=$id already confirmed, ignoring")
    }

    // ===== Analytics =====
    fun logAnalyticsUpdate(income: Double, expense: Double, balance: Double) {
        Log.d(TAG, "[ANALYTICS] income=₹$income expense=₹$expense balance=₹$balance")
    }

    fun logAnalyticsError(error: String) {
        Log.e(TAG, "[ANALYTICS_ERROR] $error")
    }

    // ===== Validation =====
    fun logValidationFailed(field: String, value: String, reason: String) {
        Log.w(TAG, "[VALIDATION_FAIL] $field=$value reason=$reason")
    }

    // ===== Database =====
    fun logDatabaseError(operation: String, error: String) {
        Log.e(TAG, "[DB_ERROR] operation=$operation error=$error")
    }
}

package com.cobfa.app.sms

import android.util.Log
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.*
import java.security.MessageDigest

object TransactionDetector {

    private const val TAG = "SMS_DETECTOR"

    fun detect(
        sender: String?,
        body: String,
        timestamp: Long
    ): ExpenseEntity? {

        val trustedSender = TrustedSenders.isTrusted(sender)

        val parsed = SmsTransactionParser.parse(sender, body)
            ?: return null

        if (!trustedSender) {
            Log.d(TAG, "Fallback validation used for sender=$sender")
        }

        val hashInput = "$sender|$body|$timestamp"
        val smsHash = sha256(hashInput)

        Log.d(TAG, "Transaction validated hash=$smsHash")

        return ExpenseEntity(
            amount = parsed.amount,
            type = parsed.type,
            category = null,
            merchant = parsed.merchant,
            timestamp = timestamp,
            source = ExpenseSource.SMS,
            status = if (parsed.type == ExpenseType.CREDIT)
                ExpenseStatus.CONFIRMED
            else
                ExpenseStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            smsHash = smsHash
        )
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

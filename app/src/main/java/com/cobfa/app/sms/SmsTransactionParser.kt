package com.cobfa.app.sms

import android.util.Log
import com.cobfa.app.domain.model.ExpenseType
import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Double,
    val type: ExpenseType,
    val merchant: String?
)

object SmsTransactionParser {

    private const val TAG = "SMS_PARSER"

    private val amountRegex =
        Pattern.compile("(rs\\.?|inr|₹)\\s*([0-9,]+(\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE)

    private val debitKeywords = listOf(
        "debit", "debited", "spent", "paid",
        "purchase", "withdrawn"
    )

    private val creditKeywords = listOf(
        "credit", "credited", "received",
        "salary", "refund"
    )

    fun parse(sender: String?, body: String): ParsedTransaction? {

        if (SmsFilters.isBlocked(body)) {
            Log.d(TAG, "Blocked SMS ignored")
            return null
        }

        val matcher = amountRegex.matcher(body.lowercase())
        if (!matcher.find()) {
            Log.d(TAG, "No amount found")
            return null
        }

        val amount = matcher.group(2)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null

        if (amount < 1) {
            Log.d(TAG, "Amount < 1 ignored")
            return null
        }

        val lower = body.lowercase()

        val type = when {
            creditKeywords.any { lower.contains(it) } ->
                ExpenseType.CREDIT
            debitKeywords.any { lower.contains(it) } ->
                ExpenseType.DEBIT
            else -> {
                Log.d(TAG, "No debit/credit keyword")
                return null
            }
        }

        val merchant = extractMerchant(lower)

        Log.d(TAG, "Parsed amount=$amount type=$type merchant=$merchant")

        return ParsedTransaction(amount, type, merchant)
    }

    private fun extractMerchant(text: String): String? =
        when {
            text.contains(" at ") -> text.substringAfter(" at ").take(40)
            text.contains(" to ") -> text.substringAfter(" to ").take(40)
            text.contains(" from ") -> text.substringAfter(" from ").take(40)
            else -> null
        }?.trim()
}

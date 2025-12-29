package com.cobfa.app.sms

import android.util.Log
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.utils.ExpenseLogger
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

    private const val MIN_AMOUNT = 1.0
    private const val MAX_AMOUNT = 1_000_000.0

    fun parse(sender: String?, body: String): ParsedTransaction? {

        if (SmsFilters.isBlocked(body)) {
            ExpenseLogger.logSmsFiltered("OTP or promotional SMS")
            return null
        }

        val matcher = amountRegex.matcher(body.lowercase())
        if (!matcher.find()) {
            ExpenseLogger.logSmsInvalid("No amount found in SMS")
            return null
        }

        val amount = matcher.group(2)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null

        // ✅ NEW: Validate amount is in reasonable range
        if (amount < MIN_AMOUNT) {
            ExpenseLogger.logValidationFailed("amount", amount.toString(), "below minimum ₹$MIN_AMOUNT")
            return null
        }

        if (amount > MAX_AMOUNT) {
            ExpenseLogger.logValidationFailed("amount", amount.toString(), "exceeds maximum ₹$MAX_AMOUNT")
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

        val merchant = extractMerchant(sender, lower)

        Log.d(TAG, "Parsed amount=$amount type=$type merchant=$merchant")
        ExpenseLogger.logTransactionDetected(amount, type.name, merchant)

        return ParsedTransaction(amount, type, merchant)
    }

    /**
     * Extract merchant from SMS.
     *
     * Priority 1: Sender code lookup (FAST, 100% reliable)
     * Priority 2: Message body parsing (FALLBACK for unknown senders)
     *
     * This hybrid approach:
     * - Uses known bank codes for reliable extraction
     * - Handles unknown senders via body parsing
     * - Enables fraud detection (unknown senders logged)
     */
    private fun extractMerchant(sender: String?, body: String): String? {
        // ============================================
        // PRIORITY 1: Sender Code Lookup
        // ============================================

        if (sender != null) {
            // Try direct sender-to-merchant mapping
            val senderMerchant = SenderMerchantMapper.getMerchantFromSender(sender)
            if (senderMerchant != null) {
                ExpenseLogger.logValidationFailed("merchant", senderMerchant, "extracted from sender code")
                return senderMerchant
            }

            // If sender is not in registry, log it for fraud detection
            if (!SenderMerchantMapper.isTrustedSender(sender)) {
                ExpenseLogger.logValidationFailed("sender", sender, "unknown/untrusted sender code")
            }
        }

        // ============================================
        // PRIORITY 2: Fallback to Body Parsing
        // ============================================

        return extractMerchantFromBody(body)
    }

    /**
     * Extract merchant from message body (FALLBACK method).
     * Only used if sender code is not recognized.
     */
    private fun extractMerchantFromBody(text: String): String? {
        val lower = text.lowercase()

        // Pattern 1: ".- Bank Name" or "- Bank Name" at end (handles both formats)
        // Handles: "6,028.57.- Canara Bank" and "fraud - Canara Bank"
        val bankRegex = Regex("""[.-]\s*([A-Za-z\s&']+?(?:Bank|Ltd|Inc|Corp|Limited|Canara|HDFC|ICICI|Axis|IndusInd))\s*(?:[.\s]|$)""")
        bankRegex.find(lower)?.groupValues?.get(1)?.let {
            val bank = it.trim()
            if (bank.isNotEmpty() && bank.length < 40) {
                return bank.capitalizeWords()
            }
        }

        // Pattern 2: Split on ".- " OR " - " (handles both credit and debit SMS)
        if (".- " in lower || " - " in lower) {
            val delimiter = if (".- " in lower) ".- " else " - "
            val parts = lower.split(delimiter)
            for (part in parts.asReversed()) {
                val trimmed = part.trim()
                if (trimmed.contains("bank") || trimmed.contains("ltd") || trimmed.contains("corp")) {
                    val cleaned = trimmed
                        .replace(Regex("""[.\n].*$"""), "")
                        .trim()
                    if (cleaned.length > 2 && cleaned.length < 40) {
                        return cleaned.capitalizeWords()
                    }
                }
            }
        }

        // Pattern 3: "at merchant_name" (Standard pattern)
        if (" at " in lower) {
            val afterAt = lower.substringAfter(" at ")
            val merchant = extractMerchantSegment(afterAt)
            if (merchant.isNotBlank() && !merchant.startsWith("your") && !merchant.startsWith("account")) {
                return merchant.capitalizeWords()
            }
        }

        // Pattern 4: "to merchant_name" (Standard pattern)
        if (" to " in lower) {
            val afterTo = lower.substringAfter(" to ")
            if (!afterTo.trimStart().startsWith("your") && !afterTo.trimStart().startsWith("account")) {
                val merchant = extractMerchantSegment(afterTo)
                if (merchant.isNotBlank()) {
                    return merchant.capitalizeWords()
                }
            }
        }

        // Pattern 5: "from merchant_name" (Standard pattern)
        if (" from " in lower) {
            val afterFrom = lower.substringAfter(" from ")
            val merchant = extractMerchantSegment(afterFrom)
            if (merchant.isNotBlank() && !merchant.startsWith("your")) {
                return merchant.capitalizeWords()
            }
        }

        // Pattern 6: UPI/Digital wallet transfers
        val upiPattern = Regex("""(paytm|googlepay|phonepe|gpay|amazonpay|whatsapp)""")
        upiPattern.find(lower)?.value?.let { return it.trim().capitalizeWords() }

        // Pattern 7: Salary/Transfer keywords
        val salaryPattern = Regex("""(salary|dividend|bonus|refund|transfer|payment)""")
        salaryPattern.find(lower)?.groupValues?.get(1)?.let {
            val keyword = it.trim()
            if (keyword.isNotEmpty()) return keyword.capitalizeWords()
        }

        // Pattern 8: ATM or withdrawal
        if (" atm " in lower || "withdrawal" in lower) {
            return "ATM Withdrawal"
        }

        // Pattern 9: Extract account holder name
        val namePattern = Regex("""debit(?:ed)?\s+to\s+([A-Za-z\s]+?)(?:\s+(?:a/c|account|on|\d))""")
        namePattern.find(lower)?.groupValues?.get(1)?.let {
            val name = it.trim()
            if (name.length in 2..30 && !name.startsWith("your")) {
                return name.capitalizeWords()
            }
        }

        return null  // Unknown/No merchant
    }

    /**
     * Extract a clean merchant segment from text.
     * Stops at common delimiters: period, comma, newline, or reaches 40 chars
     */
    private fun extractMerchantSegment(text: String): String {
        val delimiters = listOf(".", ",", "\n", "-", "via", "for", "on ", "at ", "dial", "call")
        var result = text.trim()

        for (delimiter in delimiters) {
            if (delimiter in result) {
                result = result.substringBefore(delimiter).trim()
            }
        }

        return result.take(40).trim()
    }

    /**
     * Capitalize first letter of each word (for merchant names)
     */
    private fun String.capitalizeWords(): String {
        return this.split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .map { word ->
                if (word.length > 0) {
                    word.uppercase() + word.substring(1).lowercase()
                } else word
            }
            .joinToString(" ")
    }

}

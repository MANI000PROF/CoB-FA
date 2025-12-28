package com.cobfa.app.sms

object SmsFilters {

    private val BLOCKED_KEYWORDS = listOf(
        "otp", "one time password", "verification",
        "offer", "cashback", "reward", "win",
        "sale", "discount", "promo"
    )

    fun isBlocked(body: String): Boolean {
        val lower = body.lowercase()
        return BLOCKED_KEYWORDS.any { lower.contains(it) }
    }
}

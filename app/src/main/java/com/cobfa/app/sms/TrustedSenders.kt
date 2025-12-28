package com.cobfa.app.sms

object TrustedSenders {

    // Banks + UPI apps (India)
    val BANK_SENDERS = setOf(
        "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "KOTAKB",
        "PNBSMS", "CANBNK", "IDFCFB", "YESBNK", "BOBTXN",
        "INDUSB", "FEDBNK"
    )

    val UPI_SENDERS = setOf(
        "GPAY", "GOOGLEPAY",
        "PHONEPE",
        "PAYTM",
        "AMAZONPAY", "AMZPAY"
    )

    fun isTrusted(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val s = sender.uppercase()
        return BANK_SENDERS.any { s.contains(it) } ||
                UPI_SENDERS.any { s.contains(it) }
    }
}

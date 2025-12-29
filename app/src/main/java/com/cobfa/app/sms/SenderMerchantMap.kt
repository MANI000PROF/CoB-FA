package com.cobfa.app.sms

/**
 * Maps standardized Indian bank sender codes to merchant names.
 * This is the PRIMARY method for merchant extraction.
 *
 * Source: RBI-approved bank sender IDs used in India
 */
object SenderMerchantMapper {

    private val senderToMerchantMap = mapOf(
        // Major Banks
        "CANBNK" to "Canara Bank",
        "HDFCBK" to "HDFC Bank",
        "ICIBK" to "ICICI Bank",
        "ICICI" to "ICICI Bank",
        "AXISBNK" to "Axis Bank",
        "AXIS" to "Axis Bank",
        "INDBNK" to "IndusInd Bank",
        "YESBNK" to "YES Bank",
        "KOTAK" to "Kotak Bank",
        "SBIBANK" to "SBI Bank",
        "SBI" to "SBI Bank",
        "BARODAMPAY" to "Baroda Bank",
        "PUBANK" to "Punjab National Bank",
        "HDFC" to "HDFC Bank",
        "IDBI" to "IDBI Bank",
        "FEDERAL" to "Federal Bank",
        "HSBC" to "HSBC Bank",
        "CITI" to "Citibank",

        // UPI & Digital Wallets
        "PAYTM" to "PayTM",
        "GOOGL" to "Google Pay",
        "PHONEPE" to "PhonePe",
        "AMAZONPAY" to "Amazon Pay",
        "WHATSAPP" to "WhatsApp Pay",
        "BHIM" to "BHIM UPI",

        // Payment Gateways
        "RAZORPAY" to "Razorpay",
        "INSTAMOJO" to "Instamojo",
        "CASHFREE" to "Cashfree",

        // Insurance & Finance
        "ICICIPRUI" to "ICICI Prudential",
        "HDFCLIFE" to "HDFC Life",
        "SBILY" to "SBI Life",
        "AXISVISA" to "Axis Bank",
        "IRDAI" to "Insurance Co",

        // E-commerce
        "FLIPKART" to "Flipkart",
        "AMAZON" to "Amazon",
        "SWIGGY" to "Swiggy",
        "ZOMATO" to "Zomato",
        "NETFLIX" to "Netflix",
        "OYO" to "OYO Rooms",

        // Utilities & Services
        "AIRTEL" to "Airtel",
        "JIOTELE" to "Jio (Reliance)",
        "BSNL" to "BSNL",
        "VODAFONE" to "Vodafone",
    )

    /**
     * Get merchant name from sender code.
     *
     * Example:
     *   getMerchantFromSender("CANBNK") → "Canara Bank"
     *   getMerchantFromSender("HDFCBK") → "HDFC Bank"
     *   getMerchantFromSender("UNKNOWN") → null (not in registry)
     */
    fun getMerchantFromSender(sender: String?): String? {
        if (sender == null || sender.isBlank()) {
            return null
        }

        val senderCode = sender.uppercase().trim()
        return senderToMerchantMap[senderCode]
    }

    /**
     * Check if sender is a KNOWN/TRUSTED bank code.
     * Useful for fraud detection.
     *
     * Example:
     *   isTrustedSender("CANBNK") → true
     *   isTrustedSender("RANDOMBANK") → false
     */
    fun isTrustedSender(sender: String?): Boolean {
        if (sender == null) return false
        return getMerchantFromSender(sender) != null
    }

    /**
     * Get all registered sender codes (for logging, debugging)
     */
    fun getRegisteredSenders(): Set<String> {
        return senderToMerchantMap.keys
    }

    /**
     * Get total number of registered senders
     */
    fun getRegisteredCount(): Int {
        return senderToMerchantMap.size
    }
}

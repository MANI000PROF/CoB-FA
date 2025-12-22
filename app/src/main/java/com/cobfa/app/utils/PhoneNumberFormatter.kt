package com.cobfa.app.utils

object PhoneNumberFormatter {

    fun toE164(raw: String, countryIso: String?): String? {
        val clean = raw.replace("\\s".toRegex(), "")

        if (clean.startsWith("+")) return clean

        return when (countryIso) {
            "IN" -> "+91$clean"
            "US" -> "+1$clean"
            else -> null
        }
    }
}

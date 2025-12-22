package com.cobfa.app.utils

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

object PhoneNumberResolver {

    fun getPhoneNumber(context: Context): String? {
        return try {
            val tm =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val raw = tm.line1Number
            Log.d("PHONE_RESOLVER", "Raw phone number: $raw")

            raw?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            Log.e("PHONE_RESOLVER", "Permission error", e)
            null
        } catch (e: Exception) {
            Log.e("PHONE_RESOLVER", "Unknown error", e)
            null
        }
    }
}

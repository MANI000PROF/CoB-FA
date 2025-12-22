package com.cobfa.app.utils

import android.content.Context
import android.telephony.TelephonyManager

object SimInfoUtil {

    fun isSimPresent(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }

    fun getNetworkCountryIso(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.networkCountryIso?.uppercase()
    }

    fun getSimCountryIso(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simCountryIso?.uppercase()
    }
}

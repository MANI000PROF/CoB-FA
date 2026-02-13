package com.cobfa.app.auth.session

import android.content.Context
import java.util.UUID

object DeviceId {
    private const val PREFS = "cobfa_device_prefs"
    private const val KEY = "device_id"

    fun get(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = sp.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        sp.edit().putString(KEY, created).apply()
        return created
    }
}

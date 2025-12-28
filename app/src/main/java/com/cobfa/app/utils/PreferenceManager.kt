package com.cobfa.app.utils

import android.content.Context

object PreferenceManager {

    private const val PREFS_NAME = "cobfa_prefs"
    private const val KEY_AUTO_TRACKING = "auto_tracking_enabled"
    private const val KEY_SMS_PERMISSION_GRANTED = "sms_permission_granted"
    private const val KEY_SMS_PERMISSION_DECIDED = "sms_permission_decided"
    private const val KEY_PENDING_AUTO_TRACKING = "pending_auto_tracking"
    private const val KEY_LAST_SMS_TS = "last_sms_timestamp"

    fun getLastSmsTimestamp(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SMS_TS, 0L)
    }

    fun setLastSmsTimestamp(context: Context, ts: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SMS_TS, ts)
            .apply()
    }

    fun setPendingAutoTracking(context: Context, pending: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_AUTO_TRACKING, pending)
            .apply()
    }

    fun isPendingAutoTracking(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING_AUTO_TRACKING, false)
    }

    fun isAutoTrackingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_TRACKING, false)
    }

    fun setAutoTrackingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_TRACKING, enabled).apply()
    }

    fun setSmsPermissionGranted(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SMS_PERMISSION_GRANTED, granted)
            .putBoolean(KEY_SMS_PERMISSION_DECIDED, true)
            .apply()
    }

    fun markSmsPermissionSkipped(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SMS_PERMISSION_DECIDED, true)
            .apply()
    }

    fun isSmsPermissionDecided(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SMS_PERMISSION_DECIDED, false)
    }
}

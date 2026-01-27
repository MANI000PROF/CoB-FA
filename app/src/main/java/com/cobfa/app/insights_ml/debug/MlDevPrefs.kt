package com.cobfa.app.insights_ml.debug

import android.content.Context

object MlDevPrefs {
    private const val PREF = "cobfa_ml_dev"
    private const val KEY_REMOTE_AI = "remote_ai_enabled"

    fun isRemoteAiEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMOTE_AI, DebugFlags.DEFAULT_REMOTE_AI)

    fun toggleRemoteAi(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val newVal = !prefs.getBoolean(KEY_REMOTE_AI, DebugFlags.DEFAULT_REMOTE_AI)
        prefs.edit().putBoolean(KEY_REMOTE_AI, newVal).apply()
        return newVal
    }
}

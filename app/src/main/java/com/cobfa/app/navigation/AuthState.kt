package com.cobfa.app.navigation

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

object AuthState {

    fun getStartDestination(context: Context): String {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            return "auth"
        }

        val prefs = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        val profileCompleted = prefs.getBoolean("profile_completed", false)

        return if (profileCompleted) "dashboard" else "auth"
    }
}

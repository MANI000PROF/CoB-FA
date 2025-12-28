package com.cobfa.app.launch

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@Composable
fun LaunchScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            onNavigate("auth")
            return@LaunchedEffect
        }

        val uid = user.uid
        val ref = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)

        ref.get()
            .addOnSuccessListener { snapshot ->
                val completed =
                    snapshot.child("profileCompleted").getValue(Boolean::class.java) ?: false

                if (completed) {
                    onNavigate("dashboard")
                } else {
                    onNavigate("profile")
                }
            }
            .addOnFailureListener {
                // fallback safety
                onNavigate("profile")
            }
    }
}

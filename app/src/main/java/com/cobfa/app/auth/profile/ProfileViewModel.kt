package com.cobfa.app.auth.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    fun saveProfile(
        name: String,
        dob: String,
        age: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            Log.e("ProfileVM", "saveProfile failed: user is null")
            onError("User not authenticated")
            return
        }

        Log.d("ProfileVM", "Saving profile for uid=${user.uid}")

        val profileData = mapOf(
            "uid" to user.uid,
            "phone" to user.phoneNumber,
            "name" to name,
            "dob" to dob,
            "age" to age,
            "providers" to mapOf(
                "phone" to true,
                "google" to user.providerData.any { it.providerId == "google.com" }
            ),
            "profileCompleted" to true,
            "createdAt" to System.currentTimeMillis()
        )

        db.child("users")
            .child(user.uid)
            .setValue(profileData)
            .addOnSuccessListener {
                Log.d("ProfileVM", "Profile saved successfully")
                onSuccess()
            }
            .addOnFailureListener {
                Log.e("ProfileVM", "Profile save failed", it)
                onError(it.message ?: "Failed to save profile")
            }
    }
}

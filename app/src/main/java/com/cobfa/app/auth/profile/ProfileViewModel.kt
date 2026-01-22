package com.cobfa.app.auth.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.remote.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val firestore = FirestoreService()

    fun suggestUsername(fullName: String, uid: String): String {
        val base = fullName.trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .take(10)
            .ifBlank { "user" }

        val suffix = uid.takeLast(4).lowercase()
        return "${base}_$suffix".take(15)
    }

    private fun normalizeUsername(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")

    private fun isValidUsername(u: String): Boolean {
        if (u.length !in 3..15) return false
        if (!u.matches(Regex("^[a-z0-9_]+$"))) return false
        return true
    }

    fun saveProfile(
        name: String,
        dob: String,
        age: Int,
        city: String,
        state: String,
        username: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError("User not authenticated")
            return
        }

        val handle = normalizeUsername(username)
        if (!isValidUsername(handle)) {
            onError("Invalid username. Use 3–15 chars: a-z, 0-9, underscore.")
            return
        }

        viewModelScope.launch {
            // 1) Claim username (set-once uniqueness)
            val claim = firestore.claimUsername(handle)
            if (claim.isFailure) {
                val e = claim.exceptionOrNull()
                Log.e("ProfileVM", "claimUsername failed for @$handle", e)
                onError(e?.message ?: "claimUsername failed")
                return@launch
            }
            Log.d("ProfileVM", "claimUsername success for @$handle")

            // 2) Save profile to Realtime DB
            val profileData = mapOf(
                "uid" to user.uid,
                "phone" to user.phoneNumber,
                "name" to name,
                "username" to handle,
                "dob" to dob,
                "age" to age,
                "city" to city.trim(),
                "state" to state.trim(),
                "country" to "India",
                "providers" to mapOf(
                    "phone" to true,
                    "google" to user.providerData.any { it.providerId == "google.com" }
                ),
                "profileCompleted" to true,
                "createdAt" to System.currentTimeMillis()
            )

            rtdb.child("users")
                .child(user.uid)
                .setValue(profileData)
                .addOnSuccessListener {
                    Log.d("ProfileVM", "Profile saved successfully")

                    // 3) Create public leaderboard doc (points start at 0)
                    viewModelScope.launch {
                        val res = firestore.upsertPublicUser(
                            username = handle,
                            city = city,
                            state = state,
                            country = "India",
                            pointsBalance = 0
                        )
                        if (res.isFailure) {
                            Log.e("ProfileVM", "upsertPublicUser failed", res.exceptionOrNull())
                            // still allow onboarding to finish if RTDB profile saved:
                            onSuccess()
                        } else {
                            Log.d("ProfileVM", "upsertPublicUser success")
                            onSuccess()
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("ProfileVM", "Profile save failed", it)
                    onError(it.message ?: "Failed to save profile")
                }
        }
    }
}

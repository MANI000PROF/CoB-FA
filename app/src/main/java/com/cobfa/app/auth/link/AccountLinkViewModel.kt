package com.cobfa.app.auth.link

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cobfa.app.auth.session.SessionWriter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AccountLinkViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    var errorMessage: String? = null

    fun linkGoogleAccount(
        idToken: String,
        deviceId: String,
        onSuccess: (String?) -> Unit
    ) {
        Log.d("AccountLinkVM", "linkGoogleAccount called")

        val user = auth.currentUser
        if (user == null) {
            errorMessage = "No authenticated user"
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        fun finishSuccess() {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                onSuccess(auth.currentUser?.displayName)
                return
            }
            SessionWriter.write(uid, deviceId) {
                onSuccess(auth.currentUser?.displayName)
            }
        }

        user.linkWithCredential(credential)
            .addOnSuccessListener {
                Log.d("AccountLinkVM", "Google provider linked")
                finishSuccess()
            }
            .addOnFailureListener { e ->
                if (e.message?.contains("already been linked", ignoreCase = true) == true) {
                    Log.d("AccountLinkVM", "Google already linked — treating as success")
                    finishSuccess()
                } else {
                    Log.e("AccountLinkVM", "Link failed", e)
                    errorMessage = e.message
                }
            }
    }

}

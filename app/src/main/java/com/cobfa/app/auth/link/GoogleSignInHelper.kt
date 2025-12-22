package com.cobfa.app.auth.link

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.cobfa.app.R

object GoogleSignInHelper {

    private const val WEB_CLIENT_ID =
        "369229653252-f6k7r0pma35lpohvdjiivceb8agpr5j9.apps.googleusercontent.com"

    fun getClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(activity, gso)
    }

    fun handleResult(
        data: Intent?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            Log.d("GoogleSignIn", "handleResult called, intent=$data")

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            Log.d("GoogleSignIn", "Account email=${account.email}")
            Log.d("GoogleSignIn", "Account idToken=${account.idToken != null}")

            val idToken = account.idToken
            if (idToken != null) {
                onSuccess(idToken)
            } else {
                onError("Google ID token is null")
            }

        } catch (e: ApiException) {
            Log.e("GoogleSignIn", "ApiException status=${e.statusCode}", e)
            onError("Google sign-in failed: ${e.statusCode}")
        }
    }

}


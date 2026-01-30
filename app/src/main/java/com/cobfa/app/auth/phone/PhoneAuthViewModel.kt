package com.cobfa.app.auth.phone

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PhoneAuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private var lastPhoneNumber: String? = null

    var verificationId: String? = null
        private set

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var detectedPhoneNumber by mutableStateOf<String?>(null)
        private set

    private val TAG = "PhoneAuthVM"

    var resendCooldown by mutableStateOf(0)
        private set

    fun clearError() {
        errorMessage = null
    }

    fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: () -> Unit,
        onVerified: () -> Unit
    ) {
        isLoading = true
        errorMessage = null
        Log.d(TAG, "startPhoneVerification called")

        lastPhoneNumber = phoneNumber

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(TAG, "onVerificationCompleted")
                    signInWithCredential(credential, onVerified)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(TAG, "onVerificationFailed: ${e.message}")
                    isLoading = false
                    errorMessage = e.message ?: "Verification failed"
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d(TAG, "onCodeSent")
                    startResendCooldown()
                    isLoading = false
                    verificationId = id
                    resendToken = token
                    onCodeSent()
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun startVerificationWithFallback(
        activity: Activity,
        fallbackPhone: String?,
        onCodeSent: () -> Unit,
        onVerified: () -> Unit
    ) {
        val phone = detectedPhoneNumber ?: fallbackPhone

        if (phone == null) {
            errorMessage = "Enter a valid phone number"
            return
        }

        startPhoneVerification(
            phoneNumber = phone,
            activity = activity,
            onCodeSent = onCodeSent,
            onVerified = onVerified
        )
    }

    fun verifyOtp(
        otp: String,
        onVerified: () -> Unit
    ) {
        clearError()
        val id = verificationId
        if (id.isNullOrBlank()) {
            errorMessage = "Verification session expired. Please resend OTP."
            return
        }
        if (otp.length < 6) {
            errorMessage = "Enter the 6-digit OTP"
            return
        }

        Log.d(TAG, "verifyOtp called")
        val credential = PhoneAuthProvider.getCredential(id, otp)
        signInWithCredential(credential, onVerified)
    }

    fun resendOtp(
        activity: Activity,
        onCodeSent: () -> Unit
    ) {
        clearError()

        val token = resendToken ?: run {
            errorMessage = "Resend not available yet"
            return
        }

        val phone = lastPhoneNumber ?: run {
            errorMessage = "Phone number missing"
            return
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setForceResendingToken(token)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(TAG, "Auto verification completed during resend")
                    signInWithCredential(credential) { /* stay on OTP screen */ }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    errorMessage = e.message ?: "Resend failed"
                }

                override fun onCodeSent(
                    id: String,
                    newToken: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d(TAG, "Resend OTP code sent")
                    startResendCooldown()
                    verificationId = id
                    resendToken = newToken
                    onCodeSent()
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun setDetectedPhone(number: String) {
        detectedPhoneNumber = number
        lastPhoneNumber = number
    }

    private fun startResendCooldown() {
        resendCooldown = 60
        viewModelScope.launch {
            while (resendCooldown > 0) {
                delay(1000)
                resendCooldown--
            }
        }
    }

    private fun signInWithCredential(
        credential: PhoneAuthCredential,
        onVerified: () -> Unit
    ) {
        isLoading = true
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                isLoading = false
                onVerified()
            }
            .addOnFailureListener {
                isLoading = false
                errorMessage = it.message ?: "Sign-in failed"
            }
    }
}

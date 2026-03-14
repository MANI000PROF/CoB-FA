package com.cobfa.app.auth.phone

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PhoneAuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val tag = "PhoneAuthVM"

    private var cooldownJob: Job? = null
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

    var resendCooldown by mutableStateOf(0)
        private set

    val currentPhoneNumber: String?
        get() = detectedPhoneNumber ?: lastPhoneNumber

    fun clearError() {
        errorMessage = null
    }

    fun clearVerificationSession() {
        verificationId = null
        resendToken = null
        cooldownJob?.cancel()
        resendCooldown = 0
    }

    fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: () -> Unit,
        onVerified: () -> Unit
    ) {
        isLoading = true
        errorMessage = null
        lastPhoneNumber = phoneNumber

        Log.d(tag, "startPhoneVerification called for $phoneNumber")

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(tag, "onVerificationCompleted")
                signInWithCredential(credential, onVerified)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(tag, "onVerificationFailed: ${e.message}", e)
                isLoading = false
                errorMessage = e.message ?: "Verification failed"
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(tag, "onCodeSent")
                verificationId = id
                resendToken = token
                isLoading = false
                startResendCooldown()
                onCodeSent()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
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

        if (phone.isNullOrBlank()) {
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
        errorMessage = null

        val id = verificationId
        if (id.isNullOrBlank()) {
            errorMessage = "Verification session expired. Please resend OTP."
            return
        }

        if (otp.length != 6) {
            errorMessage = "Enter the 6-digit OTP"
            return
        }

        Log.d(tag, "verifyOtp called")
        val credential = PhoneAuthProvider.getCredential(id, otp)
        signInWithCredential(credential, onVerified)
    }

    fun resendOtp(
        activity: Activity,
        onCodeSent: () -> Unit
    ) {
        errorMessage = null

        if (resendCooldown > 0) {
            errorMessage = "Please wait ${resendCooldown}s before resending"
            return
        }

        val token = resendToken
        if (token == null) {
            errorMessage = "Resend not available yet"
            return
        }

        val phone = lastPhoneNumber
        if (phone.isNullOrBlank()) {
            errorMessage = "Phone number missing"
            return
        }

        isLoading = true

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(tag, "Auto verification completed during resend")
                signInWithCredential(credential) { }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(tag, "resend onVerificationFailed: ${e.message}", e)
                isLoading = false
                errorMessage = e.message ?: "Resend failed"
            }

            override fun onCodeSent(
                id: String,
                newToken: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(tag, "Resend OTP code sent")
                verificationId = id
                resendToken = newToken
                isLoading = false
                startResendCooldown()
                onCodeSent()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setForceResendingToken(token)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun setDetectedPhone(number: String) {
        detectedPhoneNumber = number
        lastPhoneNumber = number
    }

    private fun startResendCooldown() {
        cooldownJob?.cancel()
        resendCooldown = 60

        cooldownJob = viewModelScope.launch {
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
        errorMessage = null

        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                isLoading = false
                errorMessage = null
                onVerified()
            }
            .addOnFailureListener { error ->
                isLoading = false
                errorMessage = error.message ?: "Sign-in failed"
            }
    }

    override fun onCleared() {
        cooldownJob?.cancel()
        super.onCleared()
    }
}

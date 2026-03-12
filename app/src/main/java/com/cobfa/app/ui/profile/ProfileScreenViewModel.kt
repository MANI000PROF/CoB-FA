package com.cobfa.app.ui.profile

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val user: UserProfileUi = UserProfileUi(),
    val autoTrackingEnabled: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val smsPermissionDecided: Boolean = false,
    val lastSmsTimestamp: Long = 0L,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ProfileScreenViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val context = getApplication<Application>()
    private val httpClient = OkHttpClient()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    isLoggedIn = false,
                    errorMessage = "User not signed in"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val snap = db.child("users").child(uid).get().await()
                val authEmail = auth.currentUser?.email.orEmpty()

                val profile = UserProfileUi(
                    name = snap.child("name").getValue(String::class.java).orEmpty(),
                    username = snap.child("username").getValue(String::class.java).orEmpty(),
                    phone = snap.child("phone").getValue(String::class.java).orEmpty(),
                    email = snap.child("email").getValue(String::class.java).orEmpty().ifBlank { authEmail },
                    photoUrl = snap.child("photoUrl").getValue(String::class.java).orEmpty(),
                    city = snap.child("city").getValue(String::class.java).orEmpty(),
                    state = snap.child("state").getValue(String::class.java).orEmpty(),
                    country = snap.child("country").getValue(String::class.java).orEmpty(),
                    dob = snap.child("dob").getValue(String::class.java).orEmpty(),
                    occupation = snap.child("occupation").getValue(String::class.java).orEmpty()
                )

                _uiState.value = ProfileUiState(
                    isLoading = false,
                    user = profile,
                    autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context),
                    smsPermissionGranted = isSmsPermissionGranted(),
                    smsPermissionDecided = PreferenceManager.isSmsPermissionDecided(context),
                    lastSmsTimestamp = PreferenceManager.getLastSmsTimestamp(context),
                    isLoggedIn = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    fun refreshPreferenceState() {
        _uiState.value = _uiState.value.copy(
            autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context),
            smsPermissionGranted = isSmsPermissionGranted(),
            smsPermissionDecided = PreferenceManager.isSmsPermissionDecided(context),
            lastSmsTimestamp = PreferenceManager.getLastSmsTimestamp(context)
        )
    }

    fun updateAutoTracking(enabled: Boolean) {
        PreferenceManager.setAutoTrackingEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(
            autoTrackingEnabled = enabled,
            successMessage = if (enabled) "Auto tracking enabled" else "Auto tracking disabled"
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    fun updateProfile(
        name: String,
        username: String,
        phone: String,
        email: String,
        city: String,
        state: String,
        country: String,
        dob: String,
        occupation: String
    ) {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "User not signed in")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val updates = mapOf(
                    "name" to name.trim(),
                    "username" to username.trim(),
                    "phone" to phone.trim(),
                    "email" to email.trim(),
                    "city" to city.trim(),
                    "state" to state.trim(),
                    "country" to country.trim(),
                    "dob" to dob.trim(),
                    "occupation" to occupation.trim()
                )

                db.child("users").child(uid).updateChildren(updates).await()

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    user = _uiState.value.user.copy(
                        name = name.trim(),
                        username = username.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        city = city.trim(),
                        state = state.trim(),
                        country = country.trim(),
                        dob = dob.trim(),
                        occupation = occupation.trim()
                    ),
                    successMessage = "Profile updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Failed to update profile"
                )
            }
        }
    }

    fun uploadAndSaveProfilePhoto(photoUri: String) {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "User not signed in")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val uploadResult = uploadProfilePhotoToCloudinary(photoUri)
                val photoUrl = uploadResult.getOrThrow()

                db.child("users").child(uid)
                    .child("photoUrl")
                    .setValue(photoUrl)
                    .await()

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    user = _uiState.value.user.copy(photoUrl = photoUrl),
                    successMessage = "Profile photo updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Failed to update profile photo"
                )
            }
        }
    }

    private fun isSmsPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun uploadProfilePhotoToCloudinary(photoUri: String): Result<String> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null

            try {
                val uri = Uri.parse(photoUri)

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Unable to read selected image"))

                tempFile = File.createTempFile("profile_", ".jpg", context.cacheDir)

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val cloudName = "dfrve8uzg"
                val uploadPreset = "cobfa_unsigned_preset"

                val fileBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", tempFile.name, fileBody)
                    .addFormDataPart("upload_preset", uploadPreset)
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Image upload failed: HTTP ${response.code} - $body")
                    )
                }

                val json = JSONObject(body)
                val secureUrl = json.optString("secure_url")
                Log.d("ProfileVM", "Parsed secure_url=$secureUrl")

                if (secureUrl.isBlank()) {
                    Result.failure(Exception("Cloudinary did not return secure_url"))
                } else {
                    Result.success(secureUrl)
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Cloudinary upload exception", e)
                Result.failure(e)
            } finally {
                tempFile?.delete()
            }
        }
}

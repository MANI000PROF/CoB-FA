package com.cobfa.app.auth.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.remote.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val firestore = FirestoreService()
    private val httpClient = OkHttpClient()

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
        photoUri: String?,
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
            val photoUrl = if (!photoUri.isNullOrBlank()) {
                val uploadResult = uploadProfilePhotoToCloudinary(photoUri)

                if (uploadResult.isFailure) {
                    val e = uploadResult.exceptionOrNull()
                    Log.e("ProfileVM", "Photo upload failed", e)
                    onError(e?.message ?: "Photo upload failed")
                    return@launch
                }

                val uploadedUrl = uploadResult.getOrNull()
                Log.d("ProfileVM", "uploaded photoUrl=$uploadedUrl")
                uploadedUrl
            } else {
                Log.d("ProfileVM", "photoUri is null/blank, skipping upload")
                null
            }

            val claim = firestore.claimUsername(handle)
            if (claim.isFailure) {
                val e = claim.exceptionOrNull()
                Log.e("ProfileVM", "claimUsername failed for @$handle", e)
                onError(e?.message ?: "claimUsername failed")
                return@launch
            }
            Log.d("ProfileVM", "claimUsername success for @$handle")

            val profileData = mutableMapOf(
                "uid" to user.uid,
                "phone" to (user.phoneNumber ?: ""),
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

            if (!photoUrl.isNullOrBlank()) {
                profileData["photoUrl"] = photoUrl
            }

            rtdb.child("users")
                .child(user.uid)
                .setValue(profileData)
                .addOnSuccessListener {
                    Log.d("ProfileVM", "Profile saved successfully")

                    viewModelScope.launch {
                        val res = firestore.upsertPublicUser(
                            username = handle,
                            profilePicUrl = photoUrl ?: "",
                            city = city,
                            state = state,
                            country = "India",
                            pointsBalance = 0
                        )
                        if (res.isFailure) {
                            Log.e("ProfileVM", "upsertPublicUser failed", res.exceptionOrNull())
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

    private suspend fun uploadProfilePhotoToCloudinary(photoUri: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(photoUri)
                val scheme = uri.scheme?.lowercase()

                Log.d("ProfileVM", "Uploading profile photo, uri=$photoUri, scheme=$scheme")

                when (scheme) {
                    "http", "https" -> uploadRemoteImageUrlToCloudinary(photoUri)
                    "content", "file" -> uploadLocalImageUriToCloudinary(photoUri)
                    else -> Result.failure(
                        Exception("Unsupported image source: $photoUri")
                    )
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Cloudinary upload exception", e)
                Result.failure(e)
            }
        }

    private fun uploadRemoteImageUrlToCloudinary(remoteUrl: String): Result<String> {
        return try {
            val cloudName = "dfrve8uzg"
            val uploadPreset = "cobfa_unsigned_preset"

            val requestBody = FormBody.Builder()
                .add("file", remoteUrl)
                .add("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("Remote image upload failed: HTTP ${response.code} - $body")
                )
            }

            val json = JSONObject(body)
            val secureUrl = json.optString("secure_url")
            Log.d("ProfileVM", "Remote upload secure_url=$secureUrl")

            if (secureUrl.isBlank()) {
                Result.failure(Exception("Cloudinary did not return secure_url"))
            } else {
                Result.success(secureUrl)
            }
        } catch (e: Exception) {
            Log.e("ProfileVM", "Remote image upload exception", e)
            Result.failure(e)
        }
    }

    private fun uploadLocalImageUriToCloudinary(photoUri: String): Result<String> {
        return try {
            val context = getApplication<Application>()
            val uri = Uri.parse(photoUri)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Unable to read selected image"))

            val tempFile = File.createTempFile("profile_", ".jpg", context.cacheDir)
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

            tempFile.delete()

            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("Image upload failed: HTTP ${response.code} - $body")
                )
            }

            val json = JSONObject(body)
            val secureUrl = json.optString("secure_url")
            Log.d("ProfileVM", "Local upload secure_url=$secureUrl")

            if (secureUrl.isBlank()) {
                Result.failure(Exception("Cloudinary did not return secure_url"))
            } else {
                Result.success(secureUrl)
            }
        } catch (e: Exception) {
            Log.e("ProfileVM", "Local image upload exception", e)
            Result.failure(e)
        }
    }
}

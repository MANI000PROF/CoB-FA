package com.cobfa.app.auth.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cobfa.app.auth.link.AccountLinkViewModel
import com.cobfa.app.auth.link.GoogleSignInHelper
import com.google.firebase.auth.FirebaseAuth
import java.util.*

@Composable
fun ProfileSetupScreen(
    onProfileCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as Activity
    val linkVm: AccountLinkViewModel = viewModel()

    val profileVm: ProfileViewModel = viewModel()

    var googleLinked by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }

    var age by rememberSaveable { mutableStateOf<Int?>(null) }
    var ageError by remember { mutableStateOf(false) }

    // Launcher for Google Sign-In
    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("ProfileSetup", "Activity result received: ${result.resultCode}")

            GoogleSignInHelper.handleResult(
                result.data,
                onSuccess = { token ->
                    Log.d("ProfileSetup", "Google ID token received (length=${token.length})")

                    linkVm.linkGoogleAccount(token) { displayName ->
                        Log.d("ProfileSetup", "Firebase link success, name=$displayName")
                        googleLinked = true
                        val user = FirebaseAuth.getInstance().currentUser
                        // Try multiple safe sources
                        name = when {
                            !user?.displayName.isNullOrBlank() -> user?.displayName!!
                            !user?.email.isNullOrBlank() -> user?.email!!.substringBefore("@")
                            else -> ""
                        }
                    }
                },
                onError = { error ->
                    Log.e("ProfileSetup", "Google sign-in error: $error")
                    linkVm.errorMessage = error
                }
            )
        }


    // Date picker
    fun openDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                dob = "%04d-%02d-%02d".format(year, month + 1, day)

                // Calculate age
                val today = Calendar.getInstance()
                val birth = Calendar.getInstance().apply {
                    set(year, month, day)
                }

                var calculatedAge = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                    calculatedAge--
                }

                age = calculatedAge
                ageError = calculatedAge < 18
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        Text(
            text = "Complete your profile",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(24.dp))

        // Google Linking (MANDATORY)
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !googleLinked,
            onClick = {
                val client = GoogleSignInHelper.getClient(activity)
                client.signOut().addOnCompleteListener {
                    googleLauncher.launch(client.signInIntent)
                }
            }
        ) {
            Text(if (googleLinked) "Google Account Linked" else "Continue with Google")
        }

        Spacer(Modifier.height(24.dp))

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = googleLinked
        )

        Spacer(Modifier.height(16.dp))

        // DOB
        OutlinedTextField(
            value = dob,
            onValueChange = {},
            label = { Text("Date of Birth") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = googleLinked,
            trailingIcon = {
                IconButton(onClick = { openDatePicker() }) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Select DOB"
                    )
                }
            }
        )

        //Age
        age?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Age: $it years",
                color = if (ageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        if (ageError) {
            Text(
                text = "You must be at least 18 years old to use this app",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(32.dp))

        // Continue
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = googleLinked &&
                    name.isNotBlank() &&
                    dob.isNotBlank() &&
                    age != null &&
                    age!! >= 18,
            onClick = {
                Log.d("ProfileSetup", "Continue clicked, saving profile")

                profileVm.saveProfile(
                    name = name,
                    dob = dob,
                    age = age!!,
                    onSuccess = {
                        Log.d("ProfileSetup", "Profile save success, navigating")
                        onProfileCompleted()
                    },
                    onError = { error ->
                        Log.e("ProfileSetup", "Profile save error: $error")
                        linkVm.errorMessage = error
                    }
                )
            }
        ) {
            Text("Continue")
        }

        linkVm.errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cobfa.app.auth.link.AccountLinkViewModel
import com.cobfa.app.auth.link.GoogleSignInHelper
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import java.util.*

private val INDIA_STATES_UTS = listOf(
    "Andhra Pradesh","Arunachal Pradesh","Assam","Bihar","Chhattisgarh","Goa","Gujarat","Haryana",
    "Himachal Pradesh","Jharkhand","Karnataka","Kerala","Madhya Pradesh","Maharashtra","Manipur",
    "Meghalaya","Mizoram","Nagaland","Odisha","Punjab","Rajasthan","Sikkim","Tamil Nadu","Telangana",
    "Tripura","Uttar Pradesh","Uttarakhand","West Bengal",
    "Andaman and Nicobar Islands","Chandigarh","Dadra and Nagar Haveli and Daman and Diu",
    "Delhi","Jammu and Kashmir","Ladakh","Lakshadweep","Puducherry"
)

private val CITIES_BY_STATE = mapOf(
    "Telangana" to listOf("Hyderabad", "Warangal", "Nizamabad", "Karimnagar", "Khammam", "Nalgonda"),
    "Andhra Pradesh" to listOf("Visakhapatnam", "Vijayawada", "Guntur", "Nellore", "Tirupati", "Kurnool"),
    "Karnataka" to listOf("Bengaluru", "Mysuru", "Mangaluru", "Hubballi", "Belagavi"),
    "Tamil Nadu" to listOf("Chennai", "Coimbatore", "Madurai", "Salem", "Tiruchirappalli"),
    "Maharashtra" to listOf("Mumbai", "Pune", "Nagpur", "Nashik", "Thane"),
    "Delhi" to listOf("New Delhi", "Delhi")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as Activity
    val linkVm: AccountLinkViewModel = viewModel()
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    var profileError by rememberSaveable { mutableStateOf<String?>(null) }
    val deviceId = remember { DeviceId.get(context) }

    LaunchedEffect(linkVm.errorMessage, profileError) {
        val msg = linkVm.errorMessage ?: profileError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            profileError = null
        }
    }

    val profileVm: ProfileViewModel = viewModel()

    var googleLinked by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var dob by rememberSaveable { mutableStateOf("") }

    var age by rememberSaveable { mutableStateOf<Int?>(null) }
    var ageError by remember { mutableStateOf(false) }

    var city by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }

    var autoTrackingEnabled by rememberSaveable { mutableStateOf(false) }

    // Launcher for Google Sign-In
    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("ProfileSetup", "Activity result received: ${result.resultCode}")

            GoogleSignInHelper.handleResult(
                result.data,
                onSuccess = { token ->
                    Log.d("ProfileSetup", "Google ID token received (length=${token.length})")

                    linkVm.linkGoogleAccount(token, deviceId) { displayName ->
                        Log.d("ProfileSetup", "Firebase link success, name=$displayName")
                        googleLinked = true
                        val user = FirebaseAuth.getInstance().currentUser
                        // Try multiple safe sources
                        val resolvedName =
                            when {
                                !user?.displayName.isNullOrBlank() -> user?.displayName!!
                                !user?.email.isNullOrBlank() -> user?.email!!.substringBefore("@")
                                else -> ""
                            }

                        name = TextFieldValue(resolvedName)
                        val uid = user?.uid
                        if (!uid.isNullOrBlank()) {
                            username = profileVm.suggestUsername(resolvedName, uid)
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Complete profile") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // (keep existing content inside)
            Text(
                text = "Complete your profile",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Privacy: Insights run on-device. Firebase is used for backup sync. Raw SMS bodies are never uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                onValueChange = { newValue ->
                    name = newValue
                },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = googleLinked,
                singleLine = true
            )

            // User Name
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { raw ->
                    username = raw
                        .lowercase()
                        .replace(" ", "_")
                        .filter { it.isLetterOrDigit() || it == '_' }
                        .take(15)
                },
                label = { Text("Username (unique, set once)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = googleLinked,
                singleLine = true,
                supportingText = { Text("3–15 chars: a-z, 0-9, underscore") }
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

            // City (searchable dropdown over a small suggestion list)
            val citySuggestions = remember(state) { CITIES_BY_STATE[state].orEmpty() }
            var cityExpanded by rememberSaveable { mutableStateOf(false) }
            val filteredCities = remember(city, citySuggestions) {
                if (city.isBlank()) citySuggestions
                else citySuggestions.filter { it.contains(city.trim(), ignoreCase = true) }
            }
            var stateExpanded by rememberSaveable { mutableStateOf(false) }

            // State (fixed dropdown)
            ExposedDropdownMenuBox(
                expanded = stateExpanded,
                onExpandedChange = { if (googleLinked) stateExpanded = !stateExpanded }
            ) {
                OutlinedTextField(
                    value = state,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("State") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = googleLinked,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = stateExpanded,
                    onDismissRequest = { stateExpanded = false }
                ) {
                    INDIA_STATES_UTS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                state = option
                                city = ""
                                stateExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // City (fixed dropdown)
            ExposedDropdownMenuBox(
                expanded = cityExpanded && filteredCities.isNotEmpty(),
                onExpandedChange = {
                    if (googleLinked && state.isNotBlank()) cityExpanded = !cityExpanded
                }
            ) {
                OutlinedTextField(
                    value = city,
                    onValueChange = {
                        city = it
                        if (googleLinked && state.isNotBlank()) cityExpanded = true
                    },
                    label = { Text("City (residential)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = googleLinked && state.isNotBlank(),
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = cityExpanded && filteredCities.isNotEmpty(),
                    onDismissRequest = { cityExpanded = false }
                ) {
                    filteredCities.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                city = option
                                cityExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Expense tracking (optional)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "If enabled, CoBFA will scan your SMS inbox only when you open the app or pull to refresh. No background interception.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable auto-tracking")
                        Switch(
                            checked = autoTrackingEnabled,
                            enabled = googleLinked,
                            onCheckedChange = { autoTrackingEnabled = it }
                        )
                    }
                }
            }

            // Continue
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = googleLinked &&
                        name.text.isNotBlank() &&
                        dob.isNotBlank() &&
                        age != null &&
                        age!! >= 18 && city.isNotBlank() && state.isNotBlank() && username.isNotBlank(),
                onClick = {
                    focusManager.clearFocus(force = true)

                    PreferenceManager.setAutoTrackingEnabled(
                        context = context,
                        enabled = autoTrackingEnabled
                    )

                    profileError = null

                    profileVm.saveProfile(
                        name = name.text,
                        dob = dob,
                        age = age!!,
                        city = city,
                        state = state,
                        username = username,
                        onSuccess = {
                            onProfileCompleted()
                        },
                        onError = { error ->
                            profileError = error
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

            profileError?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

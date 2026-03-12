package com.cobfa.app.auth.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
    val profileVm: ProfileViewModel = viewModel()

    val snackbarHostState = remember { SnackbarHostState() }

    var profileError by rememberSaveable { mutableStateOf<String?>(null) }
    val deviceId = remember { DeviceId.get(context) }

    var googleLinked by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var dob by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf<Int?>(null) }
    var ageError by remember { mutableStateOf(false) }
    var city by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var autoTrackingEnabled by rememberSaveable { mutableStateOf(false) }

    // Use remember, not rememberSaveable, for temporary picked URI
    var profileImageUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(linkVm.errorMessage, profileError) {
        val msg = linkVm.errorMessage ?: profileError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            profileError = null
        }
    }

    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            GoogleSignInHelper.handleResult(
                result.data,
                onSuccess = { token ->
                    linkVm.linkGoogleAccount(token, deviceId) { displayName ->
                        googleLinked = true
                        val user = FirebaseAuth.getInstance().currentUser
                        val resolvedName = when {
                            !user?.displayName.isNullOrBlank() -> user?.displayName!!
                            !user?.email.isNullOrBlank() -> user?.email!!.substringBefore("@")
                            else -> ""
                        }
                        name = TextFieldValue(resolvedName)
                        val uid = user?.uid
                        if (!uid.isNullOrBlank()) {
                            username = profileVm.suggestUsername(resolvedName, uid)
                        }
                        Log.d("ProfileSetup", "Google linked successfully. username=$username")
                    }
                },
                onError = { error ->
                    Log.e("ProfileSetup", "Google link failed: $error")
                    linkVm.errorMessage = error
                }
            )
        }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            profileImageUri = uri?.toString()
        }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                dob = "%04d-%02d-%02d".format(year, month + 1, day)

                val today = Calendar.getInstance()
                val birth = Calendar.getInstance().apply { set(year, month, day) }

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

    val citySuggestions = remember(state) { CITIES_BY_STATE[state].orEmpty() }
    var cityExpanded by rememberSaveable { mutableStateOf(false) }
    val filteredCities = remember(city, citySuggestions) {
        if (city.isBlank()) citySuggestions
        else citySuggestions.filter { it.contains(city.trim(), ignoreCase = true) }
    }
    var stateExpanded by rememberSaveable { mutableStateOf(false) }

    val canContinue = googleLinked &&
            name.text.isNotBlank() &&
            dob.isNotBlank() &&
            age != null &&
            age!! >= 18 &&
            city.isNotBlank() &&
            state.isNotBlank() &&
            username.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Set up profile") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Complete your profile",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Set up your identity once so CoBFA can personalize insights, leaderboard, and account experience.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Privacy: Insights run on-device. Firebase is used for backup sync. Raw SMS bodies are never uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .size(104.dp)
                            .clickable(enabled = googleLinked) {
                                Log.d("ProfileSetup", "Avatar tapped. googleLinked=$googleLinked")
                                imagePickerLauncher.launch("image/*")
                            }
                    ) {
                        if (!profileImageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = profileImageUri,
                                contentDescription = "Profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Add profile photo",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                    }

                    Text(
                        "Profile photo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    TextButton(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        },
                        enabled = googleLinked
                    ) {
                        Text(if (profileImageUri.isNullOrBlank()) "Add photo" else "Change photo")
                    }

                    if (!profileImageUri.isNullOrBlank()) {
                        Text(
                            text = "Photo selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Optional now, customizable later",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Account link", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Link Google to continue and prefill your profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                }
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Identity", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = googleLinked,
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { raw ->
                            username = raw
                                .lowercase()
                                .replace(" ", "_")
                                .filter { it.isLetterOrDigit() || it == '_' }
                                .take(15)
                        },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = googleLinked,
                        singleLine = true,
                        supportingText = { Text("3–15 chars: a-z, 0-9, underscore") }
                    )

                    OutlinedTextField(
                        value = dob,
                        onValueChange = {},
                        label = { Text("Date of birth") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = googleLinked,
                        trailingIcon = {
                            IconButton(onClick = { openDatePicker() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select DOB")
                            }
                        }
                    )

                    age?.let {
                        Text(
                            text = "Age: $it years",
                            color = if (ageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (ageError) {
                        Text(
                            text = "You must be at least 18 years old to use this app.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Location", style = MaterialTheme.typography.titleMedium)

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
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded)
                            }
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
                            label = { Text("City") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = googleLinked && state.isNotBlank(),
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded)
                            }
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
                }
            }

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Expense tracking", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "If enabled, CoBFA scans your SMS inbox only when you open the app or pull to refresh. No background interception.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable auto-tracking")
                            Text(
                                "You can change this later in settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoTrackingEnabled,
                            enabled = googleLinked,
                            onCheckedChange = { autoTrackingEnabled = it }
                        )
                    }
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = canContinue,
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
                        photoUri = profileImageUri,
                        onSuccess = { onProfileCompleted() },
                        onError = { error -> profileError = error }
                    )
                }
            ) {
                Text("Continue")
            }

            linkVm.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            profileError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

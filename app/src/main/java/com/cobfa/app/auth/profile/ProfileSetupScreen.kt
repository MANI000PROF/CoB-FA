package com.cobfa.app.auth.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cobfa.app.R
import com.cobfa.app.auth.link.AccountLinkViewModel
import com.cobfa.app.auth.link.GoogleSignInHelper
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale

data class IndiaLocation(
    val city: String,
    val state: String,
    val country: String = "India"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val linkVm: AccountLinkViewModel = viewModel()
    val profileVm: ProfileViewModel = viewModel()
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val deviceId = remember { DeviceId.get(context) }

    val allLocations = remember { loadIndiaLocations(context) }

    var profileError by rememberSaveable { mutableStateOf<String?>(null) }
    var googleLinked by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var dob by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf<Int?>(null) }
    var ageError by rememberSaveable { mutableStateOf(false) }
    var city by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var autoTrackingEnabled by rememberSaveable { mutableStateOf(false) }
    var profileImageUri by rememberSaveable { mutableStateOf<String?>(null) }

    var stateExpanded by rememberSaveable { mutableStateOf(false) }
    var cityExpanded by rememberSaveable { mutableStateOf(false) }

    val allStates = remember(allLocations) {
        allLocations.map { it.state }.distinct().sorted()
    }

    val citiesForSelectedState = remember(state, allLocations) {
        allLocations
            .filter { it.state.equals(state, ignoreCase = true) }
            .map { it.city }
            .distinct()
            .sorted()
    }

    var isGoogleLinking by rememberSaveable { mutableStateOf(false) }
    var isSavingProfile by rememberSaveable { mutableStateOf(false) }
    var showProfileCreatedSuccess by rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val heroComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.profile_setup)
    )
    val heroProgress by animateLottieCompositionAsState(
        composition = heroComposition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    LaunchedEffect(linkVm.errorMessage, profileError) {
        val rawMsg = linkVm.errorMessage ?: profileError
        if (!rawMsg.isNullOrBlank()) {
            isGoogleLinking = false
            isSavingProfile = false

            val uiMsg = when {
                rawMsg.contains("FirebaseAuthUserCollisionException", ignoreCase = true) ||
                        rawMsg.contains("already", ignoreCase = true) && rawMsg.contains("use", ignoreCase = true) ->
                    "This Google account is already linked with another account. Please use a different Google account."

                else -> rawMsg
            }

            snackbarHostState.showSnackbar(uiMsg)
            profileError = null
        }
    }

    LaunchedEffect(showProfileCreatedSuccess) {
        if (showProfileCreatedSuccess) {
            delay(1800)
            onProfileCompleted()
        }
    }

    val googleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            GoogleSignInHelper.handleResult(
                result.data,
                onSuccess = { token ->
                    isGoogleLinking = true
                    linkVm.linkGoogleAccount(token, deviceId) { _ ->
                        isGoogleLinking = false
                        googleLinked = true

                        val user = FirebaseAuth.getInstance().currentUser
                        val googleProvider = user?.providerData
                            ?.firstOrNull { it.providerId == "google.com" }

                        val resolvedName = when {
                            !googleProvider?.displayName.isNullOrBlank() -> googleProvider?.displayName!!
                            !user?.displayName.isNullOrBlank() -> user?.displayName!!
                            !googleProvider?.email.isNullOrBlank() -> googleProvider?.email!!.substringBefore("@")
                            !user?.email.isNullOrBlank() -> user?.email!!.substringBefore("@")
                            else -> ""
                        }
                        name = TextFieldValue(resolvedName)

                        val uid = user?.uid
                        if (!uid.isNullOrBlank()) {
                            username = profileVm.suggestUsername(resolvedName, uid)
                        }

                        val providerPhotoUrl = googleProvider?.photoUrl?.toString()
                        val fallbackPhotoUrl = user?.photoUrl?.toString()
                        val resolvedPhotoUrl = providerPhotoUrl ?: fallbackPhotoUrl

                        if (!resolvedPhotoUrl.isNullOrBlank()) {
                            profileImageUri = resolvedPhotoUrl
                        }
                    }
                },
                onError = { error ->
                    isGoogleLinking = false
                    Log.e("ProfileSetup", "Google link failed: $error")
                    linkVm.errorMessage = error
                }
            )
        }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            profileImageUri = uri?.toString() ?: profileImageUri
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
            TopAppBar(
                title = { Text("Set up profile") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = !isGoogleLinking && !isSavingProfile && !showProfileCreatedSuccess
                ) { page ->
                    when (page) {
                        0 -> IntroPage(
                            composition = heroComposition,
                            progress = heroProgress
                        )

                        1 -> DetailsPage(
                            googleLinked = googleLinked,
                            isGoogleLinking = isGoogleLinking,
                            isSavingProfile = isSavingProfile,
                            profileImageUri = profileImageUri,
                            name = name,
                            username = username,
                            dob = dob,
                            age = age,
                            ageError = ageError,
                            city = city,
                            state = state,
                            allStates = allStates,
                            citiesForSelectedState = citiesForSelectedState,
                            stateExpanded = stateExpanded,
                            cityExpanded = cityExpanded,
                            onStateExpandedChange = { stateExpanded = it },
                            onCityExpandedChange = { cityExpanded = it },
                            onLinkGoogle = {
                                val safeActivity = activity ?: return@DetailsPage
                                isGoogleLinking = true
                                val client = GoogleSignInHelper.getClient(safeActivity)
                                client.signOut().addOnCompleteListener {
                                    googleLauncher.launch(client.signInIntent)
                                }
                            },
                            onPickImage = { imagePickerLauncher.launch("image/*") },
                            onNameChange = { name = it },
                            onUsernameChange = { raw ->
                                username = raw
                                    .lowercase()
                                    .replace(" ", "_")
                                    .filter { it.isLetterOrDigit() || it == '_' }
                                    .take(15)
                            },
                            onOpenDatePicker = { openDatePicker() },
                            onStateSelected = { selectedState ->
                                state = selectedState
                                city = ""
                                stateExpanded = false
                                cityExpanded = false
                            },
                            onCitySelected = { selectedCity ->
                                city = selectedCity
                                cityExpanded = false
                            }
                        )

                        2 -> PreferencesPage(
                            autoTrackingEnabled = autoTrackingEnabled,
                            googleLinked = googleLinked,
                            isBusy = isGoogleLinking || isSavingProfile,
                            canContinue = canContinue,
                            onToggleAutoTracking = { autoTrackingEnabled = it },
                            onCreateProfile = {
                                focusManager.clearFocus(force = true)

                                PreferenceManager.setAutoTrackingEnabled(
                                    context = context,
                                    enabled = autoTrackingEnabled
                                )

                                profileError = null
                                isSavingProfile = true

                                profileVm.saveProfile(
                                    name = name.text,
                                    dob = dob,
                                    age = age!!,
                                    city = city,
                                    state = state,
                                    username = username,
                                    photoUri = profileImageUri,
                                    onSuccess = {
                                        isSavingProfile = false
                                        showProfileCreatedSuccess = true
                                    },
                                    onError = { error ->
                                        isSavingProfile = false
                                        profileError = error
                                    }
                                )
                            }
                        )
                    }
                }

                PagerIndicatorRow(
                    currentPage = pagerState.currentPage,
                    pageCount = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            enabled = !isGoogleLinking && !isSavingProfile && !showProfileCreatedSuccess
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(64.dp))
                    }

                    if (pagerState.currentPage < 2) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            enabled = !isGoogleLinking && !isSavingProfile && !showProfileCreatedSuccess
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(64.dp))
                    }
                }
            }

            if (isGoogleLinking || isSavingProfile) {
                LottieLoaderOverlay()
            }

            if (showProfileCreatedSuccess) {
                ProfileCreatedSuccessOverlay()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroPage(
    composition: com.airbnb.lottie.LottieComposition?,
    progress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(220.dp)
                )

                Text(
                    text = "Welcome to CoB-FA",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Create your profile to personalize financial insights, enable secure sync, and make your account experience feel truly yours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SetupInfoChip(Icons.Default.Security, "Secure profile")
                    SetupInfoChip(Icons.Default.Sync, "Backup sync")
                    SetupInfoChip(Icons.Default.VerifiedUser, "Editable later")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Privacy first",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Insights run on-device. Firebase is used for backup sync, and raw SMS bodies are not uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsPage(
    googleLinked: Boolean,
    isGoogleLinking: Boolean,
    isSavingProfile: Boolean,
    profileImageUri: String?,
    name: TextFieldValue,
    username: String,
    dob: String,
    age: Int?,
    ageError: Boolean,
    city: String,
    state: String,
    allStates: List<String>,
    citiesForSelectedState: List<String>,
    stateExpanded: Boolean,
    cityExpanded: Boolean,
    onStateExpandedChange: (Boolean) -> Unit,
    onCityExpandedChange: (Boolean) -> Unit,
    onLinkGoogle: () -> Unit,
    onPickImage: () -> Unit,
    onNameChange: (TextFieldValue) -> Unit,
    onUsernameChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onStateSelected: (String) -> Unit,
    onCitySelected: (String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val pagePaddingH = (maxWidth * 0.05f).coerceIn(16.dp, 28.dp)
        val pagePaddingV = (maxHeight * 0.025f).coerceIn(16.dp, 28.dp)
        val sectionSpacing = (maxHeight * 0.02f).coerceIn(12.dp, 20.dp)
        val fieldSpacing = (maxWidth * 0.035f).coerceIn(10.dp, 16.dp)
        val compactWidth = maxWidth < 360.dp
        val mediumOrLargerWidth = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = pagePaddingH, vertical = pagePaddingV)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            SetupSectionCard(
                title = "Account link",
                subtitle = "Link Google first to prefill your details and profile photo"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !googleLinked && !isGoogleLinking && !isSavingProfile,
                        onClick = onLinkGoogle
                    ) {
                        Text(if (googleLinked) "Google account linked" else "Continue with Google")
                    }

                    if (!googleLinked) {
                        Text(
                            text = "Link Google first to unlock profile fields.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (googleLinked) {
                        Text(
                            text = "Google account linked successfully. You can still edit the details below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            SetupSectionCard(
                title = "Profile photo",
                subtitle = "Your Google photo is used automatically if available, and you can change it anytime"
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val avatarSize = when {
                        maxWidth < 360.dp -> (maxWidth * 0.34f).coerceIn(92.dp, 110.dp)
                        maxWidth < 600.dp -> (maxWidth * 0.30f).coerceIn(104.dp, 138.dp)
                        else -> (maxWidth * 0.22f).coerceIn(120.dp, 168.dp)
                    }

                    val iconSize = (avatarSize * 0.4f).coerceIn(36.dp, 56.dp)
                    val photoSpacing = (maxWidth * 0.025f).coerceIn(8.dp, 14.dp)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(photoSpacing)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(avatarSize)
                                .clickable(
                                    enabled = googleLinked && !isGoogleLinking && !isSavingProfile
                                ) {
                                    onPickImage()
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
                                        contentDescription = "Profile photo",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = onPickImage,
                            enabled = googleLinked && !isGoogleLinking && !isSavingProfile
                        ) {
                            Text(if (profileImageUri.isNullOrBlank()) "Add photo" else "Change photo")
                        }
                    }
                }
            }

            SetupSectionCard(
                title = "Identity",
                subtitle = "Confirm your details and choose a unique username"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = googleLinked && !isGoogleLinking && !isSavingProfile,
                        singleLine = true,
                        textStyle = if (compactWidth) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            LocalTextStyle.current
                        }
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = googleLinked && !isGoogleLinking && !isSavingProfile,
                        singleLine = true,
                        supportingText = { Text("3–15 chars: a-z, 0-9, underscore") },
                        textStyle = if (compactWidth) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            LocalTextStyle.current
                        }
                    )

                    OutlinedTextField(
                        value = dob,
                        onValueChange = {},
                        label = { Text("Date of birth") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = googleLinked && !isGoogleLinking && !isSavingProfile,
                        singleLine = true,
                        textStyle = if (compactWidth) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            LocalTextStyle.current
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onOpenDatePicker,
                                enabled = googleLinked && !isGoogleLinking && !isSavingProfile
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Select DOB"
                                )
                            }
                        }
                    )

                    age?.let {
                        Text(
                            text = "Age: $it years",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ageError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    if (ageError) {
                        Text(
                            text = "You must be at least 18 years old to use this app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            SetupSectionCard(
                title = "Location",
                subtitle = "Select your state first, then choose your city"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                ) {
                    if (mediumOrLargerWidth) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(fieldSpacing)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = stateExpanded,
                                onExpandedChange = {
                                    if (googleLinked && !isGoogleLinking && !isSavingProfile) {
                                        onStateExpandedChange(!stateExpanded)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = state,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("State") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    enabled = googleLinked && !isGoogleLinking && !isSavingProfile,
                                    singleLine = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded)
                                    }
                                )

                                ExposedDropdownMenu(
                                    expanded = stateExpanded,
                                    onDismissRequest = { onStateExpandedChange(false) }
                                ) {
                                    allStates.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = { onStateSelected(option) }
                                        )
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = cityExpanded && citiesForSelectedState.isNotEmpty(),
                                onExpandedChange = {
                                    if (
                                        googleLinked &&
                                        state.isNotBlank() &&
                                        !isGoogleLinking &&
                                        !isSavingProfile
                                    ) {
                                        onCityExpandedChange(!cityExpanded)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = city,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("City") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    enabled = googleLinked &&
                                            state.isNotBlank() &&
                                            !isGoogleLinking &&
                                            !isSavingProfile,
                                    singleLine = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = cityExpanded && citiesForSelectedState.isNotEmpty()
                                        )
                                    }
                                )

                                ExposedDropdownMenu(
                                    expanded = cityExpanded && citiesForSelectedState.isNotEmpty(),
                                    onDismissRequest = { onCityExpandedChange(false) }
                                ) {
                                    citiesForSelectedState.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = { onCitySelected(option) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = stateExpanded,
                            onExpandedChange = {
                                if (googleLinked && !isGoogleLinking && !isSavingProfile) {
                                    onStateExpandedChange(!stateExpanded)
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = state,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("State") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = googleLinked && !isGoogleLinking && !isSavingProfile,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded)
                                }
                            )

                            ExposedDropdownMenu(
                                expanded = stateExpanded,
                                onDismissRequest = { onStateExpandedChange(false) }
                            ) {
                                allStates.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { onStateSelected(option) }
                                    )
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = cityExpanded && citiesForSelectedState.isNotEmpty(),
                            onExpandedChange = {
                                if (
                                    googleLinked &&
                                    state.isNotBlank() &&
                                    !isGoogleLinking &&
                                    !isSavingProfile
                                ) {
                                    onCityExpandedChange(!cityExpanded)
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = city,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("City") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = googleLinked &&
                                        state.isNotBlank() &&
                                        !isGoogleLinking &&
                                        !isSavingProfile,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = cityExpanded && citiesForSelectedState.isNotEmpty()
                                    )
                                }
                            )

                            ExposedDropdownMenu(
                                expanded = cityExpanded && citiesForSelectedState.isNotEmpty(),
                                onDismissRequest = { onCityExpandedChange(false) }
                            ) {
                                citiesForSelectedState.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { onCitySelected(option) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesPage(
    autoTrackingEnabled: Boolean,
    googleLinked: Boolean,
    isBusy: Boolean,
    canContinue: Boolean,
    onToggleAutoTracking: (Boolean) -> Unit,
    onCreateProfile: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val pagePaddingH = (maxWidth * 0.05f).coerceIn(16.dp, 28.dp)
        val pagePaddingV = (maxHeight * 0.025f).coerceIn(16.dp, 28.dp)
        val sectionSpacing = (maxHeight * 0.02f).coerceIn(12.dp, 20.dp)
        val contentSpacing = (maxWidth * 0.03f).coerceIn(10.dp, 16.dp)
        val cardPadding = (maxWidth * 0.045f).coerceIn(14.dp, 24.dp)
        val buttonHeight = (maxHeight * 0.07f).coerceIn(48.dp, 58.dp)
        val compactWidth = maxWidth < 360.dp
        val wideLayout = maxWidth >= 520.dp

        val titleStyle = when {
            compactWidth -> MaterialTheme.typography.titleSmall
            maxWidth < 600.dp -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleLarge
        }

        val bodyStyle = when {
            compactWidth -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyMedium
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = pagePaddingH, vertical = pagePaddingV)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            SetupSectionCard(
                title = "Expense tracking",
                subtitle = "Choose whether CoB-FA can scan transaction SMS when you open or refresh the app"
            ) {
                if (wideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Enable auto-tracking",
                                style = if (compactWidth) {
                                    MaterialTheme.typography.bodyMedium
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "No background interception. You can change this later in settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = autoTrackingEnabled,
                            enabled = googleLinked && !isBusy,
                            onCheckedChange = onToggleAutoTracking
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        Text(
                            text = "Enable auto-tracking",
                            style = if (compactWidth) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "No background interception. You can change this later in settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = autoTrackingEnabled,
                                enabled = googleLinked && !isBusy,
                                onCheckedChange = onToggleAutoTracking
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(cardPadding),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing)
                ) {
                    Text(
                        text = "Ready to create your profile?",
                        style = titleStyle,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "We will save your identity, username, location, and preferences so your account experience feels personalized from the start.",
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                        enabled = canContinue && !isBusy,
                        onClick = onCreateProfile
                    ) {
                        Text(
                            text = "Create profile",
                            style = if (compactWidth) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.titleSmall
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerIndicatorRow(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(if (selected) 26.dp else 8.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

@Composable
private fun SetupSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val cardPadding = (maxWidth * 0.045f).coerceIn(14.dp, 24.dp)
        val sectionSpacing = (maxWidth * 0.035f).coerceIn(12.dp, 18.dp)
        val titleSubtitleSpacing = (maxWidth * 0.012f).coerceIn(4.dp, 8.dp)
        val elevation = if (maxWidth < 360.dp) 1.dp else 2.dp

        val titleStyle = when {
            maxWidth < 360.dp -> MaterialTheme.typography.titleSmall
            maxWidth < 600.dp -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleLarge
        }

        val subtitleStyle = when {
            maxWidth < 360.dp -> MaterialTheme.typography.bodySmall
            maxWidth < 600.dp -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyMedium
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(titleSubtitleSpacing)
                ) {
                    Text(
                        text = title,
                        style = titleStyle,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = subtitle,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                content()
            }
        }
    }
}

@Composable
private fun SetupInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun LottieLoaderOverlay() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.loader)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(120.dp)
                )
                Text(
                    text = "Please wait...",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun ProfileCreatedSuccessOverlay() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.profile_creation_success)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
        restartOnPlay = true
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(180.dp)
                )
                Text(
                    text = "Profile created successfully",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun loadIndiaLocations(context: Context): List<IndiaLocation> {
    return try {
        val json = context.assets.open("india_locations.json")
            .bufferedReader()
            .use { it.readText() }

        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val city = obj.optString("city").trim()
                val state = obj.optString("state").trim()
                val country = obj.optString("country").trim().ifBlank { "India" }

                if (city.isNotBlank() && state.isNotBlank()) {
                    add(IndiaLocation(city = city, state = state, country = country))
                }
            }
        }.distinctBy { "${it.city.lowercase(Locale.getDefault())}|${it.state.lowercase(Locale.getDefault())}" }
            .sortedWith(compareBy({ it.city.lowercase(Locale.getDefault()) }, { it.state.lowercase(Locale.getDefault()) }))
    } catch (e: Exception) {
        Log.e("ProfileSetup", "Failed to load india_locations.json", e)
        emptyList()
    }
}

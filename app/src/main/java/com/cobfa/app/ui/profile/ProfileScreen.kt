package com.cobfa.app.ui.profile

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenSmsPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val profileScreenVM: ProfileScreenViewModel = viewModel()
    val uiState by profileScreenVM.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            profileScreenVM.clearMessage()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            profileScreenVM.clearMessage()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                profileScreenVM.refreshPreferenceState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                profileScreenVM.uploadAndSaveProfilePhoto(uri.toString())
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        enabled = !uiState.isSaving
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileHeroCard(
                    user = uiState.user,
                    isSaving = uiState.isSaving,
                    onChangePhoto = { photoPickerLauncher.launch("image/*") }
                )

                PersonalDetailsCard(
                    user = uiState.user,
                    onEditClick = { showEditDialog = true }
                )

                PreferencesCard(
                    autoTrackingEnabled = uiState.autoTrackingEnabled,
                    smsPermissionGranted = uiState.smsPermissionGranted,
                    smsPermissionDecided = uiState.smsPermissionDecided,
                    lastSmsTimestamp = uiState.lastSmsTimestamp,
                    onToggleAutoTracking = { checked ->
                        if (checked && !uiState.smsPermissionGranted) {
                            onOpenSmsPermission()
                        } else {
                            profileScreenVM.updateAutoTracking(checked)
                        }
                    },
                    onManageSms = onOpenSmsPermission
                )

                SecurityCard(
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initial = uiState.user,
            isSaving = uiState.isSaving,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                profileScreenVM.updateProfile(
                    name = updated.name,
                    username = updated.username,
                    phone = updated.phone,
                    email = updated.email,
                    city = updated.city,
                    state = updated.state,
                    country = updated.country,
                    dob = updated.dob,
                    occupation = updated.occupation
                )
                showEditDialog = false
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out of your account?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PreferencesCard(
    autoTrackingEnabled: Boolean,
    smsPermissionGranted: Boolean,
    smsPermissionDecided: Boolean,
    lastSmsTimestamp: Long,
    onToggleAutoTracking: (Boolean) -> Unit,
    onManageSms: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tracking, permissions, and import activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ProfileSettingTile(
                title = "Automatic expense tracking",
                subtitle = if (smsPermissionGranted) {
                    if (autoTrackingEnabled) {
                        "Enabled for transaction import on app open or refresh"
                    } else {
                        "Permission available, currently turned off"
                    }
                } else {
                    "SMS permission not granted"
                },
                icon = Icons.Default.PhoneAndroid,
                trailing = {
                    Switch(
                        checked = autoTrackingEnabled,
                        onCheckedChange = onToggleAutoTracking
                    )
                }
            )

            ProfileSettingTile(
                title = "SMS access",
                subtitle = when {
                    smsPermissionGranted -> "Granted"
                    smsPermissionDecided -> "Not granted"
                    else -> "Not decided"
                },
                icon = Icons.Default.VerifiedUser,
                trailing = {
                    TextButton(onClick = onManageSms) {
                        Text("Manage")
                    }
                }
            )

            ProfileSettingTile(
                title = "Last import activity",
                subtitle = if (lastSmsTimestamp > 0L) {
                    DateUtils.getRelativeTimeSpanString(lastSmsTimestamp).toString()
                } else {
                    "No import activity yet"
                },
                icon = Icons.Default.Badge
            )
        }
    }
}

@Composable
private fun SecurityCard(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Security & privacy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Account safety, sync, and permission controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ProfileSettingTile(
                title = "Account security",
                subtitle = "Signed in and synced with your account",
                icon = Icons.Default.Lock
            )

            ProfileSettingTile(
                title = "App settings",
                subtitle = "Privacy, permissions, sync, and tracking controls",
                icon = Icons.Default.Settings,
                trailing = {
                    TextButton(onClick = onOpenSettings) {
                        Text("Open")
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileSettingTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            trailing?.invoke()
        }
    }
}

@Composable
private fun ProfileHeroCard(
    user: UserProfileUi,
    isSaving: Boolean,
    onChangePhoto: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .size(96.dp)
                    .clickable(enabled = !isSaving, onClick = onChangePhoto)
            ) {
                if (user.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
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
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Text(
                text = user.name.ifBlank { "Your name" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (user.username.isBlank()) "@username" else "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = user.phone.ifBlank { "Phone number not set" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    initial: UserProfileUi,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfileUi) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var username by remember(initial) { mutableStateOf(initial.username) }
    var phone by remember(initial) { mutableStateOf(initial.phone) }
    var email by remember(initial) { mutableStateOf(initial.email) }
    var city by remember(initial) { mutableStateOf(initial.city) }
    var state by remember(initial) { mutableStateOf(initial.state) }
    var country by remember(initial) { mutableStateOf(initial.country) }
    var dob by remember(initial) { mutableStateOf(initial.dob) }
    var occupation by remember(initial) { mutableStateOf(initial.occupation) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, singleLine = true)
                OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, singleLine = true)
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("DOB") }, singleLine = true)
                OutlinedTextField(value = occupation, onValueChange = { occupation = it }, label = { Text("Occupation") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        UserProfileUi(
                            name = name,
                            username = username,
                            phone = phone,
                            email = email,
                            city = city,
                            state = state,
                            country = country,
                            dob = dob,
                            occupation = occupation,
                            photoUrl = initial.photoUrl
                        )
                    )
                },
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PersonalDetailsCard(
    user: UserProfileUi,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Personal details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Your account information and identity details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Edit")
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileDetailItem(
                    label = "Username",
                    value = if (user.username.isBlank()) "Not set" else "@${user.username}",
                    icon = Icons.Default.Person
                )
                ProfileDetailItem(
                    label = "Phone",
                    value = user.phone,
                    icon = Icons.Default.PhoneAndroid
                )
                ProfileDetailItem(
                    label = "Email",
                    value = user.email,
                    icon = Icons.Default.VerifiedUser
                )
                ProfileDetailItem(
                    label = "City",
                    value = user.city,
                    icon = Icons.Default.Badge
                )
                ProfileDetailItem(
                    label = "State",
                    value = user.state,
                    icon = Icons.Default.Badge
                )
                ProfileDetailItem(
                    label = "Country",
                    value = user.country,
                    icon = Icons.Default.Badge
                )
                ProfileDetailItem(
                    label = "Date of birth",
                    value = user.dob,
                    icon = Icons.Default.Badge
                )
                ProfileDetailItem(
                    label = "Occupation",
                    value = user.occupation,
                    icon = Icons.Default.Badge
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value.ifBlank { "Not set" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


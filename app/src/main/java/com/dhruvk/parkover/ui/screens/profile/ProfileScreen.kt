package com.dhruvk.parkover.ui.screens.profile

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.MenuDivider
import com.dhruvk.parkover.ui.components.MenuRow
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.screens.home.initials
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleLight
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.AuthViewModel
import com.dhruvk.parkover.viewmodels.VehicleViewModel
import com.google.firebase.auth.FirebaseAuth
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    accountViewModel: AccountViewModel,
    vehicleViewModel: VehicleViewModel,
    onLogout: () -> Unit,
    onSavedVehicles: () -> Unit,
    onPaymentMethods: () -> Unit = {},
    onHelpSupport: () -> Unit = {},
    onBookings: () -> Unit = {},
    onSaved: () -> Unit = {},
    /** Set for operator accounts browsing as a driver: takes them back to their parking place. */
    onOperatorMode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val authUser = FirebaseAuth.getInstance().currentUser
    val profile = authViewModel.currentUser.collectAsStateWithLifecycle().value
    val saving = authViewModel.profileSaving.collectAsStateWithLifecycle().value
    val message = authViewModel.message.collectAsStateWithLifecycle().value
    val bookings = accountViewModel.bookings.collectAsStateWithLifecycle().value
    val savedIds = accountViewModel.savedIds.collectAsStateWithLifecycle().value
    val vehicles = vehicleViewModel.vehicles.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditName by remember { mutableStateOf(false) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        authViewModel.loadCurrentUser()
        vehicleViewModel.refresh()
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            authViewModel.clearMessage()
        }
    }

    val displayName = profile?.name?.ifBlank { null } ?: authUser?.displayName?.ifBlank { null }
        ?: if (authUser?.isAnonymous == true) "Dev Tester" else "Add your name"
    val email = authUser?.email?.ifBlank { null } ?: if (authUser?.isAnonymous == true) "Signed in with dev login" else ""
    val photoUrl: String? = profile?.profilePhotoUrl ?: authUser?.photoUrl?.toString()

    // ---- Photo pickers ----
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(authViewModel::uploadProfilePhoto)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cameraUri?.let(authViewModel::uploadProfilePhoto)
    }
    fun launchCamera() {
        val dir = File(context.getExternalFilesDir(null), "Pictures").apply { mkdirs() }
        val file = File(dir, "profile_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = BottomNavReservedHeight),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Profile", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

            // Identity card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardDark)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { showPhotoOptions = true }) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PurpleLight, Purple))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(model = photoUrl, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(initials(displayName), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        }
                        if (saving) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, CardDark, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = "Change photo", tint = Purple, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    if (email.isNotEmpty()) {
                        Text(email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                    }
                }
                Surface(
                    onClick = { showEditName = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit name", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Counts
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountTile(Icons.Rounded.CalendarMonth, bookings?.size?.toString() ?: "–", "Bookings", Modifier.weight(1f), onBookings)
                CountTile(Icons.Rounded.Bookmark, savedIds.size.toString(), "Saved", Modifier.weight(1f), onSaved)
                CountTile(Icons.Rounded.DirectionsCar, vehicles.size.toString(), "Vehicles", Modifier.weight(1f), onSavedVehicles)
            }

            if (onOperatorMode != null) {
                ParkCard(contentPadding = PaddingValues(0.dp)) {
                    MenuRow(
                        icon = Icons.Rounded.Storefront,
                        title = "Back to operator mode",
                        subtitle = "Today's bookings and bays at your parking place",
                        onClick = onOperatorMode
                    )
                }
            }

            SectionLabel("Account", Modifier.padding(top = 4.dp))
            ParkCard(contentPadding = PaddingValues(0.dp)) {
                MenuRow(
                    icon = Icons.Rounded.DirectionsCar,
                    title = "My vehicles",
                    subtitle = vehicles.firstOrNull { it.isDefault }?.let { "Default: ${it.displayName}" },
                    onClick = onSavedVehicles
                )
                MenuDivider()
                MenuRow(icon = Icons.Rounded.AccountBalanceWallet, title = "Payment methods", onClick = onPaymentMethods)
                MenuDivider()
                MenuRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Booking reminders",
                    titleColor = TextTertiary,
                    tint = TextTertiary,
                    onClick = {},
                    trailing = { StatusBadge("Soon", BadgeTone.Neutral) }
                )
            }

            SectionLabel("Support")
            ParkCard(contentPadding = PaddingValues(0.dp)) {
                MenuRow(icon = Icons.AutoMirrored.Rounded.Help, title = "Help & support", onClick = onHelpSupport)
                MenuDivider()
                MenuRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "Log out",
                    titleColor = ErrorRed,
                    tint = ErrorRed,
                    iconContainer = Color(0xFFFEF2F2),
                    onClick = { showLogoutDialog = true },
                    trailing = null
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomNavReservedHeight)
        )
    }

    if (showPhotoOptions) {
        ModalBottomSheet(onDismissRequest = { showPhotoOptions = false }, containerColor = Color.White) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "Profile photo",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                MenuRow(icon = Icons.Rounded.PhotoLibrary, title = "Choose from gallery", trailing = null, onClick = {
                    showPhotoOptions = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                MenuRow(icon = Icons.Rounded.PhotoCamera, title = "Take a photo", trailing = null, onClick = {
                    showPhotoOptions = false
                    cameraPermission.launch(Manifest.permission.CAMERA)
                })
            }
        }
    }

    if (showEditName) {
        var name by remember { mutableStateOf(if (displayName == "Add your name") "" else displayName) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showEditName = false },
            containerColor = Color.White,
            title = { Text("Your name") },
            text = {
                ParkTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = "Full name",
                    error = error,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    when {
                        trimmed.length < 2 -> error = "Use at least 2 letters"
                        !trimmed.matches(Regex("^[\\p{L} .'-]+$")) -> error = "Use letters and spaces only"
                        else -> {
                            authViewModel.updateDisplayName(trimmed)
                            showEditName = false
                        }
                    }
                }) { Text("Save", color = Purple, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showEditName = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color.White,
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = ErrorRed) },
            title = { Text("Log out?") },
            text = { Text("You'll need to log in again to see your bookings.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.signOut()
                    onLogout()
                }) { Text("Log out", color = ErrorRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun CountTile(icon: ImageVector, value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    ParkCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = Purple, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

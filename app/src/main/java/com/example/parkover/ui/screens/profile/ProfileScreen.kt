package com.example.parkover.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.parkover.R
import com.example.parkover.ui.theme.ErrorRed
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.example.parkover.viewmodels.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onSavedVehicles: () -> Unit
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    val currentUser = authViewModel.currentUser.collectAsStateWithLifecycle().value

    LaunchedEffect(Unit) {
        authViewModel.loadCurrentUser()
    }

    val displayName = currentUser?.name?.ifEmpty { null } ?: user?.displayName ?: "User"
    val email = user?.email ?: ""
    val photoUrl: String? = currentUser?.profilePhotoUrl ?: user?.photoUrl?.toString()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile photo
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photoUrl ?: R.drawable.ic_default_profile)
                .crossfade(true)
                .transformations(CircleCropTransformation())
                .build(),
            contentDescription = "Profile Photo",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = displayName,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Text(
            text = email,
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Menu items
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                ProfileMenuItem(
                    icon = Icons.Default.Edit,
                    title = "Edit Profile",
                    onClick = { /* TODO */ }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileMenuItem(
                    icon = Icons.Default.Payment,
                    title = "Payment Methods",
                    onClick = { /* TODO */ }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileMenuItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    onClick = { /* TODO */ }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileMenuItem(
                    icon = Icons.Default.DirectionsCar,
                    title = "Saved Vehicles",
                    onClick = onSavedVehicles
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileMenuItem(
                    icon = Icons.Default.Help,
                    title = "Help & Support",
                    onClick = { /* TODO */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logout
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ProfileMenuItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Log Out",
                tint = ErrorRed,
                onClick = {
                    authViewModel.signOut()
                    onLogout()
                }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

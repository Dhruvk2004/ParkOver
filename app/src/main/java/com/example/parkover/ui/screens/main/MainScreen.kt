package com.example.parkover.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.parkover.navigation.Screen
import com.example.parkover.ui.screens.booking.BookingListScreen
import com.example.parkover.ui.screens.home.HomeScreen
import com.example.parkover.ui.screens.profile.ProfileScreen
import com.example.parkover.ui.screens.saved.SavedScreen
import com.example.parkover.ui.theme.NavInactive
import com.example.parkover.ui.theme.Purple
import com.example.parkover.viewmodels.AuthViewModel
import com.example.parkover.viewmodels.ParkingViewModel

data class BottomNavItem(
    val icon: ImageVector,
    val label: String
)

@Composable
fun MainScreen(
    parkingViewModel: ParkingViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController,
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val navItems = listOf(
        BottomNavItem(Icons.Default.Home, "Home"),
        BottomNavItem(Icons.Default.Bookmark, "Saved"),
        BottomNavItem(Icons.Default.CalendarMonth, "Booking"),
        BottomNavItem(Icons.Default.Person, "Profile")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Content
        when (selectedTab) {
            0 -> HomeScreen(
                parkingViewModel = parkingViewModel,
                onParkingSpotClick = { spotId ->
                    navController.navigate(Screen.ParkingDetails.createRoute(spotId))
                },
                onProfileClick = { selectedTab = 3 }
            )
            1 -> SavedScreen(
                onParkingClick = { spotId ->
                    navController.navigate(Screen.ParkingDetails.createRoute(spotId))
                }
            )
            2 -> BookingListScreen()
            3 -> ProfileScreen(
                authViewModel = authViewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSavedVehicles = {
                    navController.navigate(Screen.SavedVehicles.route)
                }
            )
        }

        // Bottom Navigation Bar
        BottomNavBar(
            items = navItems,
            selectedIndex = selectedTab,
            onItemSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                BottomNavItemView(
                    item = item,
                    isSelected = index == selectedIndex,
                    onClick = { onItemSelected(index) }
                )
            }
        }
    }
}

@Composable
fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(Purple)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (isSelected) Color.White else NavInactive,
            modifier = Modifier.size(24.dp)
        )
    }
}

package com.example.parkover.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.example.parkover.R
import com.example.parkover.ui.theme.NavSurface
import com.example.parkover.ui.theme.PurpleDark

data class BottomNavItem(
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
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
    var previousTab by remember { mutableIntStateOf(initialTab) }

    val navItems = listOf(
        BottomNavItem(R.drawable.ic_home_outline, R.drawable.ic_home_filled, "Home"),
        BottomNavItem(R.drawable.ic_saved_outline, R.drawable.ic_saved_filled, "Saved"),
        BottomNavItem(R.drawable.ic_booking_outline, R.drawable.ic_booking_filled, "Bookings"),
        BottomNavItem(R.drawable.ic_profile_outline, R.drawable.ic_profile_filled, "Profile")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Content with animated transitions
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState > previousTab) 1 else -1
                (slideInHorizontally { width -> direction * width / 4 } + fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(250)
                )).togetherWith(
                    slideOutHorizontally { width -> -direction * width / 4 } + fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    )
                )
            },
            label = "tab_content"
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    parkingViewModel = parkingViewModel,
                    onParkingSpotClick = { spotId ->
                        navController.navigate(Screen.ParkingDetails.createRoute(spotId))
                    },
                    onProfileClick = {
                        previousTab = selectedTab
                        selectedTab = 3
                    }
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
                    },
                    onPaymentMethods = {
                        navController.navigate(Screen.PaymentMethods.route)
                    },
                    onHelpSupport = {
                        navController.navigate(Screen.HelpSupport.route)
                    }
                )
            }
        }

        // Bottom Navigation Bar
        BottomNavBar(
            items = navItems,
            selectedIndex = selectedTab,
            onItemSelected = {
                previousTab = selectedTab
                selectedTab = it
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// Floating pill nav, styled after the XML custom_bottom_nav: lavender surface,
// rounded-square selected item, outline icons that switch to filled when selected
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(8.dp, shape = CircleShape, spotColor = PurpleDark)
                .clip(CircleShape)
                .background(NavSurface)
                .selectableGroup()
                .padding(horizontal = 8.dp),
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
    val background by animateColorAsState(
        targetValue = if (isSelected) Purple else Color.Transparent,
        animationSpec = tween(200),
        label = "nav_item_bg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "nav_item_scale"
    )

    Row(
        modifier = Modifier
            .height(48.dp)
            .defaultMinSize(minWidth = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(horizontal = 12.dp)
            .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
            contentDescription = if (isSelected) null else item.label,
            tint = if (isSelected) Color.White else NavInactive,
            modifier = Modifier.size(22.dp)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

package com.dhruvk.parkover.ui.screens.main

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dhruvk.parkover.R
import com.dhruvk.parkover.navigation.Screen
import com.dhruvk.parkover.navigation.Tab
import com.dhruvk.parkover.ui.screens.booking.BookingListScreen
import com.dhruvk.parkover.ui.screens.home.HomeScreen
import com.dhruvk.parkover.ui.screens.profile.ProfileScreen
import com.dhruvk.parkover.ui.screens.saved.SavedScreen
import com.dhruvk.parkover.ui.theme.NavInactive
import com.dhruvk.parkover.ui.theme.NavSurface
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleDark
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.AuthViewModel
import com.dhruvk.parkover.viewmodels.HomeViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel
import com.dhruvk.parkover.viewmodels.VehicleViewModel

data class BottomNavItem(
    val tab: Tab,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
    val label: String
)

private val navItems = listOf(
    BottomNavItem(Tab.Home, R.drawable.ic_home_outline, R.drawable.ic_home_filled, "Home"),
    BottomNavItem(Tab.Saved, R.drawable.ic_saved_outline, R.drawable.ic_saved_filled, "Saved"),
    BottomNavItem(Tab.Bookings, R.drawable.ic_booking_outline, R.drawable.ic_booking_filled, "Bookings"),
    BottomNavItem(Tab.Profile, R.drawable.ic_profile_outline, R.drawable.ic_profile_filled, "Profile")
)

/**
 * Bottom-nav shell. Tabs live in their own NavHost so each keeps its state (map camera,
 * scroll, search) when you switch, and Back from any tab returns to Home before exiting.
 */
@Composable
fun MainScreen(
    parkingViewModel: ParkingViewModel,
    authViewModel: AuthViewModel,
    accountViewModel: AccountViewModel,
    vehicleViewModel: VehicleViewModel,
    homeViewModel: HomeViewModel,
    navController: NavHostController,
    initialTab: Tab = Tab.Home
) {
    val tabNavController = rememberNavController()
    val currentRoute = tabNavController.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.fromRoute(currentRoute)

    fun selectTab(tab: Tab) {
        if (tab == currentTab && currentRoute != null) return
        tabNavController.navigate(tab.route) {
            popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialTab) {
        if (initialTab != Tab.Home) selectTab(initialTab)
    }

    fun openParking(spotId: String) = navController.navigate(Screen.ParkingDetails.createRoute(spotId))

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = tabNavController,
            startDestination = Tab.Home.route,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    parkingViewModel = parkingViewModel,
                    homeViewModel = homeViewModel,
                    accountViewModel = accountViewModel,
                    onParkingSpotClick = ::openParking,
                    onProfileClick = { selectTab(Tab.Profile) }
                )
            }
            composable(Tab.Saved.route) {
                SavedScreen(
                    parkingViewModel = parkingViewModel,
                    accountViewModel = accountViewModel,
                    homeViewModel = homeViewModel,
                    onParkingClick = ::openParking,
                    onExplore = { selectTab(Tab.Home) }
                )
            }
            composable(Tab.Bookings.route) {
                BookingListScreen(
                    accountViewModel = accountViewModel,
                    parkingViewModel = parkingViewModel,
                    onFindParking = { selectTab(Tab.Home) }
                )
            }
            composable(Tab.Profile.route) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    accountViewModel = accountViewModel,
                    vehicleViewModel = vehicleViewModel,
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSavedVehicles = { navController.navigate(Screen.SavedVehicles.route) },
                    onPaymentMethods = { navController.navigate(Screen.PaymentMethods.route) },
                    onHelpSupport = { navController.navigate(Screen.HelpSupport.route) },
                    onBookings = { selectTab(Tab.Bookings) },
                    onSaved = { selectTab(Tab.Saved) }
                )
            }
        }

        BottomNavBar(
            items = navItems,
            selectedIndex = navItems.indexOfFirst { it.tab == currentTab },
            onItemSelected = { index -> selectTab(navItems[index].tab) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Height the bottom nav occupies; scrolling content pads its end by this much. */
val BottomNavReservedHeight = 112.dp

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

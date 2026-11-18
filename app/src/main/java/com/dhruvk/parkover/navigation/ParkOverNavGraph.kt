package com.dhruvk.parkover.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dhruvk.parkover.ui.screens.operator.OperatorBookingScreen
import com.dhruvk.parkover.ui.screens.operator.OperatorShell
import com.dhruvk.parkover.ui.screens.operator.ScanScreen
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.viewmodels.OperatorViewModel
import com.dhruvk.parkover.viewmodels.RoleState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.dhruvk.parkover.ui.screens.auth.LoginScreen
import com.dhruvk.parkover.ui.screens.auth.SignUpScreen
import com.dhruvk.parkover.ui.screens.auth.SplashScreen
import com.dhruvk.parkover.ui.screens.auth.StartScreen
import com.dhruvk.parkover.ui.screens.booking.BookingSuccessScreen
import com.dhruvk.parkover.ui.screens.booking.ConfirmBookingScreen
import com.dhruvk.parkover.ui.screens.booking.ParkingDetailsScreen
import com.dhruvk.parkover.ui.screens.booking.PaymentScreen
import com.dhruvk.parkover.ui.screens.booking.ReviewSummaryScreen
import com.dhruvk.parkover.ui.screens.booking.SelectFloorScreen
import com.dhruvk.parkover.ui.screens.booking.SelectVehicleScreen
import com.dhruvk.parkover.ui.screens.main.MainScreen
import com.dhruvk.parkover.ui.screens.profile.HelpSupportScreen
import com.dhruvk.parkover.ui.screens.profile.PaymentMethodsScreen
import com.dhruvk.parkover.ui.screens.profile.SavedVehiclesScreen
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.AuthViewModel
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import com.dhruvk.parkover.viewmodels.HomeViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel
import com.dhruvk.parkover.viewmodels.VehicleViewModel

private const val NAV_MS = 300

// Push: slide 30% + fade, like the XML slide_in_right / slide_out_left
private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(NAV_MS)) { it / 3 } + fadeIn(tween(NAV_MS))
}
private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(NAV_MS)) { -it / 3 } + fadeOut(tween(NAV_MS))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(NAV_MS)) { -it / 3 } + fadeIn(tween(NAV_MS))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(NAV_MS)) { it / 3 } + fadeOut(tween(NAV_MS))
}

@Composable
fun ParkOverNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    // App-wide ViewModels (activity scope)
    val authViewModel: AuthViewModel = viewModel()
    val parkingViewModel: ParkingViewModel = viewModel()
    val accountViewModel: AccountViewModel = viewModel()
    val vehicleViewModel: VehicleViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val operatorViewModel: OperatorViewModel = viewModel()
    val role by operatorViewModel.role.collectAsState()

    // Operator actions report back here, whichever operator screen is showing.
    val context = LocalContext.current
    val operatorMessage by operatorViewModel.message.collectAsState()
    LaunchedEffect(operatorMessage) {
        operatorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            operatorViewModel.clearMessage()
        }
    }

    /** After sign-in: the account decides whether this is a driver or an operator. */
    fun goHome() {
        navController.navigate(Screen.RoleGate.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun goToOperator() {
        navController.navigate(Screen.Operator.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun logOut() {
        authViewModel.signOut()
        operatorViewModel.signedOut()
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    fun goToMain(tab: Tab = Tab.Home) {
        navController.navigate(Screen.Main.createRoute(tab)) {
            // Clear auth / flow screens so Back from Main leaves the app
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = pushEnter,
        exitTransition = pushExit,
        popEnterTransition = popEnter,
        popExitTransition = popExit
    ) {
        // ---------- Auth ----------
        composable(
            Screen.Splash.route,
            exitTransition = { fadeOut(tween(250)) }
        ) {
            SplashScreen(
                onNavigateToMain = { goHome() },
                onNavigateToStart = {
                    navController.navigate(Screen.Start.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Start.route, enterTransition = { fadeIn(tween(250)) }) {
            StartScreen(
                onGetStarted = { navController.navigate(Screen.Login.route) },
                onCreateAccount = { navController.navigate(Screen.SignUp.route) }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { goHome() },
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                viewModel = authViewModel,
                onSignUpSuccess = { goHome() },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }

        // ---------- Role ----------
        composable(Screen.RoleGate.route, enterTransition = { fadeIn(tween(150)) }) {
            LaunchedEffect(Unit) {
                when (operatorViewModel.checkRole()) {
                    is RoleState.Operator -> goToOperator()
                    else -> goToMain()
                }
            }
            Box(Modifier.fillMaxSize().background(BackgroundLight), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
        }

        // ---------- Operator ----------
        composable(Screen.Operator.route, enterTransition = { fadeIn(tween(NAV_MS)) }) {
            val profile = (role as? RoleState.Operator)?.profile
            if (profile == null) {
                LaunchedEffect(Unit) { goHome() }
            } else {
                OperatorShell(
                    profile = profile,
                    viewModel = operatorViewModel,
                    onScan = { navController.navigate(Screen.OperatorScan.route) },
                    onOpenBooking = { navController.navigate(Screen.OperatorBooking.createRoute(it)) },
                    onDriverMode = { goToMain() },
                    onLogout = ::logOut
                )
            }
        }

        composable(Screen.OperatorScan.route) {
            ScanScreen(
                viewModel = operatorViewModel,
                parkingId = operatorViewModel.profile()?.parkingId.orEmpty(),
                onClose = { navController.navigateUp() },
                onOpenBooking = { id ->
                    navController.navigate(Screen.OperatorBooking.createRoute(id)) {
                        popUpTo(Screen.OperatorScan.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.OperatorBooking.route,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { entry ->
            OperatorBookingScreen(
                bookingId = entry.arguments?.getString("bookingId").orEmpty(),
                viewModel = operatorViewModel,
                onBack = { navController.navigateUp() }
            )
        }

        // ---------- Main tabs ----------
        composable(
            route = Screen.Main.route,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = Tab.Home.route }),
            enterTransition = { fadeIn(tween(NAV_MS)) }
        ) { entry ->
            MainScreen(
                parkingViewModel = parkingViewModel,
                authViewModel = authViewModel,
                accountViewModel = accountViewModel,
                vehicleViewModel = vehicleViewModel,
                homeViewModel = homeViewModel,
                navController = navController,
                initialTab = Tab.fromRoute(entry.arguments?.getString("tab")),
                onOperatorMode = if (role is RoleState.Operator) ({ goToOperator() }) else null
            )
        }

        // ---------- Booking flow ----------
        navigation(startDestination = Screen.ParkingDetails.route, route = Screen.BOOKING_FLOW) {
            composable(
                route = Screen.ParkingDetails.route,
                arguments = listOf(navArgument("parkingId") { type = NavType.StringType })
            ) { entry ->
                ParkingDetailsScreen(
                    parkingId = entry.arguments?.getString("parkingId").orEmpty(),
                    parkingViewModel = parkingViewModel,
                    accountViewModel = accountViewModel,
                    flowViewModel = entry.flowViewModel(navController),
                    onBack = { navController.navigateUp() },
                    onBookParking = { navController.navigate(Screen.SelectVehicle.route) }
                )
            }

            composable(Screen.SelectVehicle.route) { entry ->
                SelectVehicleScreen(
                    flowViewModel = entry.flowViewModel(navController),
                    vehicleViewModel = vehicleViewModel,
                    onBack = { navController.navigateUp() },
                    onContinue = { navController.navigate(Screen.SelectFloor.route) }
                )
            }

            composable(Screen.SelectFloor.route) { entry ->
                SelectFloorScreen(
                    flowViewModel = entry.flowViewModel(navController),
                    parkingViewModel = parkingViewModel,
                    onBack = { navController.navigateUp() },
                    onContinue = { navController.navigate(Screen.ConfirmBooking.route) }
                )
            }

            composable(Screen.ConfirmBooking.route) { entry ->
                ConfirmBookingScreen(
                    flowViewModel = entry.flowViewModel(navController),
                    onBack = { navController.navigateUp() },
                    onContinue = { navController.navigate(Screen.ReviewSummary.route) }
                )
            }

            composable(Screen.ReviewSummary.route) { entry ->
                ReviewSummaryScreen(
                    flowViewModel = entry.flowViewModel(navController),
                    onBack = { navController.navigateUp() },
                    onEdit = { route -> navController.popBackStack(route, inclusive = false) },
                    onContinue = { navController.navigate(Screen.Payment.route) }
                )
            }

            composable(Screen.Payment.route) { entry ->
                PaymentScreen(
                    flowViewModel = entry.flowViewModel(navController),
                    accountViewModel = accountViewModel,
                    onBack = { navController.navigateUp() },
                    onAddUpi = { navController.navigate(Screen.PaymentMethods.route) },
                    onPaymentSuccess = { bookingId ->
                        navController.navigate(Screen.BookingSuccess.createRoute(bookingId)) {
                            // Leaving the flow clears its ViewModel; Success loads the saved booking
                            popUpTo(Screen.BOOKING_FLOW) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(
            route = Screen.BookingSuccess.route,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
            enterTransition = { fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.92f) }
        ) { entry ->
            BookingSuccessScreen(
                bookingId = entry.arguments?.getString("bookingId").orEmpty(),
                onViewBooking = { goToMain(Tab.Bookings) },
                onBackHome = { goToMain(Tab.Home) }
            )
        }

        // ---------- Profile sub-screens ----------
        composable(Screen.SavedVehicles.route) {
            SavedVehiclesScreen(vehicleViewModel = vehicleViewModel, onBack = { navController.navigateUp() })
        }

        composable(Screen.PaymentMethods.route) {
            PaymentMethodsScreen(accountViewModel = accountViewModel, onBack = { navController.navigateUp() })
        }

        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(accountViewModel = accountViewModel, onBack = { navController.navigateUp() })
        }
    }
}

/** BookingFlowViewModel shared by every screen in the booking_flow graph. */
@Composable
private fun NavBackStackEntry.flowViewModel(navController: NavController): BookingFlowViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(Screen.BOOKING_FLOW) }
    return viewModel(parentEntry)
}

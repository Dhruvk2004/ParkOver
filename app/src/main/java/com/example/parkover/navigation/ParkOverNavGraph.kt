package com.example.parkover.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.parkover.ui.screens.auth.LoginScreen
import com.example.parkover.ui.screens.auth.SignUpScreen
import com.example.parkover.ui.screens.auth.SplashScreen
import com.example.parkover.ui.screens.auth.StartScreen
import com.example.parkover.ui.screens.booking.BookingSuccessScreen
import com.example.parkover.ui.screens.booking.ConfirmBookingScreen
import com.example.parkover.ui.screens.booking.ParkingDetailsScreen
import com.example.parkover.ui.screens.booking.PaymentScreen
import com.example.parkover.ui.screens.booking.ReviewSummaryScreen
import com.example.parkover.ui.screens.booking.SelectFloorScreen
import com.example.parkover.ui.screens.booking.SelectVehicleScreen
import com.example.parkover.ui.screens.main.MainScreen
import com.example.parkover.viewmodels.AuthViewModel
import com.example.parkover.viewmodels.BookingFlowViewModel
import com.example.parkover.viewmodels.ParkingViewModel

@Composable
fun ParkOverNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    val authViewModel: AuthViewModel = viewModel()
    val parkingViewModel: ParkingViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth flow
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToStart = {
                    navController.navigate(Screen.Start.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Start.route) {
            StartScreen(
                onGetStarted = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        popUpTo(Screen.Start.route) { inclusive = true }
                    }
                },
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
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                        popUpTo(Screen.Start.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }

        // Main screen with bottom nav (Home, Saved, Booking, Profile)
        composable(Screen.Home.route) {
            MainScreen(
                parkingViewModel = parkingViewModel,
                authViewModel = authViewModel,
                navController = navController,
                initialTab = 0
            )
        }

        composable(Screen.Saved.route) {
            MainScreen(
                parkingViewModel = parkingViewModel,
                authViewModel = authViewModel,
                navController = navController,
                initialTab = 1
            )
        }

        composable(Screen.Booking.route) {
            MainScreen(
                parkingViewModel = parkingViewModel,
                authViewModel = authViewModel,
                navController = navController,
                initialTab = 2
            )
        }

        composable(Screen.Profile.route) {
            MainScreen(
                parkingViewModel = parkingViewModel,
                authViewModel = authViewModel,
                navController = navController,
                initialTab = 3
            )
        }

        // Booking flow
        composable(
            route = Screen.ParkingDetails.route,
            arguments = listOf(navArgument("parkingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val parkingId = backStackEntry.arguments?.getString("parkingId") ?: ""
            ParkingDetailsScreen(
                parkingId = parkingId,
                parkingViewModel = parkingViewModel,
                onBack = { navController.navigateUp() },
                onBookParking = {
                    navController.navigate(Screen.SelectVehicle.route)
                }
            )
        }

        composable(Screen.SelectVehicle.route) {
            SelectVehicleScreen(
                onBack = { navController.navigateUp() },
                onContinue = {
                    navController.navigate(Screen.SelectFloor.route)
                }
            )
        }

        composable(Screen.SelectFloor.route) {
            SelectFloorScreen(
                onBack = { navController.navigateUp() },
                onContinue = {
                    navController.navigate(Screen.ConfirmBooking.route)
                }
            )
        }

        composable(Screen.ConfirmBooking.route) {
            ConfirmBookingScreen(
                onBack = { navController.navigateUp() },
                onContinue = {
                    navController.navigate(Screen.ReviewSummary.route)
                }
            )
        }

        composable(Screen.ReviewSummary.route) {
            ReviewSummaryScreen(
                onBack = { navController.navigateUp() },
                onContinue = {
                    navController.navigate(Screen.Payment.route)
                }
            )
        }

        composable(Screen.Payment.route) {
            PaymentScreen(
                onBack = { navController.navigateUp() },
                onPaymentSuccess = { bookingId ->
                    navController.navigate(Screen.BookingSuccess.createRoute(bookingId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Screen.BookingSuccess.route,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            BookingSuccessScreen(
                bookingId = bookingId,
                onViewBooking = {
                    navController.navigate(Screen.Booking.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onBackHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        // Profile sub-screens
        composable(Screen.SavedVehicles.route) {
            SavedVehiclesScreen(
                onBack = { navController.navigateUp() }
            )
        }
    }
}

// Placeholder - will be implemented in the profile package
@Composable
fun SavedVehiclesScreen(onBack: () -> Unit) {
    com.example.parkover.ui.screens.profile.SavedVehiclesScreen(onBack = onBack)
}

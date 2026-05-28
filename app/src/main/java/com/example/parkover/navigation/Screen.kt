package com.example.parkover.navigation

sealed class Screen(val route: String) {
    // Auth flow
    object Splash : Screen("splash")
    object Start : Screen("start")
    object Login : Screen("login")
    object SignUp : Screen("signup")

    // Main tabs
    object Home : Screen("home")
    object Saved : Screen("saved")
    object Booking : Screen("booking")
    object Profile : Screen("profile")

    // Booking flow
    object ParkingDetails : Screen("parking_details/{parkingId}") {
        fun createRoute(parkingId: String) = "parking_details/$parkingId"
    }
    object SelectVehicle : Screen("select_vehicle")
    object SelectFloor : Screen("select_floor")
    object ConfirmBooking : Screen("confirm_booking")
    object ReviewSummary : Screen("review_summary")
    object Payment : Screen("payment")
    object BookingSuccess : Screen("booking_success/{bookingId}") {
        fun createRoute(bookingId: String) = "booking_success/$bookingId"
    }

    // Profile sub-screens
    object SavedVehicles : Screen("saved_vehicles")
}

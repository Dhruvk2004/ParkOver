package com.dhruvk.parkover.navigation

sealed class Screen(val route: String) {
    // Auth flow
    object Splash : Screen("splash")
    object Start : Screen("start")
    object Login : Screen("login")
    object SignUp : Screen("signup")

    // Main shell with the bottom-nav tabs (one instance; tabs keep their own state)
    object Main : Screen("main?tab={tab}") {
        fun createRoute(tab: Tab = Tab.Home) = "main?tab=${tab.route}"
    }

    // Booking flow (nested graph "booking_flow", shares one BookingFlowViewModel)
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
    object PaymentMethods : Screen("payment_methods")
    object HelpSupport : Screen("help_support")

    companion object {
        const val BOOKING_FLOW = "booking_flow"
    }
}

/** Bottom-nav tabs inside [Screen.Main]. */
enum class Tab(val route: String) {
    Home("tab_home"),
    Saved("tab_saved"),
    Bookings("tab_bookings"),
    Profile("tab_profile");

    companion object {
        fun fromRoute(route: String?): Tab = entries.firstOrNull { it.route == route } ?: Home
    }
}

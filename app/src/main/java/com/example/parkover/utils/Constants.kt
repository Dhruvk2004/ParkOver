package com.example.parkover.utils

object Constants {
    // Firebase Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_VEHICLES = "vehicles"
    const val COLLECTION_PARKING_SPOTS = "parking_spots"
    const val COLLECTION_BOOKINGS = "bookings"
    const val COLLECTION_REVIEWS = "reviews"
    
    // SharedPreferences
    const val PREFS_NAME = "parkover_prefs"
    const val PREF_USER_ID = "user_id"
    const val PREF_IS_LOGGED_IN = "is_logged_in"
    const val PREF_FIRST_LAUNCH = "first_launch"
    
    // Map Settings
    const val DEFAULT_ZOOM = 15.0
    const val SEARCH_RADIUS_KM = 5.0
    const val DEFAULT_LAT = 28.6139 // Delhi
    const val DEFAULT_LNG = 77.2090
    
    // Booking
    const val MIN_BOOKING_HOURS = 1
    const val MAX_BOOKING_HOURS = 24
    const val TAX_PERCENTAGE = 18.0 // GST
    
    // Validation
    const val MIN_PASSWORD_LENGTH = 6
    const val VEHICLE_NUMBER_REGEX = "^[A-Z]{2}[0-9]{1,2}[A-Z]{1,2}[0-9]{4}$"
    
    // Request Codes
    const val REQUEST_LOCATION_PERMISSION = 1001
    const val REQUEST_CAMERA_PERMISSION = 1002
    const val REQUEST_STORAGE_PERMISSION = 1003
}

package com.example.parkover.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class to hold booking information as user progresses through the booking flow
 */
@Parcelize
data class BookingData(
    val parkingId: String = "",
    val parkingName: String = "",
    val parkingAddress: String = "",
    val parkingImage: String = "",
    val pricePerHour: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    
    // Vehicle selection
    var vehicleId: String = "",
    var vehicleName: String = "",
    var vehicleNumber: String = "",
    var vehicleType: VehicleType = VehicleType.FOUR_WHEELER,
    
    // Floor and spot selection
    var floorNumber: Int = 0,
    var floorName: String = "",
    var spotId: String = "",
    var spotNumber: String = "",
    
    // Time selection
    var checkInTime: Long = System.currentTimeMillis(),
    var checkOutTime: Long = System.currentTimeMillis(),
    var durationHours: Int = 1,
    
    // Specifications
    var needsWheelchairAccess: Boolean = false,
    var needsEvCharging: Boolean = false,
    
    // Pricing
    var basePrice: Double = 0.0,
    var taxAmount: Double = 0.0,
    var totalPrice: Double = 0.0,
    
    // Payment
    var paymentMethod: PaymentMethod = PaymentMethod.UPI
) : Parcelable {
    
    fun calculatePricing() {
        basePrice = pricePerHour * durationHours
        taxAmount = basePrice * 0.10 // 10% tax
        totalPrice = basePrice + taxAmount
    }
}

/**
 * Represents a single parking spot in a floor
 */
@Parcelize
data class ParkingSlot(
    val id: String = "",
    val spotNumber: String = "",
    val floorNumber: Int = 0,
    val isAvailable: Boolean = true,
    val isBooked: Boolean = false,
    val bookedUntil: Long? = null,
    val vehicleType: VehicleType = VehicleType.FOUR_WHEELER,
    val hasWheelchairAccess: Boolean = false,
    val hasEvCharging: Boolean = false,
    val row: Int = 0,
    val column: Int = 0 // 0 = left, 1 = right
) : Parcelable {
    
    fun isAvailableForBooking(checkInTime: Long, checkOutTime: Long): Boolean {
        if (!isAvailable) return false
        if (!isBooked) return true
        
        // Check if the booking time conflicts
        bookedUntil?.let { until ->
            return checkInTime >= until
        }
        return true
    }
}

/**
 * Floor data with spots
 */
@Parcelize
data class FloorData(
    val floorNumber: Int = 0,
    val name: String = "",
    val spots: List<ParkingSlot> = emptyList(),
    val totalSpots: Int = 0,
    val availableSpots: Int = 0
) : Parcelable

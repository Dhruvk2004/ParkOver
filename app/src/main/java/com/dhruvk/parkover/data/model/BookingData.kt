package com.dhruvk.parkover.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Booking-in-progress, filled in step by step by BookingFlowViewModel:
 * Details → Vehicle → Spot → Time → Review → Payment
 */
@Parcelize
data class BookingData(
    val parkingId: String = "",
    val parkingName: String = "",
    val parkingAddress: String = "",
    val parkingImage: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    // Vehicle selection
    val vehicleId: String = "",
    val vehicleName: String = "",
    val vehicleNumber: String = "",
    val vehicleType: VehicleType = VehicleType.FOUR_WHEELER,

    // Floor and spot selection
    val floorNumber: Int = 0,
    val floorName: String = "",
    val floorMultiplier: Double = 1.0,
    val spotId: String = "",
    val spotNumber: String = "",

    // Time selection
    val checkInTime: Long = 0L,
    val checkOutTime: Long = 0L,
    val durationHours: Int = 2,

    // Specifications
    val needsWheelchairAccess: Boolean = false,
    val needsEvCharging: Boolean = false,

    // Pricing (rate already includes the floor multiplier)
    val pricePerHour: Double = 0.0,
    val basePrice: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalPrice: Double = 0.0,

    // Payment
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val upiId: String = ""
) : Parcelable

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
) : Parcelable

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

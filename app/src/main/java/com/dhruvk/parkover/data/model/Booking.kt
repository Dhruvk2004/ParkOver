package com.dhruvk.parkover.data.model

import com.google.firebase.Timestamp

data class Booking(
    val id: String = "",
    val userId: String = "",
    val parkingId: String = "",
    val parkingName: String = "", // Denormalized for quick display
    val parkingAddress: String = "",
    val vehicleId: String = "",
    val vehicleNumber: String = "", // Denormalized
    val vehicleType: VehicleType = VehicleType.FOUR_WHEELER,
    val floorNumber: Int = 0,
    val floorName: String = "",
    val spotNumber: String = "",
    val entryTime: Timestamp = Timestamp.now(),
    val exitTime: Timestamp = Timestamp.now(),
    val durationHours: Int = 1,
    val basePrice: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val totalPrice: Double = 0.0,
    val couponCode: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val bookingStatus: BookingStatus = BookingStatus.PENDING,
    val qrCodeData: String = "",
    val transactionId: String? = null,
    val extraCharges: Double = 0.0, // Overstay, worked out at check-out
    val actualExitTime: Timestamp? = null, // When the operator checked the car out
    val checkedInAt: Timestamp? = null, // When the operator marked the car arrived
    val driverName: String = "", // Denormalized so the operator sees who is coming
    val walkIn: Boolean = false, // Bay blocked by the operator (walk-in car, repair, staff)
    val blockReason: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    constructor() : this(id = "")
    
    fun isOngoing(): Boolean {
        val now = Timestamp.now()
        return (bookingStatus == BookingStatus.CONFIRMED || bookingStatus == BookingStatus.ACTIVE) &&
               now.toDate().before(exitTime.toDate())
    }
    
    fun isPast(): Boolean {
        return bookingStatus == BookingStatus.COMPLETED || 
               bookingStatus == BookingStatus.CANCELLED
    }
}

enum class PaymentMethod {
    UPI,
    NET_BANKING,
    CARD,
    CASH // Pay at parking
}

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED
}

/**
 * Booked → Arrived → Exited, or Didn't come. Only the parking's operator moves a booking past
 * Booked (check-in, check-out, didn't come); the driver can only cancel.
 */
enum class BookingStatus {
    PENDING,    // Booking created, payment pending
    CONFIRMED,  // "Booked": waiting for the driver to arrive
    ACTIVE,     // "Arrived": the operator checked the car in; it's parked
    COMPLETED,  // "Exited": the operator checked the car out
    CANCELLED,  // The driver cancelled
    EXPIRED     // "Didn't come": marked by the operator after trying to reach the driver
}

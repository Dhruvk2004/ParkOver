package com.example.parkover.data.model

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
    val extraCharges: Double = 0.0, // For overstay
    val actualExitTime: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    constructor() : this(id = "")
    
    fun isOngoing(): Boolean {
        val now = Timestamp.now()
        return bookingStatus == BookingStatus.CONFIRMED || 
               bookingStatus == BookingStatus.ACTIVE &&
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

enum class BookingStatus {
    PENDING,    // Booking created, payment pending
    CONFIRMED,  // Payment done, waiting for entry time
    ACTIVE,     // Currently parked
    COMPLETED,  // Exited parking
    CANCELLED,  // User cancelled
    EXPIRED     // User didn't show up
}

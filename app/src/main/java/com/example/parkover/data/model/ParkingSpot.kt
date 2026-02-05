package com.example.parkover.data.model

import com.google.firebase.Timestamp

data class ParkingSpot(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val pricePerHourTwoWheeler: Double = 0.0,
    val pricePerHourFourWheeler: Double = 0.0,
    val pricePerHourHeavy: Double = 0.0,
    val pricePerDayTwoWheeler: Double = 0.0,
    val pricePerDayFourWheeler: Double = 0.0,
    val pricePerDayHeavy: Double = 0.0,
    val totalSpotsTwoWheeler: Int = 0,
    val totalSpotsFourWheeler: Int = 0,
    val totalSpotsHeavy: Int = 0,
    val availableSpotsTwoWheeler: Int = 0,
    val availableSpotsFourWheeler: Int = 0,
    val availableSpotsHeavy: Int = 0,
    val floors: List<Floor> = emptyList(),
    val amenities: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val operatingHours: OperatingHours = OperatingHours(),
    val isActive: Boolean = true,
    val ownerId: String = "",
    val createdAt: Timestamp = Timestamp.now()
) {
    constructor() : this(id = "")
    
    fun getTotalAvailableSpots(): Int {
        return availableSpotsTwoWheeler + availableSpotsFourWheeler + availableSpotsHeavy
    }
    
    fun getTotalSpots(): Int {
        return totalSpotsTwoWheeler + totalSpotsFourWheeler + totalSpotsHeavy
    }
    
    fun getAvailabilityStatus(): AvailabilityStatus {
        val total = getTotalSpots()
        val available = getTotalAvailableSpots()
        return when {
            available == 0 -> AvailabilityStatus.FULL
            available.toDouble() / total < 0.2 -> AvailabilityStatus.LIMITED
            else -> AvailabilityStatus.AVAILABLE
        }
    }
}

data class Floor(
    val floorNumber: Int = 0,
    val name: String = "", // e.g., "Ground Floor", "Floor 1", "Basement"
    val totalSpots: Int = 0,
    val availableSpots: Int = 0,
    val priceMultiplier: Double = 1.0 // For premium floors
) {
    constructor() : this(floorNumber = 0)
}

data class OperatingHours(
    val is24Hours: Boolean = false,
    val openTime: String = "06:00", // HH:mm format
    val closeTime: String = "22:00",
    val closedDays: List<Int> = emptyList() // 0 = Sunday, 6 = Saturday
) {
    constructor() : this(is24Hours = false)
}

enum class AvailabilityStatus {
    AVAILABLE,
    LIMITED,
    FULL
}

// Amenities constants
object ParkingAmenities {
    const val CCTV = "CCTV"
    const val COVERED = "Covered"
    const val EV_CHARGING = "EV Charging"
    const val WASHROOM = "Washroom"
    const val SECURITY = "24/7 Security"
    const val WHEELCHAIR_ACCESS = "Wheelchair Access"
    const val VALET = "Valet Parking"
    const val CAR_WASH = "Car Wash"
}

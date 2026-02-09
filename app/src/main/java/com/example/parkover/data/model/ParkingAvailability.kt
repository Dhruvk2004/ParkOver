package com.example.parkover.data.model

import com.google.firebase.Timestamp

data class ParkingAvailability(
    val spotId: String = "",
    val availableSpotsTwoWheeler: Int = 0,
    val availableSpotsFourWheeler: Int = 0,
    val availableSpotsHeavy: Int = 0,
    val floorAvailability: Map<String, Int> = emptyMap(), // floorNumber -> availableSpots
    val lastUpdated: Timestamp = Timestamp.now()
) {
    constructor() : this(spotId = "")
}

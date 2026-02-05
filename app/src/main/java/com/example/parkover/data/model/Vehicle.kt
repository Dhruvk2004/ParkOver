package com.example.parkover.data.model

import com.example.parkover.R

enum class VehicleType {
    TWO_WHEELER,
    FOUR_WHEELER,
    HEAVY
}

data class Vehicle(
    val id: String = "",
    val userId: String = "",
    val type: VehicleType = VehicleType.FOUR_WHEELER,
    val number: String = "",
    val model: String = "",
    val brand: String = "",
    val color: String = "",
    val photoUrl: String? = null,
    val imageResId: Int = R.drawable.car_land_cruiser, // Local drawable resource
    val isDefault: Boolean = false,
    val isPreset: Boolean = false // For default vehicle options
) {
    constructor() : this(id = "")
}

// Default vehicle presets
object DefaultVehicles {
    val presets = listOf(
        Vehicle(id = "preset_1", type = VehicleType.TWO_WHEELER, brand = "Honda", model = "Activa", isPreset = true),
        Vehicle(id = "preset_2", type = VehicleType.TWO_WHEELER, brand = "TVS", model = "Jupiter", isPreset = true),
        Vehicle(id = "preset_3", type = VehicleType.TWO_WHEELER, brand = "Royal Enfield", model = "Classic 350", isPreset = true),
        Vehicle(id = "preset_4", type = VehicleType.FOUR_WHEELER, brand = "Maruti Suzuki", model = "Swift", isPreset = true),
        Vehicle(id = "preset_5", type = VehicleType.FOUR_WHEELER, brand = "Hyundai", model = "i20", isPreset = true),
        Vehicle(id = "preset_6", type = VehicleType.FOUR_WHEELER, brand = "Tata", model = "Nexon", isPreset = true),
        Vehicle(id = "preset_7", type = VehicleType.FOUR_WHEELER, brand = "Honda", model = "City", isPreset = true),
        Vehicle(id = "preset_8", type = VehicleType.FOUR_WHEELER, brand = "Mahindra", model = "XUV700", isPreset = true)
    )
}

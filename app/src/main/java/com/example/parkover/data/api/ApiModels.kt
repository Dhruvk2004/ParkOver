package com.example.parkover.data.api

import com.google.gson.annotations.SerializedName

// ============ Vehicle API Models ============

data class VehiclesResponse(
    @SerializedName("vehicleTypes") val vehicleTypes: List<VehicleTypeApi>,
    @SerializedName("brands") val brands: List<BrandApi>,
    @SerializedName("presetVehicles") val presetVehicles: List<PresetVehicleApi>,
    @SerializedName("colors") val colors: List<ColorApi>
)

data class VehicleTypeApi(
    @SerializedName("type") val type: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("icon") val icon: String
)

data class BrandApi(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("logoUrl") val logoUrl: String,
    @SerializedName("vehicleTypes") val vehicleTypes: List<String>
)

data class PresetVehicleApi(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("brand") val brand: String,
    @SerializedName("model") val model: String,
    @SerializedName("imageUrl") val imageUrl: String
)

data class ColorApi(
    @SerializedName("name") val name: String,
    @SerializedName("hex") val hex: String
)

// ============ Parking Spots API Models ============

data class ParkingSpotsResponse(
    @SerializedName("parkingSpots") val parkingSpots: List<ParkingSpotApi>,
    @SerializedName("amenitiesMaster") val amenitiesMaster: List<AmenityApi>
)

data class ParkingSpotApi(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("pricePerHourTwoWheeler") val pricePerHourTwoWheeler: Double,
    @SerializedName("pricePerHourFourWheeler") val pricePerHourFourWheeler: Double,
    @SerializedName("pricePerHourHeavy") val pricePerHourHeavy: Double,
    @SerializedName("pricePerDayTwoWheeler") val pricePerDayTwoWheeler: Double,
    @SerializedName("pricePerDayFourWheeler") val pricePerDayFourWheeler: Double,
    @SerializedName("pricePerDayHeavy") val pricePerDayHeavy: Double,
    @SerializedName("totalSpotsTwoWheeler") val totalSpotsTwoWheeler: Int,
    @SerializedName("totalSpotsFourWheeler") val totalSpotsFourWheeler: Int,
    @SerializedName("totalSpotsHeavy") val totalSpotsHeavy: Int,
    @SerializedName("floors") val floors: List<FloorApi>,
    @SerializedName("amenities") val amenities: List<String>,
    @SerializedName("images") val images: List<String>,
    @SerializedName("rating") val rating: Double,
    @SerializedName("reviewCount") val reviewCount: Int,
    @SerializedName("operatingHours") val operatingHours: OperatingHoursApi,
    @SerializedName("isActive") val isActive: Boolean
)

data class FloorApi(
    @SerializedName("floorNumber") val floorNumber: Int,
    @SerializedName("name") val name: String,
    @SerializedName("totalSpots") val totalSpots: Int,
    @SerializedName("priceMultiplier") val priceMultiplier: Double
)

data class OperatingHoursApi(
    @SerializedName("is24Hours") val is24Hours: Boolean,
    @SerializedName("openTime") val openTime: String,
    @SerializedName("closeTime") val closeTime: String,
    @SerializedName("closedDays") val closedDays: List<Int>
)

data class AmenityApi(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String
)

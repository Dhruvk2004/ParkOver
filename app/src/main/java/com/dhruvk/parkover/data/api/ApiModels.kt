package com.dhruvk.parkover.data.api

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

// Fields are nullable: the JSON is hand-edited and Gson leaves missing fields null.
data class ParkingSpotApi(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("pricePerHourTwoWheeler") val pricePerHourTwoWheeler: Double? = null,
    @SerializedName("pricePerHourFourWheeler") val pricePerHourFourWheeler: Double? = null,
    @SerializedName("pricePerHourHeavy") val pricePerHourHeavy: Double? = null,
    @SerializedName("pricePerDayTwoWheeler") val pricePerDayTwoWheeler: Double? = null,
    @SerializedName("pricePerDayFourWheeler") val pricePerDayFourWheeler: Double? = null,
    @SerializedName("pricePerDayHeavy") val pricePerDayHeavy: Double? = null,
    @SerializedName("totalSpotsTwoWheeler") val totalSpotsTwoWheeler: Int? = null,
    @SerializedName("totalSpotsFourWheeler") val totalSpotsFourWheeler: Int? = null,
    @SerializedName("totalSpotsHeavy") val totalSpotsHeavy: Int? = null,
    @SerializedName("floors") val floors: List<FloorApi>? = null,
    @SerializedName("amenities") val amenities: List<String>? = null,
    @SerializedName("images") val images: List<String>? = null,
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("reviewCount") val reviewCount: Int? = null,
    @SerializedName("operatingHours") val operatingHours: OperatingHoursApi? = null,
    @SerializedName("isActive") val isActive: Boolean? = null
)

data class FloorApi(
    @SerializedName("floorNumber") val floorNumber: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("totalSpots") val totalSpots: Int? = null,
    @SerializedName("priceMultiplier") val priceMultiplier: Double? = null
)

data class OperatingHoursApi(
    @SerializedName("is24Hours") val is24Hours: Boolean? = null,
    @SerializedName("openTime") val openTime: String? = null,
    @SerializedName("closeTime") val closeTime: String? = null,
    @SerializedName("closedDays") val closedDays: List<String>? = null
)

data class AmenityApi(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String
)

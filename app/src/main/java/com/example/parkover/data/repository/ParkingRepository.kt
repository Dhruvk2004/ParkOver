package com.example.parkover.data.repository

import com.example.parkover.data.api.ParkingSpotApi
import com.example.parkover.data.api.ParkingSpotsResponse
import com.example.parkover.data.api.PresetVehicleApi
import com.example.parkover.data.api.RetrofitClient
import com.example.parkover.data.api.VehiclesResponse
import com.example.parkover.data.model.Floor
import com.example.parkover.data.model.OperatingHours
import com.example.parkover.data.model.ParkingSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

class ParkingRepository {
    
    private val apiService = RetrofitClient.apiService
    
    suspend fun getVehiclesData(): ApiResult<VehiclesResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getVehicles()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("Failed to fetch vehicles: ${response.message()}")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    suspend fun getParkingSpotsData(): ApiResult<ParkingSpotsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getParkingSpots()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("Failed to fetch parking spots: ${response.message()}")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    // Convert API model to domain model
    suspend fun getParkingSpots(): ApiResult<List<ParkingSpot>> = withContext(Dispatchers.IO) {
        when (val result = getParkingSpotsData()) {
            is ApiResult.Success -> {
                val spots = result.data.parkingSpots.map { it.toDomainModel() }
                ApiResult.Success(spots)
            }
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }
    
    suspend fun getPresetVehicles(): ApiResult<List<PresetVehicleApi>> = withContext(Dispatchers.IO) {
        when (val result = getVehiclesData()) {
            is ApiResult.Success -> ApiResult.Success(result.data.presetVehicles)
            is ApiResult.Error -> result
            is ApiResult.Loading -> result
        }
    }
    
    // Extension function to convert API model to domain model
    private fun ParkingSpotApi.toDomainModel(): ParkingSpot {
        return ParkingSpot(
            id = this.id,
            name = this.name,
            address = this.address,
            latitude = this.latitude,
            longitude = this.longitude,
            pricePerHourTwoWheeler = this.pricePerHourTwoWheeler,
            pricePerHourFourWheeler = this.pricePerHourFourWheeler,
            pricePerHourHeavy = this.pricePerHourHeavy,
            pricePerDayTwoWheeler = this.pricePerDayTwoWheeler,
            pricePerDayFourWheeler = this.pricePerDayFourWheeler,
            pricePerDayHeavy = this.pricePerDayHeavy,
            totalSpotsTwoWheeler = this.totalSpotsTwoWheeler,
            totalSpotsFourWheeler = this.totalSpotsFourWheeler,
            totalSpotsHeavy = this.totalSpotsHeavy,
            // Available spots will come from Firestore (dynamic data)
            availableSpotsTwoWheeler = this.totalSpotsTwoWheeler,
            availableSpotsFourWheeler = this.totalSpotsFourWheeler,
            availableSpotsHeavy = this.totalSpotsHeavy,
            floors = this.floors.map { floor ->
                Floor(
                    floorNumber = floor.floorNumber,
                    name = floor.name,
                    totalSpots = floor.totalSpots,
                    availableSpots = floor.totalSpots, // Will be updated from Firestore
                    priceMultiplier = floor.priceMultiplier
                )
            },
            amenities = this.amenities,
            images = this.images,
            rating = this.rating,
            reviewCount = this.reviewCount,
            operatingHours = OperatingHours(
                is24Hours = this.operatingHours.is24Hours,
                openTime = this.operatingHours.openTime,
                closeTime = this.operatingHours.closeTime,
                closedDays = this.operatingHours.closedDays
            ),
            isActive = this.isActive
        )
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ParkingRepository? = null
        
        fun getInstance(): ParkingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ParkingRepository().also { INSTANCE = it }
            }
        }
    }
}

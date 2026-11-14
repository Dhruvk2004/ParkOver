package com.dhruvk.parkover.data.repository

import android.util.Log
import com.dhruvk.parkover.data.api.ParkingSpotApi
import com.dhruvk.parkover.data.api.ParkingSpotsResponse
import com.dhruvk.parkover.data.api.PresetVehicleApi
import com.dhruvk.parkover.data.api.RetrofitClient
import com.dhruvk.parkover.data.api.VehiclesResponse
import com.dhruvk.parkover.data.model.Floor
import com.dhruvk.parkover.data.model.OperatingHours
import com.dhruvk.parkover.data.model.ParkingSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ParkingRepository"

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

class ParkingRepository : com.dhruvk.parkover.di.ParkingSource {
    
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
            Log.d(TAG, "Fetching parking spots from GitHub API...")
            val response = apiService.getParkingSpots()
            Log.d(TAG, "API Response code: ${response.code()}")
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "Successfully fetched ${body.parkingSpots.size} parking spots")
                if (body.parkingSpots.isNotEmpty()) {
                    val first = body.parkingSpots.first()
                    Log.d(TAG, "First spot from API: ${first.name} at (${first.latitude}, ${first.longitude})")
                }
                ApiResult.Success(body)
            } else {
                Log.e(TAG, "API Error: ${response.code()} - ${response.message()}")
                ApiResult.Error("Failed to fetch parking spots: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching parking spots", e)
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    // Convert API model to domain model
    override suspend fun getParkingSpots(): ApiResult<List<ParkingSpot>> = withContext(Dispatchers.IO) {
        when (val result = getParkingSpotsData()) {
            is ApiResult.Success -> {
                ApiResult.Success(parkingSpotsFromApi(result.data.parkingSpots))
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

/**
 * Maps the parking list from the API, skipping entries that can't be shown (no id, name or
 * position) instead of failing the whole list because of one bad row.
 */
internal fun parkingSpotsFromApi(spots: List<ParkingSpotApi?>?): List<ParkingSpot> =
    spots.orEmpty().mapNotNull { api -> api?.let { runCatching { it.toDomainModel() }.getOrNull() } }

/** API model → domain model, or null when required fields are missing. */
internal fun ParkingSpotApi.toDomainModel(): ParkingSpot? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val name = name?.takeIf { it.isNotBlank() } ?: return null
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    if (lat == 0.0 && lng == 0.0) return null
    val two = totalSpotsTwoWheeler ?: 0
    val four = totalSpotsFourWheeler ?: 0
    val heavy = totalSpotsHeavy ?: 0
    return ParkingSpot(
        id = id,
        name = name,
        address = address.orEmpty(),
        latitude = lat,
        longitude = lng,
        pricePerHourTwoWheeler = pricePerHourTwoWheeler ?: 0.0,
        pricePerHourFourWheeler = pricePerHourFourWheeler ?: 0.0,
        pricePerHourHeavy = pricePerHourHeavy ?: 0.0,
        pricePerDayTwoWheeler = pricePerDayTwoWheeler ?: 0.0,
        pricePerDayFourWheeler = pricePerDayFourWheeler ?: 0.0,
        pricePerDayHeavy = pricePerDayHeavy ?: 0.0,
        totalSpotsTwoWheeler = two,
        totalSpotsFourWheeler = four,
        totalSpotsHeavy = heavy,
        // Live free counts come from Firestore; start as "all free".
        availableSpotsTwoWheeler = two,
        availableSpotsFourWheeler = four,
        availableSpotsHeavy = heavy,
        floors = floors.orEmpty().filterNotNull().map { floor ->
            Floor(
                floorNumber = floor.floorNumber ?: 0,
                name = floor.name.orEmpty(),
                totalSpots = floor.totalSpots ?: 0,
                availableSpots = floor.totalSpots ?: 0,
                priceMultiplier = floor.priceMultiplier ?: 1.0
            )
        },
        amenities = amenities.orEmpty().filterNotNull(),
        images = images.orEmpty().filterNotNull(),
        rating = rating ?: 0.0,
        reviewCount = reviewCount ?: 0,
        operatingHours = operatingHours?.let {
            OperatingHours(
                is24Hours = it.is24Hours ?: false,
                openTime = it.openTime ?: OperatingHours().openTime,
                closeTime = it.closeTime ?: OperatingHours().closeTime,
                closedDays = it.closedDays.orEmpty().filterNotNull()
            )
        } ?: OperatingHours(),
        isActive = isActive ?: true
    )
}

package com.example.parkover.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parkover.data.api.PresetVehicleApi
import com.example.parkover.data.api.VehiclesResponse
import com.example.parkover.data.model.ParkingAvailability
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.data.model.VehicleType
import com.example.parkover.data.repository.ApiResult
import com.example.parkover.data.repository.AvailabilityRepository
import com.example.parkover.data.repository.ParkingRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ParkingViewModel : ViewModel() {
    
    private val parkingRepository = ParkingRepository.getInstance()
    private val availabilityRepository = AvailabilityRepository.getInstance()
    
    // Raw parking spots from GitHub (static data)
    private var staticParkingSpots: List<ParkingSpot> = emptyList()
    
    // Parking Spots with real-time availability merged
    private val _parkingSpots = MutableLiveData<ApiResult<List<ParkingSpot>>>()
    val parkingSpots: LiveData<ApiResult<List<ParkingSpot>>> = _parkingSpots
    
    // Real-time availability map
    private val _availabilityMap = MutableLiveData<Map<String, ParkingAvailability>>()
    val availabilityMap: LiveData<Map<String, ParkingAvailability>> = _availabilityMap
    
    // Vehicles Data
    private val _vehiclesData = MutableLiveData<ApiResult<VehiclesResponse>>()
    val vehiclesData: LiveData<ApiResult<VehiclesResponse>> = _vehiclesData
    
    // Preset Vehicles
    private val _presetVehicles = MutableLiveData<ApiResult<List<PresetVehicleApi>>>()
    val presetVehicles: LiveData<ApiResult<List<PresetVehicleApi>>> = _presetVehicles
    
    // Selected parking spot for details
    private val _selectedParkingSpot = MutableLiveData<ParkingSpot?>()
    val selectedParkingSpot: LiveData<ParkingSpot?> = _selectedParkingSpot
    
    // Booking status
    private val _bookingStatus = MutableLiveData<BookingStatus>()
    val bookingStatus: LiveData<BookingStatus> = _bookingStatus
    
    init {
        loadParkingSpots()
        loadVehiclesData()
        observeAvailability()
    }
    
    fun loadParkingSpots() {
        _parkingSpots.value = ApiResult.Loading
        viewModelScope.launch {
            when (val result = parkingRepository.getParkingSpots()) {
                is ApiResult.Success -> {
                    staticParkingSpots = result.data
                    // Initialize availability in Firestore for new spots
                    availabilityRepository.checkAndInitializeAvailability(result.data)
                    // Merge with current availability
                    mergeWithAvailability()
                }
                is ApiResult.Error -> _parkingSpots.value = result
                is ApiResult.Loading -> {}
            }
        }
    }
    
    private fun observeAvailability() {
        viewModelScope.launch {
            availabilityRepository.getAllAvailabilityRealtime().collectLatest { availMap ->
                _availabilityMap.value = availMap
                mergeWithAvailability()
            }
        }
    }
    
    private fun mergeWithAvailability() {
        if (staticParkingSpots.isEmpty()) return
        
        val availMap = _availabilityMap.value ?: emptyMap()
        
        val mergedSpots = staticParkingSpots.map { spot ->
            val availability = availMap[spot.id]
            if (availability != null) {
                spot.copy(
                    availableSpotsTwoWheeler = availability.availableSpotsTwoWheeler,
                    availableSpotsFourWheeler = availability.availableSpotsFourWheeler,
                    availableSpotsHeavy = availability.availableSpotsHeavy,
                    floors = spot.floors.map { floor ->
                        val floorAvail = availability.floorAvailability[floor.floorNumber.toString()]
                        floor.copy(availableSpots = floorAvail ?: floor.totalSpots)
                    }
                )
            } else {
                spot
            }
        }
        
        _parkingSpots.value = ApiResult.Success(mergedSpots)
    }
    
    fun loadVehiclesData() {
        _vehiclesData.value = ApiResult.Loading
        viewModelScope.launch {
            _vehiclesData.value = parkingRepository.getVehiclesData()
        }
    }
    
    fun loadPresetVehicles() {
        _presetVehicles.value = ApiResult.Loading
        viewModelScope.launch {
            _presetVehicles.value = parkingRepository.getPresetVehicles()
        }
    }
    
    fun selectParkingSpot(spot: ParkingSpot) {
        _selectedParkingSpot.value = spot
    }
    
    fun clearSelectedSpot() {
        _selectedParkingSpot.value = null
    }
    
    // Called when a booking is confirmed
    fun onBookingConfirmed(spotId: String, vehicleType: VehicleType, floorNumber: Int? = null) {
        viewModelScope.launch {
            _bookingStatus.value = BookingStatus.Processing
            val result = availabilityRepository.decreaseAvailability(spotId, vehicleType, floorNumber)
            _bookingStatus.value = if (result.isSuccess) {
                BookingStatus.Success
            } else {
                BookingStatus.Error(result.exceptionOrNull()?.message ?: "Booking failed")
            }
        }
    }
    
    // Called when a booking ends or is cancelled
    fun onBookingEnded(spotId: String, vehicleType: VehicleType, floorNumber: Int? = null) {
        val spot = staticParkingSpots.find { it.id == spotId } ?: return
        viewModelScope.launch {
            availabilityRepository.increaseAvailability(
                spotId = spotId,
                vehicleType = vehicleType,
                floorNumber = floorNumber,
                maxTwoWheeler = spot.totalSpotsTwoWheeler,
                maxFourWheeler = spot.totalSpotsFourWheeler,
                maxHeavy = spot.totalSpotsHeavy
            )
        }
    }
    
    // Get parking spots near a location
    fun getParkingSpotsNearLocation(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 5.0
    ): List<ParkingSpot> {
        val spots = (_parkingSpots.value as? ApiResult.Success)?.data ?: return emptyList()
        return spots.filter { spot ->
            val distance = calculateDistance(latitude, longitude, spot.latitude, spot.longitude)
            distance <= radiusKm
        }.sortedBy { spot ->
            calculateDistance(latitude, longitude, spot.latitude, spot.longitude)
        }
    }
    
    // Haversine formula to calculate distance between two points
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

sealed class BookingStatus {
    object Idle : BookingStatus()
    object Processing : BookingStatus()
    object Success : BookingStatus()
    data class Error(val message: String) : BookingStatus()
}

package com.example.parkover.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parkover.data.api.PresetVehicleApi
import com.example.parkover.data.api.VehiclesResponse
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.data.repository.ApiResult
import com.example.parkover.data.repository.ParkingRepository
import kotlinx.coroutines.launch

class ParkingViewModel : ViewModel() {
    
    private val repository = ParkingRepository.getInstance()
    
    // Parking Spots
    private val _parkingSpots = MutableLiveData<ApiResult<List<ParkingSpot>>>()
    val parkingSpots: LiveData<ApiResult<List<ParkingSpot>>> = _parkingSpots
    
    // Vehicles Data
    private val _vehiclesData = MutableLiveData<ApiResult<VehiclesResponse>>()
    val vehiclesData: LiveData<ApiResult<VehiclesResponse>> = _vehiclesData
    
    // Preset Vehicles
    private val _presetVehicles = MutableLiveData<ApiResult<List<PresetVehicleApi>>>()
    val presetVehicles: LiveData<ApiResult<List<PresetVehicleApi>>> = _presetVehicles
    
    // Selected parking spot for details
    private val _selectedParkingSpot = MutableLiveData<ParkingSpot?>()
    val selectedParkingSpot: LiveData<ParkingSpot?> = _selectedParkingSpot
    
    init {
        loadParkingSpots()
        loadVehiclesData()
    }
    
    fun loadParkingSpots() {
        _parkingSpots.value = ApiResult.Loading
        viewModelScope.launch {
            _parkingSpots.value = repository.getParkingSpots()
        }
    }
    
    fun loadVehiclesData() {
        _vehiclesData.value = ApiResult.Loading
        viewModelScope.launch {
            _vehiclesData.value = repository.getVehiclesData()
        }
    }
    
    fun loadPresetVehicles() {
        _presetVehicles.value = ApiResult.Loading
        viewModelScope.launch {
            _presetVehicles.value = repository.getPresetVehicles()
        }
    }
    
    fun selectParkingSpot(spot: ParkingSpot) {
        _selectedParkingSpot.value = spot
    }
    
    fun clearSelectedSpot() {
        _selectedParkingSpot.value = null
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

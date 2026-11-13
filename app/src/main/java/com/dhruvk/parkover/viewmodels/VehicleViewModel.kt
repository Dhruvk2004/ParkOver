package com.dhruvk.parkover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhruvk.parkover.data.model.DefaultVehicles
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.data.repository.ParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Brand → models, per vehicle type, for the add-vehicle sheet. */
data class VehicleCatalog(val models: Map<VehicleType, Map<String, List<String>>>) {
    fun brands(type: VehicleType): List<String> = models[type]?.keys?.sorted().orEmpty()
    fun models(type: VehicleType, brand: String): List<String> = models[type]?.get(brand).orEmpty()
}

class VehicleViewModel : ViewModel() {

    private val bookingRepository = BookingRepository()
    private val parkingRepository = ParkingRepository.getInstance()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _catalog = MutableStateFlow(fallbackCatalog())
    val catalog: StateFlow<VehicleCatalog> = _catalog.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
        loadCatalog()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _vehicles.value = bookingRepository.getUserVehicles()
            _loading.value = false
        }
    }

    /** Brands/models from vehicles.json; falls back to the built-in presets offline. */
    private fun loadCatalog() {
        viewModelScope.launch {
            val result = parkingRepository.getVehiclesData()
            if (result is ApiResult.Success) {
                val presets = result.data.presetVehicles
                val built = VehicleType.entries.associateWith { type ->
                    presets.filter { it.type.equals(type.name, ignoreCase = true) }
                        .groupBy({ it.brand }, { it.model })
                        .mapValues { (_, models) -> models.distinct().sorted() }
                }
                if (built.values.any { it.isNotEmpty() }) {
                    // Merge so every type has at least the built-in options
                    val fallback = fallbackCatalog().models
                    _catalog.value = VehicleCatalog(
                        VehicleType.entries.associateWith { type ->
                            (built[type].orEmpty().ifEmpty { fallback[type].orEmpty() })
                        }
                    )
                }
            }
        }
    }

    fun addVehicle(
        type: VehicleType,
        brand: String,
        model: String,
        number: String,
        makeDefault: Boolean,
        onAdded: (Vehicle) -> Unit = {}
    ) {
        viewModelScope.launch {
            _saving.value = true
            val vehicle = Vehicle(
                type = type,
                brand = brand.trim(),
                model = model.trim(),
                number = number.replace(" ", "").uppercase(),
                isDefault = makeDefault || _vehicles.value.isEmpty()
            )
            bookingRepository.addVehicle(vehicle).fold(
                onSuccess = { saved ->
                    _vehicles.value = (listOf(saved) + _vehicles.value.map {
                        if (saved.isDefault) it.copy(isDefault = false) else it
                    })
                    _message.value = "${saved.displayName} added"
                    onAdded(saved)
                },
                onFailure = { _message.value = "Couldn't save the vehicle: ${it.message}" }
            )
            _saving.value = false
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            _vehicles.value = _vehicles.value.filterNot { it.id == vehicle.id }
            bookingRepository.deleteVehicle(vehicle.id).onFailure {
                _message.value = "Couldn't delete: ${it.message}"
                refresh()
            }
        }
    }

    fun restoreVehicle(vehicle: Vehicle) {
        addVehicle(vehicle.type, vehicle.brand, vehicle.model, vehicle.number, vehicle.isDefault)
    }

    fun setDefault(vehicle: Vehicle) {
        viewModelScope.launch {
            _vehicles.value = _vehicles.value.map { it.copy(isDefault = it.id == vehicle.id) }
                .sortedByDescending { it.isDefault }
            bookingRepository.setDefaultVehicle(vehicle.id).onFailure { refresh() }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun fallbackCatalog(): VehicleCatalog = VehicleCatalog(
        VehicleType.entries.associateWith { type ->
            val presets = DefaultVehicles.presets.filter { it.type == type }
            val base = presets.groupBy({ it.brand }, { it.model })
            if (type == VehicleType.HEAVY && base.isEmpty()) {
                mapOf("Tata" to listOf("Ultra", "Signa"), "Ashok Leyland" to listOf("Dost", "Boss"), "Eicher" to listOf("Pro 2049"))
            } else {
                base
            }
        }
    )
}

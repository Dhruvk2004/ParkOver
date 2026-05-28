package com.example.parkover.viewmodels

import androidx.lifecycle.ViewModel
import com.example.parkover.data.model.BookingData
import com.example.parkover.data.model.FloorData
import com.example.parkover.data.model.ParkingSlot
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.data.model.PaymentMethod
import com.example.parkover.data.model.Vehicle
import com.example.parkover.data.model.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared ViewModel for the booking flow.
 * Holds booking data as user progresses through:
 * ParkingDetails -> SelectVehicle -> SelectFloor -> ConfirmBooking -> ReviewSummary -> Payment -> Success
 */
class BookingFlowViewModel : ViewModel() {

    private val _bookingData = MutableStateFlow(BookingData())
    val bookingData: StateFlow<BookingData> = _bookingData.asStateFlow()

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    private val _selectedFloor = MutableStateFlow<FloorData?>(null)
    val selectedFloor: StateFlow<FloorData?> = _selectedFloor.asStateFlow()

    private val _selectedSpot = MutableStateFlow<ParkingSlot?>(null)
    val selectedSpot: StateFlow<ParkingSlot?> = _selectedSpot.asStateFlow()

    fun initBooking(spot: ParkingSpot) {
        _bookingData.value = BookingData(
            parkingId = spot.id,
            parkingName = spot.name,
            parkingAddress = spot.address,
            pricePerHour = spot.pricePerHourFourWheeler,
            latitude = spot.latitude,
            longitude = spot.longitude,
            parkingImage = spot.images.firstOrNull() ?: ""
        )
    }

    fun selectVehicle(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        _bookingData.value = _bookingData.value.copy(
            vehicleId = vehicle.id,
            vehicleName = "${vehicle.brand} ${vehicle.model}",
            vehicleNumber = vehicle.number,
            vehicleType = vehicle.type
        )
    }

    fun selectFloor(floor: FloorData) {
        _selectedFloor.value = floor
        _bookingData.value = _bookingData.value.copy(
            floorNumber = floor.floorNumber,
            floorName = floor.name
        )
    }

    fun selectSpot(spot: ParkingSlot) {
        _selectedSpot.value = spot
        _bookingData.value = _bookingData.value.copy(
            spotId = spot.id,
            spotNumber = spot.spotNumber
        )
    }

    fun setCheckInTime(timeMillis: Long) {
        _bookingData.value = _bookingData.value.copy(checkInTime = timeMillis)
        recalculatePricing()
    }

    fun setDuration(hours: Int) {
        val data = _bookingData.value
        val checkOutTime = data.checkInTime + (hours * 3600000L)
        _bookingData.value = data.copy(
            durationHours = hours,
            checkOutTime = checkOutTime
        )
        recalculatePricing()
    }

    fun setSpecifications(wheelchairAccess: Boolean, evCharging: Boolean) {
        _bookingData.value = _bookingData.value.copy(
            needsWheelchairAccess = wheelchairAccess,
            needsEvCharging = evCharging
        )
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _bookingData.value = _bookingData.value.copy(paymentMethod = method)
    }

    private fun recalculatePricing() {
        val data = _bookingData.value
        val basePrice = data.pricePerHour * data.durationHours
        val taxAmount = basePrice * 0.10
        val totalPrice = basePrice + taxAmount
        _bookingData.value = data.copy(
            basePrice = basePrice,
            taxAmount = taxAmount,
            totalPrice = totalPrice
        )
    }

    fun reset() {
        _bookingData.value = BookingData()
        _selectedVehicle.value = null
        _selectedFloor.value = null
        _selectedSpot.value = null
    }
}

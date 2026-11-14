package com.dhruvk.parkover.viewmodels

import com.dhruvk.parkover.di.AppServices
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingData
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.FloorData
import com.dhruvk.parkover.data.model.ParkingSlot
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.PaymentMethod
import com.dhruvk.parkover.data.model.PaymentStatus
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.utils.Constants
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

sealed class SubmitState {
    object Idle : SubmitState()
    object Submitting : SubmitState()
    data class Error(val message: String) : SubmitState()
    data class Done(val bookingId: String) : SubmitState()
}

/**
 * Shared state for the booking flow, scoped to the "booking_flow" nav graph:
 * ParkingDetails → SelectVehicle → SelectFloor → ConfirmBooking → ReviewSummary → Payment
 */
class BookingFlowViewModel : ViewModel() {

    private val repository = BookingRepository()

    private val _spot = MutableStateFlow<ParkingSpot?>(null)
    val spot: StateFlow<ParkingSpot?> = _spot.asStateFlow()

    private val _bookingData = MutableStateFlow(BookingData())
    val bookingData: StateFlow<BookingData> = _bookingData.asStateFlow()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _vehiclesLoading = MutableStateFlow(true)
    val vehiclesLoading: StateFlow<Boolean> = _vehiclesLoading.asStateFlow()

    private val _floors = MutableStateFlow<List<FloorData>>(emptyList())
    val floors: StateFlow<List<FloorData>> = _floors.asStateFlow()

    private val _floorsLoading = MutableStateFlow(false)
    val floorsLoading: StateFlow<Boolean> = _floorsLoading.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    /** Called from Parking details when the user taps Book. Keeps selections if it's the same parking. */
    fun initBooking(spot: ParkingSpot) {
        _spot.value = spot
        if (_bookingData.value.parkingId == spot.id) return
        val checkIn = nextHalfHour()
        _bookingData.value = BookingData(
            parkingId = spot.id,
            parkingName = spot.name,
            parkingAddress = spot.address,
            parkingImage = spot.images.firstOrNull() ?: "",
            latitude = spot.latitude,
            longitude = spot.longitude,
            checkInTime = checkIn,
            checkOutTime = checkIn + 2 * HOUR,
            durationHours = 2
        )
        _floors.value = emptyList()
        _submitState.value = SubmitState.Idle
        recalculate()
    }

    /** Keeps the live availability fresh while the flow is open. */
    fun updateSpot(spot: ParkingSpot) {
        if (spot.id == _spot.value?.id) _spot.value = spot
    }

    // ---------- Vehicle ----------

    fun loadVehicles() {
        viewModelScope.launch {
            _vehiclesLoading.value = true
            val list = repository.getUserVehicles()
            _vehicles.value = list
            _vehiclesLoading.value = false
            if (_bookingData.value.vehicleId.isEmpty()) {
                list.firstOrNull { it.isDefault }?.let(::selectVehicle)
            }
        }
    }

    fun onVehicleAdded(vehicle: Vehicle) {
        _vehicles.update { listOf(vehicle) + it.filterNot { v -> v.id == vehicle.id } }
        selectVehicle(vehicle)
    }

    fun selectVehicle(vehicle: Vehicle) {
        val typeChanged = vehicle.type != _bookingData.value.vehicleType
        _bookingData.update {
            it.copy(
                vehicleId = vehicle.id,
                vehicleName = vehicle.displayName,
                vehicleNumber = vehicle.number,
                vehicleType = vehicle.type
            )
        }
        if (typeChanged) clearSpot()
        recalculate()
    }

    // ---------- Floor and spot ----------

    fun loadFloors() {
        val spot = _spot.value ?: return
        viewModelScope.launch {
            _floorsLoading.value = true
            _floors.value = repository.getFloorsWithSpots(spot, _bookingData.value.vehicleType)
            _floorsLoading.value = false
        }
    }

    fun selectSpot(floor: FloorData, slot: ParkingSlot) {
        val multiplier = _spot.value?.floors?.firstOrNull { it.floorNumber == floor.floorNumber }?.priceMultiplier ?: 1.0
        _bookingData.update {
            it.copy(
                floorNumber = floor.floorNumber,
                floorName = floor.name,
                floorMultiplier = multiplier,
                spotId = slot.id,
                spotNumber = slot.spotNumber,
                needsEvCharging = it.needsEvCharging || slot.hasEvCharging
            )
        }
        recalculate()
    }

    private fun clearSpot() {
        _bookingData.update { it.copy(spotId = "", spotNumber = "", floorName = "", floorMultiplier = 1.0) }
        _floors.value = emptyList()
    }

    // ---------- Time ----------

    fun setCheckInTime(timeMillis: Long) {
        _bookingData.update { it.copy(checkInTime = timeMillis, checkOutTime = timeMillis + it.durationHours * HOUR) }
        recalculate()
    }

    fun setDuration(hours: Int) {
        val clamped = hours.coerceIn(Constants.MIN_BOOKING_HOURS, Constants.MAX_BOOKING_HOURS)
        _bookingData.update { it.copy(durationHours = clamped, checkOutTime = it.checkInTime + clamped * HOUR) }
        recalculate()
    }

    fun setSpecifications(wheelchairAccess: Boolean, evCharging: Boolean) {
        _bookingData.update { it.copy(needsWheelchairAccess = wheelchairAccess, needsEvCharging = evCharging) }
    }

    // ---------- Payment ----------

    fun setPaymentMethod(method: PaymentMethod, upiId: String = "") {
        _bookingData.update { it.copy(paymentMethod = method, upiId = upiId) }
    }

    fun confirmBooking() {
        val data = _bookingData.value
        if (_submitState.value is SubmitState.Submitting) return
        if (data.vehicleId.isEmpty() || data.spotNumber.isEmpty()) {
            _submitState.value = SubmitState.Error("Pick a vehicle and a spot first.")
            return
        }
        // Check-in in the past (screen left open): start now instead
        val now = AppServices.clock.nowMillis()
        if (data.checkInTime < now) {
            setCheckInTime(now)
        }
        val current = _bookingData.value
        _submitState.value = SubmitState.Submitting
        viewModelScope.launch {
            val booking = Booking(
                parkingId = current.parkingId,
                parkingName = current.parkingName,
                parkingAddress = current.parkingAddress,
                vehicleId = current.vehicleId,
                vehicleNumber = current.vehicleNumber,
                vehicleType = current.vehicleType,
                floorNumber = current.floorNumber,
                floorName = current.floorName,
                spotNumber = current.spotNumber,
                entryTime = Timestamp(Date(current.checkInTime)),
                exitTime = Timestamp(Date(current.checkOutTime)),
                durationHours = current.durationHours,
                basePrice = current.basePrice,
                taxAmount = current.taxAmount,
                totalPrice = current.totalPrice,
                paymentMethod = current.paymentMethod,
                // No payment gateway yet: every booking is confirmed and paid at the parking
                paymentStatus = PaymentStatus.PENDING,
                bookingStatus = BookingStatus.CONFIRMED
            )
            _submitState.value = repository.createBooking(booking).fold(
                onSuccess = { SubmitState.Done(it.id) },
                onFailure = { SubmitState.Error(it.message ?: "Couldn't create the booking. Try again.") }
            )
        }
    }

    fun clearSubmitError() {
        if (_submitState.value is SubmitState.Error) _submitState.value = SubmitState.Idle
    }

    // ---------- Pricing ----------

    private fun recalculate() {
        val spot = _spot.value
        _bookingData.update { data ->
            val typeRate = when (data.vehicleType) {
                VehicleType.TWO_WHEELER -> spot?.pricePerHourTwoWheeler
                VehicleType.FOUR_WHEELER -> spot?.pricePerHourFourWheeler
                VehicleType.HEAVY -> spot?.pricePerHourHeavy
            } ?: 0.0
            val rate = typeRate * data.floorMultiplier
            val base = rate * data.durationHours
            val tax = base * Constants.BOOKING_TAX_RATE
            data.copy(pricePerHour = rate, basePrice = base, taxAmount = tax, totalPrice = base + tax)
        }
    }

    companion object {
        const val HOUR = 3_600_000L

        fun nextHalfHour(from: Long = AppServices.clock.nowMillis()): Long =
            Calendar.getInstance().apply {
                timeInMillis = from
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val minute = get(Calendar.MINUTE)
                if (minute < 30) set(Calendar.MINUTE, 30) else {
                    set(Calendar.MINUTE, 0)
                    add(Calendar.HOUR_OF_DAY, 1)
                }
            }.timeInMillis
    }
}

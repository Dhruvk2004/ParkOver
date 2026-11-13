package com.dhruvk.parkover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.repository.AccountRepository
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.data.repository.SavedParking
import com.dhruvk.parkover.data.repository.UpiId
import com.dhruvk.parkover.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Signed-in user's data that several tabs share: saved parkings, bookings, UPI IDs, support.
 * Scoped to the main nav graph so the Firestore listeners survive tab switches.
 */
class AccountViewModel : ViewModel() {

    private val accountRepository = AccountRepository()
    private val bookingRepository = BookingRepository()

    val savedParkings: StateFlow<List<SavedParking>?> = accountRepository.observeSavedParkings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val savedIds: StateFlow<Set<String>> = accountRepository.observeSavedParkings()
        .map { list -> list.map { it.parkingId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** null while the first snapshot is loading. */
    val bookings: StateFlow<List<Booking>?> = bookingRepository.observeUserBookings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val upiIds: StateFlow<List<UpiId>> = accountRepository.observeUpiIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    // ---------- Saved ----------

    fun toggleSaved(spot: ParkingSpot, currentlySaved: Boolean) {
        viewModelScope.launch {
            val result = if (currentlySaved) accountRepository.removeSavedParking(spot.id) else accountRepository.saveParking(spot)
            result.onFailure { _message.value = "Couldn't update saved: ${it.message}" }
        }
    }

    fun removeSaved(parkingId: String) {
        viewModelScope.launch {
            accountRepository.removeSavedParking(parkingId).onFailure {
                _message.value = "Couldn't remove: ${it.message}"
            }
        }
    }

    fun restoreSaved(spot: ParkingSpot) {
        viewModelScope.launch { accountRepository.saveParking(spot) }
    }

    // ---------- Bookings ----------

    fun cancelBooking(booking: Booking) {
        viewModelScope.launch {
            _busy.value = true
            bookingRepository.cancelBooking(booking).fold(
                onSuccess = { _message.value = "Booking cancelled. The spot is free again." },
                onFailure = { _message.value = "Couldn't cancel: ${it.message}" }
            )
            _busy.value = false
        }
    }

    fun extendBooking(booking: Booking, hours: Int = 1) {
        viewModelScope.launch {
            _busy.value = true
            val rate = if (booking.durationHours > 0) booking.basePrice / booking.durationHours else 0.0
            bookingRepository.extendBooking(booking, hours, rate, Constants.BOOKING_TAX_RATE).fold(
                onSuccess = { _message.value = "Extended by $hours h" },
                onFailure = { _message.value = "Couldn't extend: ${it.message}" }
            )
            _busy.value = false
        }
    }

    // ---------- UPI ----------

    fun addUpiId(vpa: String, makeDefault: Boolean) {
        viewModelScope.launch {
            accountRepository.addUpiId(vpa.trim(), makeDefault || upiIds.value.isEmpty()).fold(
                onSuccess = { _message.value = "UPI ID added" },
                onFailure = { _message.value = "Couldn't add UPI ID: ${it.message}" }
            )
        }
    }

    fun removeUpiId(id: String) {
        viewModelScope.launch { accountRepository.removeUpiId(id) }
    }

    // ---------- Support ----------

    fun submitTicket(category: String, subject: String, description: String, bookingId: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            accountRepository.submitSupportTicket(category, subject, description, bookingId).fold(
                onSuccess = { onDone() },
                onFailure = { _message.value = "Couldn't send: ${it.message}" }
            )
            _busy.value = false
        }
    }

    companion object {
        fun isUpcoming(booking: Booking): Boolean =
            booking.bookingStatus in setOf(BookingStatus.CONFIRMED, BookingStatus.ACTIVE, BookingStatus.PENDING) &&
                booking.exitTime.toDate().time > System.currentTimeMillis()

        fun isActiveNow(booking: Booking): Boolean {
            val now = System.currentTimeMillis()
            return isUpcoming(booking) && booking.entryTime.toDate().time <= now
        }

        val UPI_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")
    }
}

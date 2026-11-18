package com.dhruvk.parkover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.Floor
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.repository.BayLock
import com.dhruvk.parkover.data.repository.BlockReason
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.data.repository.OperatorProfile
import com.dhruvk.parkover.data.repository.OperatorRepository
import com.dhruvk.parkover.di.AppServices
import com.dhruvk.parkover.domain.BayState
import com.dhruvk.parkover.domain.OperatorRules
import com.dhruvk.parkover.domain.TodayTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One bay on the operator's floor grid, as it is right now. */
data class BayView(
    val spotNumber: String,
    val shortLabel: String,
    val state: BayState,
    /** The booking or block holding it now (parked, booked or blocked). */
    val booking: Booking?,
    /** Start of the next booking on this bay after now, if any. */
    val nextBookingAt: Long?
)

data class FloorView(val floor: Floor, val label: String, val bays: List<BayView>)

/** What the operator's role check found. */
sealed interface RoleState {
    data object Checking : RoleState
    data object Driver : RoleState
    data class Operator(val profile: OperatorProfile) : RoleState
}

/**
 * The operator side: who the operator is, their parking place's bookings and bays, live, and
 * the check-in / check-out / block actions. Activity scoped so the listeners survive tab switches.
 */
class OperatorViewModel(
    private val repository: OperatorRepository = OperatorRepository()
) : ViewModel() {

    private val _role = MutableStateFlow<RoleState>(RoleState.Checking)
    val role: StateFlow<RoleState> = _role.asStateFlow()

    private val _spot = MutableStateFlow<ParkingSpot?>(null)
    val spot: StateFlow<ParkingSpot?> = _spot.asStateFlow()

    /** null while the first snapshot is loading. */
    private val _bookings = MutableStateFlow<List<Booking>?>(null)
    val bookings: StateFlow<List<Booking>?> = _bookings.asStateFlow()

    private val _locks = MutableStateFlow<List<BayLock>>(emptyList())
    val locks: StateFlow<List<BayLock>> = _locks.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** Ticks every 30 s so "in 12 min", "late" and "time left" stay true without a reload. */
    private val _now = MutableStateFlow(AppServices.clock.nowMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var listeners: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                _now.value = AppServices.clock.nowMillis()
            }
        }
    }

    /** Decides the role for the signed-in account (call after every sign-in). */
    suspend fun checkRole(): RoleState {
        _role.value = RoleState.Checking
        val profile = repository.loadProfile()
        val state = if (profile == null) RoleState.Driver else RoleState.Operator(profile)
        _role.value = state
        if (profile != null) start(profile) else stop()
        return state
    }

    fun profile(): OperatorProfile? = (role.value as? RoleState.Operator)?.profile

    private fun start(profile: OperatorProfile) {
        listeners?.cancel()
        _bookings.value = null
        listeners = viewModelScope.launch {
            launch {
                repository.observeBookings(profile.parkingId).collect { result ->
                    result.fold(
                        onSuccess = { _bookings.value = it; _loadError.value = null },
                        onFailure = { _loadError.value = "Couldn't load today's bookings. Check your connection." }
                    )
                }
            }
            launch {
                val fromBucket = BookingRepository.bucketOf(AppServices.clock.nowMillis())
                repository.observeLocks(profile.parkingId, fromBucket).collect { _locks.value = it }
            }
            launch { loadSpot(profile.parkingId) }
        }
    }

    private suspend fun loadSpot(parkingId: String) {
        when (val result = AppServices.parkingSource.getParkingSpots()) {
            is ApiResult.Success -> _spot.value = result.data.firstOrNull { it.id == parkingId }
            else -> Unit
        }
    }

    /** Signing out or switching to the driver side. */
    fun stop() {
        listeners?.cancel()
        listeners = null
        _bookings.value = null
        _locks.value = emptyList()
    }

    fun signedOut() {
        stop()
        _role.value = RoleState.Checking
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---------- Today ----------

    fun today(bookings: List<Booking>, now: Long): Map<TodayTab, List<Booking>> = OperatorRules.groupForToday(bookings, now)

    fun booking(id: String): Booking? = _bookings.value?.firstOrNull { it.id == id }

    /** A scanned booking: from the live list, else read once (it may be for another day). */
    suspend fun findBooking(id: String): Booking? = booking(id) ?: repository.getBooking(id)

    /** Waiting bookings whose plate matches what the operator typed. */
    fun searchByPlate(query: String): List<Booking> {
        val now = _now.value
        return _bookings.value.orEmpty()
            .filter { it.bookingStatus in WAITING && !it.walkIn && OperatorRules.tabOf(it, now) != null }
            .filter { OperatorRules.plateMatches(it.vehicleNumber, query) }
            .sortedBy { it.entryTime.toDate() }
    }

    // ---------- Actions ----------

    fun checkIn(booking: Booking, onDone: () -> Unit = {}) = act(onDone) {
        repository.checkIn(booking.id).map { "Checked in. Send them to bay ${booking.spotNumber}." }
    }

    fun checkOut(booking: Booking, onDone: () -> Unit = {}) = act(onDone) {
        repository.checkOut(booking.id).map { extra ->
            when {
                booking.walkIn -> "Bay ${booking.spotNumber} is free again."
                extra > 0 -> "Checked out. Collect ${rupees(extra)} extra at the gate."
                else -> "Checked out. No extra charge."
            }
        }
    }

    fun blockBay(floor: Floor, floorLabel: String, spotNumber: String, untilMs: Long, reason: BlockReason, vehicleNumber: String, onDone: () -> Unit = {}) {
        val profile = profile() ?: return
        act(onDone) {
            repository.blockBay(profile, floor.floorNumber, floor.name.ifBlank { floorLabel }, spotNumber, untilMs, reason, vehicleNumber)
                .map { "Bay $spotNumber is blocked." }
        }
    }

    private fun act(onDone: () -> Unit, block: suspend () -> Result<String>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block().fold(
                onSuccess = { _message.value = it; onDone() },
                onFailure = { _message.value = it.message ?: "That didn't work. Try again." }
            )
            _busy.value = false
        }
    }

    // ---------- Bays ----------

    /** The floor grid right now: who is in each bay, and when each bay is next booked. */
    fun floors(spot: ParkingSpot?, bookings: List<Booking>, locks: List<BayLock>, now: Long): List<FloorView> {
        spot ?: return emptyList()
        val nowBucket = BookingRepository.bucketOf(now)
        val byId = bookings.associateBy { it.id }
        val parked = bookings.filter { it.bookingStatus == BookingStatus.ACTIVE }.associateBy { it.spotNumber }
        val heldNow = locks.filter { it.bucket == nowBucket }.associateBy { it.spotNumber }
        val nextBucket = locks.filter { it.bucket > nowBucket }
            .groupBy { it.spotNumber }
            .mapValues { (_, l) -> l.filter { lock -> lock.bookingId != heldNow[lock.spotNumber]?.bookingId && lock.bookingId != parked[lock.spotNumber]?.id }.minOfOrNull { it.bucket } }

        return spot.floors.sortedBy { it.floorNumber }.map { floor ->
            val label = BookingRepository.floorLabel(floor.floorNumber)
            val bays = (1..floor.totalSpots).map { i ->
                val number = "$label-${String.format("%02d", i)}"
                val inBay = parked[number]
                val held = heldNow[number]
                val state = when {
                    inBay != null && inBay.walkIn -> BayState.Blocked
                    inBay != null -> BayState.Parked
                    held != null -> BayState.Booked
                    else -> BayState.Free
                }
                BayView(
                    spotNumber = number,
                    shortLabel = String.format("%02d", i),
                    state = state,
                    booking = inBay ?: held?.let { byId[it.bookingId] },
                    nextBookingAt = nextBucket[number]?.let { it * BookingRepository.LOCK_BUCKET_MS }
                )
            }
            FloorView(floor, floor.name.ifBlank { label }, bays)
        }
    }

    companion object {
        private const val TICK_MS = 30_000L
        private val WAITING = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)
        fun rupees(amount: Double) = com.dhruvk.parkover.ui.components.formatRupees(amount)
    }
}

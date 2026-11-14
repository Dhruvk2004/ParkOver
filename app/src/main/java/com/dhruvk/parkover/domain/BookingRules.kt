package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.BookingRepository.SlotTakenException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Calendar
import kotlin.math.roundToLong

/** A price for a stay: everything in rupees, rounded to paise. */
data class Quote(val ratePerHour: Double, val base: Double, val tax: Double, val total: Double)

/** Pure booking logic: pricing, time rules, upcoming/past and error messages. No Android, no Firebase calls. */
object BookingRules {

    const val HOUR_MS = 3_600_000L
    private val ACTIVE = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE)

    /** Hourly rate for a vehicle type on a floor (premium floors multiply the base rate). */
    fun ratePerHour(spot: ParkingSpot?, type: VehicleType, floorMultiplier: Double = 1.0): Double {
        val base = when (type) {
            VehicleType.TWO_WHEELER -> spot?.pricePerHourTwoWheeler
            VehicleType.FOUR_WHEELER -> spot?.pricePerHourFourWheeler
            VehicleType.HEAVY -> spot?.pricePerHourHeavy
        } ?: 0.0
        return base * floorMultiplier
    }

    fun quote(ratePerHour: Double, hours: Int, taxRate: Double): Quote {
        val base = paise(ratePerHour * hours)
        val tax = paise(base * taxRate)
        return Quote(ratePerHour, base, tax, paise(base + tax))
    }

    private fun paise(rupees: Double) = (rupees * 100).roundToLong() / 100.0

    /** Why a stay can't be booked at [now], or null when it can. */
    fun timeProblem(entryMs: Long, exitMs: Long, now: Long): String? = when {
        exitMs <= entryMs -> "Check-out must be after check-in."
        exitMs <= now -> "That time has already passed. Pick a later check-in."
        else -> null
    }

    /** The next :00 or :30 strictly after [from], in the phone's time zone. */
    fun nextHalfHour(from: Long): Long = Calendar.getInstance().apply {
        timeInMillis = from
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (get(Calendar.MINUTE) < 30) set(Calendar.MINUTE, 30)
        else {
            set(Calendar.MINUTE, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }
    }.timeInMillis

    /** Still to come or in progress: active status and not yet ended. */
    fun isUpcoming(booking: Booking, now: Long): Boolean =
        booking.bookingStatus in ACTIVE && booking.exitTime.toDate().time > now

    /** Parked right now. */
    fun isActiveNow(booking: Booking, now: Long): Boolean =
        isUpcoming(booking, now) && booking.entryTime.toDate().time <= now

    /** Splits bookings for the two tabs: upcoming soonest first, past newest first. */
    fun split(bookings: List<Booking>, now: Long): Pair<List<Booking>, List<Booking>> {
        val (upcoming, past) = bookings.partition { isUpcoming(it, now) }
        return upcoming.sortedBy { it.entryTime.toDate() } to past.sortedByDescending { it.entryTime.toDate() }
    }

    /** Turns a failed booking write into a message a driver can act on. Never implies success. */
    fun errorFor(e: Throwable): Exception {
        val chain = generateSequence(e) { it.cause }.toList()
        chain.firstOrNull { it is SlotTakenException }?.let { return it as Exception }
        return when (chain.filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                Exception("You're offline, so nothing was booked. Check your connection and try again.")
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                Exception("This booking was refused. Refresh and try again.")
            FirebaseFirestoreException.Code.ABORTED ->
                Exception("Someone booked at the same moment. Pick the spot again.")
            else -> Exception(
                chain.firstOrNull { it !is FirebaseFirestoreException && !it.message.isNullOrBlank() }?.message
                    ?: "Couldn't complete the booking. Try again."
            )
        }
    }
}

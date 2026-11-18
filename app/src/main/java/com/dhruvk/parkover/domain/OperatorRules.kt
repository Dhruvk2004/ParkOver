package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import java.util.Calendar
import kotlin.math.ceil

/** Which list a booking sits in on the operator's Today screen. */
enum class TodayTab(val label: String) {
    Arriving("Arriving"),
    Late("Late"),
    Parked("Parked"),
    Done("Done")
}

/** What a bay looks like on the operator's floor grid right now. */
enum class BayState { Free, Booked, Parked, Blocked }

/** Pure operator logic: Today tabs, overstay charges, QR codes. No Android, no Firebase calls. */
object OperatorRules {

    /** A driver counts as late this long after their start time. */
    const val LATE_AFTER_MS = 15 * 60_000L
    /** Leaving within this long after the end time is free. */
    const val OVERSTAY_GRACE_MS = 10 * 60_000L
    /** Overstay is charged per started half hour. */
    const val OVERSTAY_STEP_MS = 30 * 60_000L

    private const val QR_PREFIX = "PARKOVER:"
    private val WAITING = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)

    /**
     * Which tab a booking belongs in at [now], or null when it isn't part of today
     * (another day, and not parked right now).
     */
    fun tabOf(booking: Booking, now: Long): TodayTab? {
        val entry = booking.entryTime.toDate().time
        return when (booking.bookingStatus) {
            BookingStatus.ACTIVE -> TodayTab.Parked // Even if it started yesterday: it's in a bay now
            in WAITING -> when {
                !sameDay(entry, now) && entry > now -> null // A later day
                now >= entry + LATE_AFTER_MS -> TodayTab.Late
                else -> TodayTab.Arriving
            }
            else -> {
                val closedAt = (booking.actualExitTime ?: booking.updatedAt).toDate().time
                if (sameDay(closedAt, now) || sameDay(entry, now)) TodayTab.Done else null
            }
        }
    }

    /** Today's bookings per tab, each sorted the way the operator works through it. */
    fun groupForToday(bookings: List<Booking>, now: Long): Map<TodayTab, List<Booking>> {
        val grouped = bookings.groupBy { tabOf(it, now) }
        return TodayTab.entries.associateWith { tab ->
            val list = grouped[tab].orEmpty()
            when (tab) {
                TodayTab.Done -> list.sortedByDescending { (it.actualExitTime ?: it.updatedAt).toDate() }
                TodayTab.Parked -> list.sortedBy { it.exitTime.toDate() } // Due out first
                else -> list.sortedBy { it.entryTime.toDate() }
            }
        }
    }

    /** Booking id inside a ParkOver QR code, or null for any other QR. */
    fun bookingIdFromQr(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith(QR_PREFIX)) return null
        return text.removePrefix(QR_PREFIX).trim().takeIf { it.matches(Regex("[A-Za-z0-9-]{4,40}")) }
    }

    /** Hourly rate (before tax) the driver paid for this booking. */
    fun ratePerHour(booking: Booking): Double =
        if (booking.durationHours > 0) booking.basePrice / booking.durationHours else 0.0

    /** Tax rate the booking was priced with (e.g. 0.18). */
    fun taxRate(booking: Booking): Double =
        if (booking.basePrice > 0) booking.taxAmount / booking.basePrice else 0.0

    /**
     * Extra to collect when a car leaves at [exitAt]: nothing within the grace period, then the
     * booking's own rate per started half hour, plus the same tax. Rounded to paise.
     */
    fun overstayCharge(booking: Booking, exitAt: Long): Double {
        val over = exitAt - booking.exitTime.toDate().time
        if (over <= OVERSTAY_GRACE_MS) return 0.0
        val steps = ceil(over.toDouble() / OVERSTAY_STEP_MS)
        val base = ratePerHour(booking) / 2 * steps
        return BookingRules.quote(base, 1, taxRate(booking)).total
    }

    /** Price of one overstay step (half an hour) with tax, for the "if they stay past" note. */
    fun overstayStepPrice(booking: Booking): Double =
        BookingRules.quote(ratePerHour(booking) / 2, 1, taxRate(booking)).total

    /** Number plate matching that ignores spaces, dots and dashes ("MH01 AB-1234" = "mh01ab1234"). */
    fun plateMatches(plate: String, query: String): Boolean {
        val q = normalize(query)
        return q.length >= 2 && normalize(plate).contains(q)
    }

    private fun normalize(s: String) = s.replace(Regex("[\\s.-]"), "").uppercase()

    /** "1h 42m", "42 min", "under a minute". */
    fun formatSpan(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "under a minute"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    /** End of the calendar day of [now], in the phone's time zone. */
    fun endOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}

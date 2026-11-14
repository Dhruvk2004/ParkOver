package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.BookingRepository.SlotTakenException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** BOOK-03/04/05, BKL-01, NET-07: pricing, time rules, tabs and error messages. */
class BookingRulesTest {

    private val H = BookingRules.HOUR_MS
    private val spot = ParkingSpot(
        id = "p1", pricePerHourTwoWheeler = 10.0, pricePerHourFourWheeler = 20.0, pricePerHourHeavy = 40.0
    )

    // ---- BOOK-03 price maths ----

    @Test fun BOOK_03_ratePerVehicleTypeAndFloorMultiplier() {
        assertEquals(10.0, BookingRules.ratePerHour(spot, VehicleType.TWO_WHEELER), 0.0)
        assertEquals(20.0, BookingRules.ratePerHour(spot, VehicleType.FOUR_WHEELER), 0.0)
        assertEquals(40.0, BookingRules.ratePerHour(spot, VehicleType.HEAVY), 0.0)
        assertEquals(30.0, BookingRules.ratePerHour(spot, VehicleType.FOUR_WHEELER, floorMultiplier = 1.5), 0.0)
        assertEquals(0.0, BookingRules.ratePerHour(null, VehicleType.FOUR_WHEELER), 0.0)
    }

    @Test fun BOOK_03_quoteMatchesTheReviewScreen() {
        // Govandi, car, 2 h: ₹40 + ₹4 tax = ₹44 (as seen on the phone)
        val q = BookingRules.quote(20.0, 2, 0.10)
        assertEquals(40.0, q.base, 0.0)
        assertEquals(4.0, q.tax, 0.0)
        assertEquals(44.0, q.total, 0.0)
    }

    @Test fun BOOK_03_totalsAreRoundedToPaiseAndAddUp() {
        // 1.1 × 3 is 3.3000000000000003 in floating point; prices must not show that.
        val q = BookingRules.quote(1.1, 3, 0.18)
        assertEquals(3.3, q.base, 0.0)
        assertEquals(0.59, q.tax, 0.0)
        assertEquals(q.base + q.tax, q.total, 1e-9)
        for (rate in listOf(12.5, 33.33, 19.99)) for (hours in 1..24) {
            val x = BookingRules.quote(rate, hours, 0.10)
            assertEquals("rate $rate × $hours h", x.total, x.base + x.tax, 1e-9)
            assertEquals(x.total, Math.round(x.total * 100) / 100.0, 0.0)
        }
    }

    @Test fun BOOK_05_fullDayAndOvernightStaysAreChargedPerHour() {
        assertEquals(480.0, BookingRules.quote(20.0, 24, 0.10).base, 0.0)
        assertEquals(528.0, BookingRules.quote(20.0, 24, 0.10).total, 0.0)
    }

    // ---- BOOK-04 / BOOK-05 time rules ----

    @Test fun BOOK_04_pastOrEmptyStaysAreRefused() {
        val now = 1_800_000_000_000L
        assertNotNull(BookingRules.timeProblem(now - 3 * H, now - H, now))       // ended already
        assertNotNull(BookingRules.timeProblem(now, now, now))                   // zero length
        assertNotNull(BookingRules.timeProblem(now + 2 * H, now + H, now))       // backwards
        assertNull(BookingRules.timeProblem(now - H, now + H, now))              // started, still running
        assertNull(BookingRules.timeProblem(now + H, now + 3 * H, now))          // future
    }

    @Test fun BOOK_05_nextHalfHourRollsOverHourAndMidnight() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(ist)
        try {
            fun at(h: Int, m: Int, s: Int = 0, day: Int = 14) = Calendar.getInstance(ist).apply {
                clear(); set(2026, Calendar.NOVEMBER, day, h, m, s)
            }.timeInMillis
            assertEquals(at(10, 30), BookingRules.nextHalfHour(at(10, 5)))
            assertEquals(at(11, 0), BookingRules.nextHalfHour(at(10, 30)))       // exactly :30 → next slot
            assertEquals(at(11, 0), BookingRules.nextHalfHour(at(10, 59, 59)))
            assertEquals(at(0, 0, day = 15), BookingRules.nextHalfHour(at(23, 45))) // across midnight
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    // ---- BKL-01 upcoming vs past ----

    private fun booking(id: String, startH: Long, endH: Long, status: BookingStatus, now: Long) = Booking(
        id = id, bookingStatus = status,
        entryTime = Timestamp(Date(now + startH * H)), exitTime = Timestamp(Date(now + endH * H))
    )

    @Test fun BKL_01_tabsSplitByStatusAndEndTime() {
        val now = 1_800_000_000_000L
        val list = listOf(
            booking("future", 2, 4, BookingStatus.CONFIRMED, now),
            booking("parkedNow", -1, 1, BookingStatus.ACTIVE, now),
            booking("soonest", 1, 2, BookingStatus.PENDING, now),
            booking("ended", -5, -3, BookingStatus.CONFIRMED, now),   // never marked complete, but over
            booking("cancelled", 3, 5, BookingStatus.CANCELLED, now),
            booking("done", -10, -8, BookingStatus.COMPLETED, now)
        )
        val (upcoming, past) = BookingRules.split(list, now)
        assertEquals(listOf("parkedNow", "soonest", "future"), upcoming.map { it.id })
        assertEquals(listOf("cancelled", "ended", "done"), past.map { it.id })
        assertTrue(BookingRules.isActiveNow(list[1], now))
        assertFalse(BookingRules.isActiveNow(list[0], now))
    }

    @Test fun BKL_01_bookingMovesToPastTheMomentItEnds() {
        val now = 1_800_000_000_000L
        val b = booking("b", -2, 0, BookingStatus.CONFIRMED, now)
        assertFalse(BookingRules.isUpcoming(b, now))
        assertTrue(BookingRules.isUpcoming(b, now - 1))
    }

    // ---- NET-07 errors never look like success ----

    private fun fs(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException("x", code)

    @Test fun NET_07_offlineSaysNothingWasBooked() {
        for (code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)) {
            assertTrue(BookingRules.errorFor(fs(code)).message!!.contains("nothing was booked"))
        }
    }

    @Test fun NET_07_collisionsAndRefusalsHaveClearMessages() {
        assertTrue(BookingRules.errorFor(fs(FirebaseFirestoreException.Code.ABORTED)).message!!.contains("same moment"))
        assertTrue(BookingRules.errorFor(fs(FirebaseFirestoreException.Code.PERMISSION_DENIED)).message!!.contains("refused"))
        val taken = SlotTakenException("G-01")
        assertEquals(taken, BookingRules.errorFor(RuntimeException("wrapped", taken)))
    }

    @Test fun NET_07_ownMessagesPassThroughAndUnknownsGetAFallback() {
        assertEquals("This parking is full for your vehicle type.",
            BookingRules.errorFor(Exception("This parking is full for your vehicle type.")).message)
        assertEquals("Couldn't complete the booking. Try again.", BookingRules.errorFor(Exception()).message)
    }
}

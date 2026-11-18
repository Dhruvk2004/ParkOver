package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** OP: the operator's Today tabs, overstay charges, QR codes and plate search. */
class OperatorRulesTest {

    private val min = 60_000L
    private val hour = 60 * min

    /** Today at 6:00 PM, phone time. */
    private val sixPm = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun ts(ms: Long) = Timestamp(Date(ms))

    /** 6–8 PM today, ₹100 + 18% GST, like the designs. */
    private fun booking(
        status: BookingStatus = BookingStatus.CONFIRMED,
        entry: Long = sixPm,
        hours: Int = 2,
        exitedAt: Long? = null
    ) = Booking(
        id = "PK-TEST",
        entryTime = ts(entry),
        exitTime = ts(entry + hours * hour),
        durationHours = hours,
        basePrice = 100.0,
        taxAmount = 18.0,
        totalPrice = 118.0,
        bookingStatus = status,
        actualExitTime = exitedAt?.let(::ts),
        updatedAt = ts(exitedAt ?: entry)
    )

    // ---- Today tabs ----

    @Test fun OP_tabs_waitingIsArrivingUntil15MinutesLate() {
        assertEquals(TodayTab.Arriving, OperatorRules.tabOf(booking(), sixPm - 30 * min))
        assertEquals(TodayTab.Arriving, OperatorRules.tabOf(booking(), sixPm + 14 * min))
        assertEquals(TodayTab.Late, OperatorRules.tabOf(booking(), sixPm + 15 * min))
        // Still late after the end time: only the operator decides "didn't come".
        assertEquals(TodayTab.Late, OperatorRules.tabOf(booking(), sixPm + 3 * hour))
    }

    @Test fun OP_tabs_parkedEvenIfItStartedYesterday() {
        val yesterday = booking(BookingStatus.ACTIVE, entry = sixPm - 24 * hour, hours = 30)
        assertEquals(TodayTab.Parked, OperatorRules.tabOf(yesterday, sixPm))
    }

    @Test fun OP_tabs_laterDaysAreNotOnToday() {
        assertNull(OperatorRules.tabOf(booking(entry = sixPm + 24 * hour), sixPm))
    }

    @Test fun OP_tabs_closedTodayIsDone_closedYesterdayIsGone() {
        assertEquals(TodayTab.Done, OperatorRules.tabOf(booking(BookingStatus.COMPLETED, exitedAt = sixPm + hour), sixPm + 2 * hour))
        assertEquals(TodayTab.Done, OperatorRules.tabOf(booking(BookingStatus.CANCELLED), sixPm))
        val old = booking(BookingStatus.COMPLETED, entry = sixPm - 48 * hour, exitedAt = sixPm - 47 * hour)
        assertNull(OperatorRules.tabOf(old, sixPm))
    }

    @Test fun OP_tabs_groupedAndSorted() {
        val a = booking().copy(id = "a", entryTime = ts(sixPm + hour), exitTime = ts(sixPm + 2 * hour))
        val b = booking().copy(id = "b", entryTime = ts(sixPm + 30 * min), exitTime = ts(sixPm + 2 * hour))
        val grouped = OperatorRules.groupForToday(listOf(a, b), sixPm)
        assertEquals(listOf("b", "a"), grouped.getValue(TodayTab.Arriving).map { it.id })
        assertTrue(grouped.getValue(TodayTab.Late).isEmpty())
    }

    // ---- Overstay ----

    @Test fun OP_overstay_freeWithinTheGrace() {
        val b = booking(BookingStatus.ACTIVE)
        assertEquals(0.0, OperatorRules.overstayCharge(b, b.exitTime.toDate().time), 0.0)
        assertEquals(0.0, OperatorRules.overstayCharge(b, b.exitTime.toDate().time + 10 * min), 0.0)
        assertEquals(0.0, OperatorRules.overstayCharge(b, sixPm + hour), 0.0) // Leaving early
    }

    @Test fun OP_overstay_perStartedHalfHourAtTheBookingsRatePlusTax() {
        val b = booking(BookingStatus.ACTIVE) // ₹50/h → ₹25 per half hour → ₹29.50 with 18%
        val end = b.exitTime.toDate().time
        assertEquals(29.5, OperatorRules.overstayCharge(b, end + 11 * min), 0.001)
        assertEquals(29.5, OperatorRules.overstayCharge(b, end + 30 * min), 0.001)
        assertEquals(59.0, OperatorRules.overstayCharge(b, end + 31 * min), 0.001)
        assertEquals(29.5, OperatorRules.overstayStepPrice(b), 0.001)
    }

    @Test fun OP_overstay_blockedBaysCostNothing() {
        val block = booking(BookingStatus.ACTIVE).copy(basePrice = 0.0, taxAmount = 0.0, totalPrice = 0.0, walkIn = true)
        assertEquals(0.0, OperatorRules.overstayCharge(block, block.exitTime.toDate().time + 3 * hour), 0.0)
    }

    // ---- QR and plates ----

    @Test fun OP_qr_onlyParkOverCodes() {
        assertEquals("PK-AB12-CD34", OperatorRules.bookingIdFromQr("PARKOVER:PK-AB12-CD34"))
        assertEquals("PK-AB12-CD34", OperatorRules.bookingIdFromQr("  PARKOVER:PK-AB12-CD34\n"))
        assertNull(OperatorRules.bookingIdFromQr("https://example.com"))
        assertNull(OperatorRules.bookingIdFromQr("PARKOVER:"))
        assertNull(OperatorRules.bookingIdFromQr("PARKOVER:../../users/x"))
        assertNull(OperatorRules.bookingIdFromQr(null))
    }

    @Test fun OP_plates_ignoreSpacesAndCase() {
        assertTrue(OperatorRules.plateMatches("MH01AB1234", "mh01 ab-1234"))
        assertTrue(OperatorRules.plateMatches("MH01AB1234", "1234"))
        assertFalse(OperatorRules.plateMatches("MH01AB1234", "5678"))
        assertFalse(OperatorRules.plateMatches("MH01AB1234", "1")) // Too short to mean anything
    }

    @Test fun OP_formatSpan() {
        assertEquals("under a minute", OperatorRules.formatSpan(30_000))
        assertEquals("42 min", OperatorRules.formatSpan(42 * min))
        assertEquals("2h", OperatorRules.formatSpan(2 * hour))
        assertEquals("1h 42m", OperatorRules.formatSpan(hour + 42 * min))
    }
}

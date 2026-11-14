package com.dhruvk.parkover.data

import com.dhruvk.parkover.data.repository.BookingRepository.Companion.LOCK_BUCKET_MS
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.lockBuckets
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.lockId
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.newBookingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layer 1: the slot-lock maths behind double-booking protection (must match firestore.rules). */
class SlotLockBucketsTest {

    private val H = 3_600_000L
    private val base = 1_000_000L * LOCK_BUCKET_MS // a half-hour boundary

    @Test fun BOOK_06_twoHourStayOnTheHalfHourHoldsFourBuckets() {
        assertEquals((1_000_000L..1_000_003L).toList(), lockBuckets(base, base + 2 * H))
    }

    @Test fun BOOK_06_partialBucketsAreHeldAtBothEnds() {
        // 10:05 → 11:05 touches 10:00, 10:30 and 11:00
        val b = lockBuckets(base + 5 * 60_000, base + H + 5 * 60_000)
        assertEquals(listOf(1_000_000L, 1_000_001L, 1_000_002L), b)
    }

    @Test fun BOOK_06_backToBackStaysDoNotOverlap() {
        val first = lockBuckets(base, base + H).toSet()
        val second = lockBuckets(base + H, base + 2 * H).toSet()
        assertTrue((first intersect second).isEmpty())
    }

    @Test fun BOOK_06_overlappingStaysShareABucket() {
        val first = lockBuckets(base, base + 2 * H).toSet()
        val second = lockBuckets(base + H, base + 3 * H).toSet()
        assertTrue((first intersect second).isNotEmpty())
    }

    @Test fun BOOK_04_emptyOrBackwardsIntervalHoldsNothing() {
        assertTrue(lockBuckets(base, base).isEmpty())
        assertTrue(lockBuckets(base + H, base).isEmpty())
    }

    @Test fun lockIdMatchesTheRulesFormat() {
        assertEquals("p1_G-01_1000000", lockId("p1", "G-01", 1_000_000L))
    }

    @Test fun BOOK_13_bookingIdsHaveTheRightShapeAndDoNotCollide() {
        val ids = List(10_000) { newBookingId() }
        assertTrue(ids.all { Regex("PK-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}").matches(it) })
        assertEquals(ids.size, ids.toSet().size)
    }
}

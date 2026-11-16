package com.dhruvk.parkover.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.domain.BookingRules
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.exists
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.ui.TestTags
import com.dhruvk.parkover.ui.components.MapTestHooks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.Date

/** E2E smoke on the live dev project, signed in as a fresh throwaway driver. */
@RunWith(AndroidJUnit4::class)
class E2eSmokeTest {
    private val e2e = E2eRule()
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(e2e).around(compose).around(FailureArtifactsRule())

    @Before
    fun setUp() {
        E2eRule.allowLocationIfAsked(2_000)
        seedVehicle()
    }

    /** "Total free of total" on the details screen, e.g. 103 from "103 of 104". */
    private fun freeNowOnScreen(): Int? = compose.onAllNodes(hasText(" of 104", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes().firstNotNullOfOrNull { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString()?.let { Regex("(\\d+) of 104").find(it)?.groupValues?.get(1)?.toInt() }
        }

    @Test
    fun E2E_02_realSearch_realRoadRoute() {
        compose.openGovandiSheet()
        compose.waitUntil(NET_MS) {
            compose.onAllNodes(hasTestTag(TestTags.SPOT_SHEET_META), useUnmergedTree = true).fetchSemanticsNodes()
                .any { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString()?.let { Regex("\\d+(\\.\\d)? (km|m) · \\d+ (min|h)").containsMatchIn(it) } == true }
        }
        assertFalse(compose.exists(hasText("straight-line", substring = true)))
        assertEquals(Govandi.ID, MapTestHooks.selectedPinId)
        assertTrue("a real road route has many points, got ${MapTestHooks.routePointCount}", MapTestHooks.routePointCount > 10)
    }

    @Test
    fun E2E_03_bookAndCancel_liveBackend_restoresTheBay() {
        compose.openGovandiDetails()
        val bay = compose.fromDetailsToPayment()
        compose.confirmBooking()
        compose.waitFor(hasText("You're booked!"), NET_MS)

        val booking = myBookings().single()
        assertEquals("CONFIRMED", booking["bookingStatus"])
        assertEquals(bay, booking["spotNumber"])
        val slots = BookingRepository.lockBuckets(
            (booking["entryTime"] as Timestamp).toDate().time, (booking["exitTime"] as Timestamp).toDate().time)
        val before = slots.associateWith { govandiCarsInSlot(it) }
        assertTrue("each slot of the stay counts this car", before.values.all { it >= 1 })
        assertEquals(4, locksFor(booking["_id"] as String))

        compose.onNodeWithText("My bookings").performClick()
        compose.waitFor(hasText("Cancel booking"), NET_MS)
        compose.onNodeWithText("Cancel booking").performClick()
        compose.waitFor(hasText("Keep it"))
        compose.onAllNodesWithText("Cancel booking").onLast().performClick()
        compose.waitUntil(NET_MS) { myBookings().single()["bookingStatus"] == "CANCELLED" }
        slots.forEach { assertEquals("slot $it given back", before.getValue(it) - 1, govandiCarsInSlot(it)) }
        assertEquals(0, locksFor(booking["_id"] as String))
    }

    @Test
    fun E2E_04_anotherDriverBooks_freeNowCountsOnlyCarsParkedNow() {
        compose.openGovandiDetails()
        compose.waitUntil(NET_MS) { freeNowOnScreen() != null }
        val shown = freeNowOnScreen()!!

        // Driver B, a separate signed-in client, books through the app's own booking code.
        val b = e2e.otherDriver()
        val repoB = BookingRepository(FirebaseFirestore.getInstance(b), FirebaseAuth.getInstance(b))
        fun book(start: Long, bay: String) = runBlocking {
            repoB.createBooking(Booking(
                parkingId = Govandi.ID, parkingName = Govandi.NAME, vehicleType = VehicleType.FOUR_WHEELER,
                floorNumber = 0, floorName = "Ground Level", spotNumber = bay, vehicleNumber = "MH01ZZ0002",
                entryTime = Timestamp(Date(start)), exitTime = Timestamp(Date(start + BookingRules.HOUR_MS)),
                durationHours = 1, bookingStatus = BookingStatus.CONFIRMED
            ))
        }.getOrThrow()

        // Tomorrow: today's "free now" must not move.
        val tomorrow = book(BookingRules.nextHalfHour(System.currentTimeMillis()) + 24 * BookingRules.HOUR_MS, "G-98")
        Thread.sleep(6_000)
        assertEquals("a booking for tomorrow must not lower today's count", shown, freeNowOnScreen())

        // Parked right now (stay started at the top of this half hour): count drops live.
        val nowStart = BookingRepository.bucketOf(System.currentTimeMillis()) * BookingRepository.LOCK_BUCKET_MS
        val parkedNow = book(nowStart, "G-99")
        compose.waitUntil(15_000) { freeNowOnScreen() == shown - 1 }

        runBlocking { repoB.cancelBooking(parkedNow) }.getOrThrow()
        compose.waitUntil(15_000) { freeNowOnScreen() == shown }
        runBlocking { repoB.cancelBooking(tomorrow) }.getOrThrow()
    }

    @Test
    fun E2E_05_realNetworkOff_atConfirm_nothingBooked_thenRecovers() {
        compose.openGovandiDetails()
        compose.fromDetailsToPayment()
        E2eRule.setNetwork(false)
        try {
            Thread.sleep(3_000)
            compose.confirmBooking()
            compose.waitFor(hasText("nothing was booked", substring = true), NET_MS)
        } finally {
            E2eRule.setNetwork(true)
        }
        // Back online: nothing was queued behind the user's back…
        Thread.sleep(8_000)
        assertTrue(myBookings().isEmpty())
        // …and trying again works. Firestore may still be reconnecting, so like a driver we
        // retry if it says offline again (each attempt must fail cleanly or succeed once).
        var booked = false
        repeat(3) {
            if (booked) return@repeat
            compose.confirmBooking()
            booked = runCatching { compose.waitFor(hasText("You're booked!"), 20_000) }.isSuccess
        }
        assertTrue("booking should succeed once the network is back", booked)
        assertEquals(1, myBookings().size)
    }
}

package com.dhruvk.parkover.booking

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.TestBackend
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.testing.waitForPins
import com.dhruvk.parkover.ui.TestTags
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Layer 2 (Phase 3): the booking flow end to end on the phone, against the local Firestore
 * emulator with the real rules. The emulator is wiped before each test.
 */
@RunWith(AndroidJUnit4::class)
class BookingFlowTest {

    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    private val spot = TestPlaces.SPOT_A
    private lateinit var uid: String

    @Before
    fun seedVehicle() {
        uid = FirebaseAuth.getInstance().currentUser!!.uid
        TestBackend.seed(
            "vehicles/test-car",
            mapOf("id" to "test-car", "userId" to uid, "type" to "FOUR_WHEELER", "brand" to "Tata",
                "model" to "Nexon", "number" to "MH02AB1234", "color" to "", "default" to true, "preset" to false)
        )
    }

    /**
     * Home → details → vehicle → bay → time → review → payment, stopping at Confirm.
     * The fake parking has 12 of 20 bays free; the app spreads the 8 occupied ones across the
     * floor (G-01 is one of them), so tests use bays that stay free: G-02, G-03, G-04.
     */
    private fun goToPayment(bay: String = "G-02") {
        compose.waitForPins(spot.id)
        compose.tapPin(spot.id)
        compose.waitFor(hasText("View details"))
        compose.onNodeWithText("View details").performClick()
        compose.waitFor(hasText("Book a spot"))
        compose.onNodeWithText("Book a spot").performClick()
        compose.waitFor(hasText("MH 02 AB 1234") or hasText("MH02AB1234"))
        compose.onNodeWithText("Continue").performClick()
        compose.waitFor(hasContentDescription("Spot $bay, free"))
        compose.onNodeWithContentDescription("Spot $bay, free").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitFor(hasText("Review"))
        compose.onNodeWithText("Review").performClick()
        compose.waitFor(hasText("Continue to pay"))
        compose.onNodeWithText("Continue to pay").performClick()
        compose.waitFor(hasText("Pay at parking"))
        compose.onNodeWithText("Pay at parking").performClick()
    }

    private fun confirm() = compose.onNodeWithText("Confirm booking", substring = true).performClick()
    private fun bookings() = TestBackend.list("bookings")
    private fun locks() = TestBackend.list("slot_locks")

    @Test
    fun BOOK_01_happyPath_createsBookingAndLocksTheBay() {
        goToPayment()
        confirm()
        compose.waitFor(hasText("You're booked!"), timeoutMs = 20_000)
        compose.waitFor(hasTestTag(TestTags.BOOKING_ID))

        val booking = bookings().single()
        assertEquals("CONFIRMED", booking["bookingStatus"])
        assertEquals(uid, booking["userId"])
        assertEquals("G-02", booking["spotNumber"])
        assertEquals(66.0, (booking["totalPrice"] as Number).toDouble(), 0.001) // ₹30 × 2 h + 10%
        assertEquals("2 h stay holds four 30-minute locks", 4, locks().count { it["bookingId"] == booking["_id"] })
        val occupancy = TestBackend.list("occupancy")
        assertEquals("one occupancy doc per slot of the stay", 4, occupancy.size)
        assertTrue(occupancy.all { it["FOUR_WHEELER"] == 1L && (it["floors"] as Map<*, *>)["0"] == 1L })
    }

    @Test
    fun BOOK_06_bayTakenByAnotherDriver_showsAsTaken() {
        val now = System.currentTimeMillis()
        val first = BookingRepository.bucketOf(now)
        (first..first + 8).forEach { b ->
            TestBackend.seed("slot_locks/${BookingRepository.lockId(spot.id, "G-03", b)}",
                mapOf("parkingId" to spot.id, "spotNumber" to "G-03", "bucket" to b, "bookingId" to "PK-OTHER", "userId" to "someone-else"))
        }
        compose.waitForPins(spot.id)
        compose.tapPin(spot.id)
        compose.waitFor(hasText("View details")); compose.onNodeWithText("View details").performClick()
        compose.waitFor(hasText("Book a spot")); compose.onNodeWithText("Book a spot").performClick()
        compose.waitFor(hasText("Continue")); compose.onNodeWithText("Continue").performClick()
        compose.waitFor(hasContentDescription("Spot G-03, taken"))
        compose.waitFor(hasContentDescription("Spot G-02, free"))
    }

    @Test
    fun BOOK_07_anotherDriverTakesTheBayAtTheLastMoment_bookingRefusedClearly() {
        goToPayment("G-04")
        // Someone else books G-04 while we're on the payment screen.
        val first = BookingRepository.bucketOf(System.currentTimeMillis())
        (first..first + 8).forEach { b ->
            TestBackend.seed("slot_locks/${BookingRepository.lockId(spot.id, "G-04", b)}",
                mapOf("parkingId" to spot.id, "spotNumber" to "G-04", "bucket" to b, "bookingId" to "PK-OTHER", "userId" to "someone-else"))
        }
        confirm()
        compose.waitFor(hasText("was just booked for that time", substring = true), timeoutMs = 20_000)
        assertTrue("no booking may be created", bookings().isEmpty())
    }

    @Test
    fun BOOK_08_parkingFillsUpMidFlow_bookingRefused() {
        goToPayment()
        // Every car bay is booked during the chosen time (by other drivers, at that time only).
        val first = BookingRepository.bucketOf(System.currentTimeMillis())
        (first..first + 8).forEach { b ->
            TestBackend.seed("occupancy/${BookingRepository.occupancyId(spot.id, b)}", mapOf(
                "parkingId" to spot.id, "bucket" to b, "FOUR_WHEELER" to spot.totalSpotsFourWheeler,
                "floors" to mapOf("0" to spot.totalSpotsFourWheeler), "lastBookingId" to "PK-OTHER"))
        }
        confirm()
        compose.waitFor(hasText("full", substring = true), timeoutMs = 20_000)
        assertTrue(bookings().isEmpty())
        assertTrue(locks().isEmpty())
    }

    @Test
    fun BOOK_10_doubleTapConfirm_makesOneBooking() {
        goToPayment()
        val button = compose.onNodeWithText("Confirm booking", substring = true)
        button.performClick()
        runCatching { button.performClick() } // second tap while the first is in flight
        compose.waitFor(hasText("You're booked!"), timeoutMs = 20_000)
        Thread.sleep(2_000)
        assertEquals(1, bookings().size)
    }

    @Test
    fun BOOK_09_offlineAtConfirm_saysNothingWasBooked() {
        goToPayment()
        Tasks.await(TestBackend.firestore().disableNetwork(), 10, TimeUnit.SECONDS)
        try {
            confirm()
            compose.waitFor(hasText("nothing was booked", substring = true), timeoutMs = 30_000)
        } finally {
            Tasks.await(TestBackend.firestore().enableNetwork(), 10, TimeUnit.SECONDS)
        }
        Thread.sleep(2_000)
        assertTrue("an offline tap must not book later behind the user's back", bookings().isEmpty())
    }

    @Test
    fun BKL_04_cancelFromMyBookings_freesTheBay() {
        goToPayment()
        confirm()
        compose.waitFor(hasText("My bookings"), timeoutMs = 20_000)
        compose.onNodeWithText("My bookings").performClick()
        compose.waitFor(hasText("Cancel booking"))
        compose.onNodeWithText("Cancel booking").performClick()
        compose.waitFor(hasText("Keep it"))
        compose.onAllNodesWithText("Cancel booking").onLast().performClick()

        compose.waitUntil(15_000) { bookings().singleOrNull()?.get("bookingStatus") == "CANCELLED" }
        compose.waitUntil(10_000) { locks().isEmpty() }
        assertTrue("cancel gives every slot back", TestBackend.list("occupancy").all { it["FOUR_WHEELER"] == 0L })
        compose.waitFor(hasText("Past · 1"))
    }
}

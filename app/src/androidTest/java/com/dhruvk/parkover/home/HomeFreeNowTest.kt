package com.dhruvk.parkover.home

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.dhruvk.parkover.testing.exists
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.testing.waitForPins
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * FREE-NOW: the count on the map is bays booked *right now*; bookings for later don't lower it,
 * and the floor plan shows bays taken during the chosen time only.
 */
@RunWith(AndroidJUnit4::class)
class HomeFreeNowTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    private val spot = TestPlaces.SPOT_A // 20 car bays on the ground floor
    private val now = BookingRepository.bucketOf(System.currentTimeMillis())

    private fun occupy(bucket: Long, cars: Int) = TestBackend.seed(
        "occupancy/${BookingRepository.occupancyId(spot.id, bucket)}",
        mapOf("parkingId" to spot.id, "bucket" to bucket, "FOUR_WHEELER" to cars, "floors" to mapOf("0" to cars), "lastBookingId" to "PK-OTHER")
    )

    private fun lock(bay: String, bucket: Long) = TestBackend.seed(
        "slot_locks/${BookingRepository.lockId(spot.id, bay, bucket)}",
        mapOf("parkingId" to spot.id, "spotNumber" to bay, "bucket" to bucket, "bookingId" to "PK-OTHER", "userId" to "someone-else")
    )

    @Before
    fun seed() {
        occupy(now, 3)          // 3 cars parked right now
        occupy(now + 48, 15)    // 15 booked for tomorrow: must not change today's count
        occupy(now + 49, 15)
    }

    @Test
    fun FREE_NOW_mapCountsOnlyThisHalfHour() {
        compose.waitForPins(spot.id)
        compose.tapPin(spot.id)
        compose.waitFor(hasText("17 free"), timeoutMs = 20_000)
        assertFalse("tomorrow's bookings must not count today", compose.exists(hasText("2 free")))
    }

    @Test
    fun FREE_NOW_floorPlanBlocksOnlyBaysTakenDuringTheChosenTime() {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        TestBackend.seed("vehicles/test-car", mapOf("id" to "test-car", "userId" to uid, "type" to "FOUR_WHEELER",
            "brand" to "Tata", "model" to "Nexon", "number" to "MH02AB1234", "color" to "", "default" to true, "preset" to false))
        (now..now + 8).forEach { lock("G-05", it) }   // booked during the default 2 h window
        (now + 48..now + 52).forEach { lock("G-06", it) } // booked tomorrow only

        compose.waitForPins(spot.id)
        compose.tapPin(spot.id)
        compose.waitFor(hasText("View details")); compose.onNodeWithText("View details").performClick()
        compose.waitFor(hasText("Book a spot")); compose.onNodeWithText("Book a spot").performClick()
        compose.waitFor(hasText("Continue")); compose.onNodeWithText("Continue").performClick()
        compose.waitFor(hasContentDescription("Spot G-05, taken"))
        compose.waitFor(hasContentDescription("Spot G-06, free"))
    }
}

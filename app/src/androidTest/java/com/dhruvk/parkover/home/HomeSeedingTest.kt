package com.dhruvk.parkover.home

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.FakeParkingSource
import com.dhruvk.parkover.testing.TestBackend
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.waitForPins
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** SPOT-05 / LOAD-06: parkings without live counts get them, from the listener's snapshot. */
@RunWith(AndroidJUnit4::class)
class HomeSeedingTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(parking = FakeParkingSource(seedsAvailability = true), resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun SPOT_05_missingLiveCountsAreCreatedWithFullCapacity() {
        compose.waitForPins(TestPlaces.SPOT_A.id, TestPlaces.SPOT_B.id)
        compose.waitUntil(15_000) { TestBackend.list("parking_availability").size == 2 }
        val a = TestBackend.list("parking_availability").single { it["_id"] == TestPlaces.SPOT_A.id }
        assertEquals(TestPlaces.SPOT_A.totalSpotsFourWheeler.toLong(), a["availableSpotsFourWheeler"])
        Thread.sleep(3_000)
        assertEquals("seeded once, nothing extra", 2, TestBackend.list("parking_availability").size)
    }
}

package com.dhruvk.parkover.booking

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.TestBackend
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.click
import com.dhruvk.parkover.testing.clickText
import com.dhruvk.parkover.testing.myVehicles
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.testing.waitForPins
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** BOOK-02: a driver with no vehicle adds one inside the booking flow and books. */
@RunWith(AndroidJUnit4::class)
class NoVehicleBookingTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun BOOK_02_noVehicleYet_addInsideTheFlow_thenBook() {
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(hasText("View details")); compose.clickText("View details")
        compose.waitFor(hasText("Book a spot")); compose.clickText("Book a spot")
        compose.waitFor(hasText("Add a vehicle"))
        compose.clickText("Add a vehicle")
        compose.waitFor(hasText("Save vehicle"))
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Maruti")
        fields[1].performTextInput("Swift")
        fields[2].performTextInput("MH01ZZ9999")
        compose.clickText("Save vehicle")

        compose.waitFor(hasText("MH 01 ZZ 9999") or hasText("MH01ZZ9999"), 15_000)
        compose.clickText("Continue")
        compose.waitFor(hasContentDescription("Spot G-02, free"))
        compose.click(hasContentDescription("Spot G-02, free"))
        compose.clickText("Continue")
        compose.waitFor(hasText("Review")); compose.clickText("Review")
        compose.waitFor(hasText("Continue to pay")); compose.clickText("Continue to pay")
        compose.waitFor(hasText("Pay at parking")); compose.clickText("Pay at parking")
        compose.click(hasText("Confirm booking", substring = true))
        compose.waitFor(hasText("You're booked!"), 20_000)

        assertEquals(1, myVehicles().size)
        assertEquals("MH01ZZ9999", TestBackend.list("bookings").single()["vehicleNumber"])
    }
}

package com.dhruvk.parkover.auth

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.click
import com.dhruvk.parkover.testing.clickText
import com.dhruvk.parkover.testing.exists
import com.dhruvk.parkover.testing.seedVehicle
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.testing.waitForPins
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** AUTH-02 reopening while signed in; LIFE-05 Back through the booking flow. */
@RunWith(AndroidJUnit4::class)
class ReopenAndBackTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    private val authScreens = listOf("Get started", "Log in to ParkOver", "Create your account")

    @Test
    fun AUTH_02_signedIn_opensStraightToHome_neverFlashesLogin() {
        // Watch every frame from launch until Home shows.
        var sawAuth = false
        compose.waitUntil(20_000) {
            if (authScreens.any { compose.exists(hasText(it)) }) sawAuth = true
            compose.exists(hasText("PARKING NEAR YOU"))
        }
        assertFalse("a signed-in driver must not see the login screens on launch", sawAuth)
        // Relaunch (as if from the launcher) and watch again.
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {
            if (authScreens.any { compose.exists(hasText(it)) }) sawAuth = true
            compose.exists(hasText("PARKING NEAR YOU"))
        }
        assertFalse(sawAuth)
    }

    @Test
    fun LIFE_05_backWalksTheBookingFlowStepByStep_keepsChoices_neverToLogin() {
        seedVehicle("test-car", "MH02AB1234", default = true)
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(hasText("View details")); compose.clickText("View details")
        compose.waitFor(hasText("Book a spot")); compose.clickText("Book a spot")
        compose.waitFor(hasText("Continue")); compose.clickText("Continue")
        compose.waitFor(hasContentDescription("Spot G-02, free")); compose.click(hasContentDescription("Spot G-02, free"))
        compose.clickText("Continue")
        compose.waitFor(hasText("4h")); compose.clickText("4h")
        compose.waitFor(hasText("Review")); compose.clickText("Review")
        compose.waitFor(hasText("Continue to pay")); compose.clickText("Continue to pay")
        compose.waitFor(hasText("Pay at parking"))

        // Back, one step at a time, keeping what was chosen.
        Espresso.pressBack()
        compose.waitFor(hasText("Continue to pay"))                  // review
        compose.waitFor(hasText("4 hours", substring = true))         // duration kept
        Espresso.pressBack()
        compose.waitFor(hasText("When are you parking?"))             // time
        Espresso.pressBack()
        compose.waitFor(hasText("Pick your spot"))                    // bay
        compose.waitFor(hasContentDescription("Spot G-02, selected")) // bay kept
        Espresso.pressBack()
        compose.waitFor(hasText("Choose vehicle"))
        Espresso.pressBack()
        compose.waitFor(hasText("Book a spot"))                       // details
        Espresso.pressBack()
        compose.waitFor(hasText("PARKING NEAR YOU"))                  // home
        assertFalse(authScreens.any { compose.exists(hasText(it)) })
    }
}

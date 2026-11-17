package com.dhruvk.parkover.home

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.TestBackend
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.click
import com.dhruvk.parkover.testing.openTab
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.testing.waitForGone
import com.dhruvk.parkover.testing.waitForPins
import com.google.firebase.auth.FirebaseAuth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** SPOT-08: save and unsave a parking from the map sheet; Saved follows live. */
@RunWith(AndroidJUnit4::class)
class SavedParkingTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    private fun savedDocs() = TestBackend.list("users/${FirebaseAuth.getInstance().currentUser!!.uid}/savedParkings")

    @Test
    fun SPOT_08_saveFromSheet_showsInSaved_thenUnsave() {
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(hasContentDescription("Save parking"))
        compose.click(hasContentDescription("Save parking"))
        compose.waitUntil(15_000) { savedDocs().any { it["_id"] == TestPlaces.SPOT_A.id } }
        compose.waitFor(hasContentDescription("Remove from saved"))

        compose.openTab("Saved")
        compose.waitFor(hasText(TestPlaces.SPOT_A.name), 15_000)

        compose.openTab("Home")
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(hasContentDescription("Remove from saved"))
        compose.click(hasContentDescription("Remove from saved"))
        compose.waitUntil(15_000) { savedDocs().isEmpty() }
        compose.openTab("Saved")
        compose.waitForGone(hasText(TestPlaces.SPOT_A.name))
    }
}

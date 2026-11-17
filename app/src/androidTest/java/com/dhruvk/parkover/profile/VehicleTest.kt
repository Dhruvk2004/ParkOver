package com.dhruvk.parkover.profile

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.clickText
import com.dhruvk.parkover.testing.myVehicles
import com.dhruvk.parkover.testing.openTab
import com.dhruvk.parkover.testing.seedVehicle
import com.dhruvk.parkover.testing.waitFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** VEH-01 add a vehicle, VEH-04 one default at a time (emulator). */
@RunWith(AndroidJUnit4::class)
class VehicleTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    private fun openMyVehicles() {
        compose.waitFor(hasText("PARKING NEAR YOU"), 20_000)
        compose.openTab("Profile")
        compose.waitFor(hasText("My vehicles"))
        compose.clickText("My vehicles")
        compose.waitFor(hasText("Add vehicle"))
    }

    @Test
    fun VEH_01_addVehicleThroughTheApp_savedNormalisedAndDefault() {
        openMyVehicles()
        // The floating button's text sits on a child node; click the clickable node that contains it.
        compose.waitFor(hasText("No vehicles yet"))
        compose.onNode(androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyDescendant(hasText("Add vehicle")), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitFor(hasText("Save vehicle"))
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Tata")
        fields[1].performTextInput("Nexon")
        fields[2].performTextInput("mh-02 ab 1234")
        compose.clickText("Save vehicle")

        compose.waitUntil(15_000) { myVehicles().size == 1 }
        val v = myVehicles().single()
        assertEquals("MH02AB1234", v["number"])
        assertEquals("FOUR_WHEELER", v["type"])
        assertEquals("first vehicle becomes the default", true, v["default"])
        compose.waitFor(hasText("Nexon", substring = true))
    }

    @Test
    fun VEH_04_makeDefault_leavesExactlyOneDefault() {
        seedVehicle("car-a", "MH02AB1111", default = true)
        seedVehicle("car-b", "MH02AB2222", default = false)
        openMyVehicles()
        compose.waitFor(hasText("Make default"))
        compose.clickText("Make default")
        compose.waitUntil(15_000) { myVehicles().singleOrNull { it["default"] == true }?.get("id") == "car-b" }
        assertEquals(1, myVehicles().count { it["default"] == true })
    }

    @Test
    fun VEH_04_twoQuickDefaultChanges_neverLeaveTwoDefaults() {
        seedVehicle("car-a", "MH02AB1111", default = true)
        seedVehicle("car-b", "MH02AB2222", default = false)
        seedVehicle("car-c", "MH02AB3333", default = false)
        val repo = BookingRepository()
        runBlocking {
            listOf("car-b", "car-c").map { id -> async { repo.setDefaultVehicle(id) } }.awaitAll()
        }
        assertEquals("exactly one default after two quick taps", 1, myVehicles().count { it["default"] == true })
    }
}

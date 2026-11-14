package com.dhruvk.parkover.home

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitForPins
import com.dhruvk.parkover.ui.TestTags
import com.dhruvk.parkover.ui.components.MapTestHooks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Layer 2: Home map + routing with fake services on a real device. */
@RunWith(AndroidJUnit4::class)
class HomeRouteTest {

    private val app = AppTestRule()
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        .around(app)
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun MAP_03_pinsShowForEverySpot() {
        compose.waitForPins(TestPlaces.SPOT_A.id, TestPlaces.SPOT_B.id)
    }

    @Test
    fun ROUTE_01_tapPin_showsRoadRouteDistanceAndTime() {
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)

        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(TestTags.SPOT_SHEET_META) and hasText("3.2 km", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(TestTags.SPOT_SHEET_META).assertTextContains("7 min", substring = true)
        compose.waitUntil(5_000) { MapTestHooks.routePointCount == 5 }
        assertEquals(TestPlaces.SPOT_A.id, MapTestHooks.selectedPinId)
    }

    @Test
    fun ROUTE_04_routingFails_fallsBackToLabelledStraightLine() {
        app.routing.result = null
        compose.waitForPins(TestPlaces.SPOT_B.id)
        compose.tapPin(TestPlaces.SPOT_B.id)

        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(TestTags.SPOT_SHEET_META) and hasText("straight-line", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, MapTestHooks.routePointCount)
    }
}

package com.dhruvk.parkover.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.data.model.SearchResult
import com.dhruvk.parkover.data.repository.RouteInfo
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.FakeLocation
import com.dhruvk.parkover.testing.FakePlaceSearch
import com.dhruvk.parkover.testing.FakeRouting
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.exists
import com.dhruvk.parkover.testing.sheetMeta
import com.dhruvk.parkover.testing.tapPin
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.testing.waitForGone
import com.dhruvk.parkover.testing.waitForPins
import com.dhruvk.parkover.ui.TestTags
import com.dhruvk.parkover.ui.components.MapTestHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng

/**
 * Layer 2 (Phase 3): Home behaviour with fakes. Each test builds its own fakes, because the
 * app reads them at launch.
 */
@RunWith(AndroidJUnit4::class)
class HomeBehaviourTest {

    private fun chain(app: AppTestRule) = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        .around(app)
        .around(compose)
        .around(FailureArtifactsRule())

    private val compose = createAndroidComposeRule<ParkOverActivity>()

    // Fakes are configured per test through this holder, before the activity starts.
    private val routing = FakeRouting()
    private val location = FakeLocation()
    private val places = FakePlaceSearch()

    @get:Rule
    val rules: RuleChain = chain(AppTestRule(routing = routing, location = location, placeSearch = places))

    private val sheetGone = hasTestTag(TestTags.SPOT_SHEET)

    @Test
    fun MAP_06_tapEmptyMap_closesSheetAndClearsRoute() {
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(sheetMeta("3.2 km"))
        compose.waitUntil(5_000) { MapTestHooks.routePointCount > 0 }

        compose.runOnUiThread { MapTestHooks.tapEmptyMap() }
        compose.waitForGone(sheetGone)
        compose.waitUntil(5_000) { MapTestHooks.routePointCount == 0 }
    }

    @Test
    fun ROUTE_07_quickTapsOnTwoPins_showOnlyTheLastRoute() {
        val slowA = RouteInfo(listOf(TestPlaces.USER, LatLng(TestPlaces.SPOT_A.latitude, TestPlaces.SPOT_A.longitude)), 9.9, 30.0)
        val fastB = RouteInfo(List(7) { TestPlaces.USER }, 1.4, 4.0)
        routing.byDestination = mapOf(
            TestPlaces.SPOT_A.latitude to (4_000L to slowA),
            TestPlaces.SPOT_B.latitude to (0L to fastB)
        )
        compose.waitForPins(TestPlaces.SPOT_A.id, TestPlaces.SPOT_B.id)
        compose.waitUntil(10_000) { compose.exists(hasText("Test Area")) }

        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_B.id)
        compose.waitFor(sheetMeta("1.4 km"))
        // A's slow answer arrives later and must not replace B's.
        Thread.sleep(5_000)
        assertTrue(compose.exists(sheetMeta("1.4 km")))
        assertFalse(compose.exists(sheetMeta("9.9 km")))
        assertEquals(7, MapTestHooks.routePointCount)
    }

    @Test
    fun SRCH_01_03_ownSpotsListFirst_andTappingOneRoutesToIt() {
        places.results = listOf(SearchResult("Test Parking Plaza Mall", "Some road", 19.07, 72.90))
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.onNodeWithTag(TestTags.HOME_SEARCH).performTextInput("Test Parking")

        compose.waitFor(hasText("Test Parking Alpha"))
        compose.waitFor(hasText("Test Parking Plaza Mall"))
        val ours = compose.onNodeWithText("Test Parking Alpha").fetchSemanticsNode().boundsInRoot.top
        val place = compose.onNodeWithText("Test Parking Plaza Mall").fetchSemanticsNode().boundsInRoot.top
        assertTrue("our parking should list above places", ours < place)

        compose.onNodeWithText("Test Parking Alpha").performClick()
        compose.waitFor(sheetMeta("3.2 km"))
        assertEquals(TestPlaces.SPOT_A.id, MapTestHooks.selectedPinId)
    }

    @Test
    fun SRCH_04_ROUTE_08_pickAPlace_thenRoutesStartFromThatPlace() {
        val mall = SearchResult("Govandi Test Mall", "Mall Road", 19.0545, 72.9065)
        places.results = listOf(mall)
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.onNodeWithTag(TestTags.HOME_SEARCH).performTextInput("Govandi Test")
        compose.waitFor(hasText("Govandi Test Mall"))
        compose.onNodeWithText("Govandi Test Mall").performClick()

        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(sheetMeta("3.2 km"))
        assertEquals(mall.latitude, routing.lastFrom!!.latitude, 1e-6)
        assertEquals(mall.longitude, routing.lastFrom!!.longitude, 1e-6)
    }

    @Test
    fun SRCH_06_placeSearchDown_ownSpotsStillFound() {
        places.fail = true
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.onNodeWithTag(TestTags.HOME_SEARCH).performTextInput("Test Parking")
        compose.waitFor(hasText("Test Parking Alpha"))
        compose.waitFor(hasText("Test Parking Bravo"))
    }

    @Test
    fun FLT_04_filtersWithNoMatch_explainAndClear() {
        compose.waitForPins(TestPlaces.SPOT_A.id, TestPlaces.SPOT_B.id)
        compose.onNodeWithText("Under ₹50").performClick()
        compose.onNodeWithText("Covered").performClick()
        compose.waitFor(hasText("No parking matches these filters here."))
        compose.waitUntil(5_000) { MapTestHooks.pinIds.isEmpty() }
        compose.onNodeWithText("Clear filters").performClick()
        compose.waitForPins(TestPlaces.SPOT_A.id)
    }
}

/** GPS is slow to arrive. */
@RunWith(AndroidJUnit4::class)
class HomeSlowGpsTest {
    private val routing = FakeRouting()
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(routing = routing, location = FakeLocation(delayMs = 8_000)))
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun ROUTE_02_LOC_06_tapBeforeGpsFix_waitsThenRoutesFromRealPosition() {
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(sheetMeta("Finding your location"))
        assertEquals("no route from the fallback point", 0, routing.routeCalls.get())

        compose.waitFor(sheetMeta("3.2 km"), timeoutMs = 20_000)
        assertEquals(TestPlaces.USER.latitude, routing.lastFrom!!.latitude, 1e-6)
        compose.waitFor(hasText("Test Area")) // header updates to the real area
    }
}

/** Location permission is off. */
@RunWith(AndroidJUnit4::class)
class HomeNoLocationTest {
    private val routing = FakeRouting()
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(AppTestRule(routing = routing, location = FakeLocation(permission = false, fix = null)))
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun ROUTE_03_noLocation_noRouteFromFallback_andSaysWhy() {
        compose.waitForPins(TestPlaces.SPOT_A.id)
        compose.tapPin(TestPlaces.SPOT_A.id)
        compose.waitFor(sheetMeta("Turn on location for distance"))
        Thread.sleep(2_000)
        assertEquals(0, routing.routeCalls.get())
        assertEquals(0, MapTestHooks.routePointCount)
    }
}

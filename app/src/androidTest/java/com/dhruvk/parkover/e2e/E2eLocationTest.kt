package com.dhruvk.parkover.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.sheetMeta
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.ui.components.MapTestHooks
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** LOC-02: the driver says "Don't allow". run-e2e.sh revokes location before this class. */
@RunWith(AndroidJUnit4::class)
class E2eLocationDeniedTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(E2eRule()).around(compose).around(FailureArtifactsRule())

    @Test
    fun LOC_02_denyLocation_appStillUsable_saysWhy() {
        assertTrue("Android should ask for location", compose.pumpUntil(NET_MS) { E2eRule.denyLocationIfAsked(0) })
        compose.waitFor(hasText("Location off", substring = true, ignoreCase = true), NET_MS) // header is in capitals
        compose.waitUntil(NET_MS) { MapTestHooks.styleLoaded && MapTestHooks.pinIds.isNotEmpty() }
        compose.runOnUiThread { MapTestHooks.selectPin(MapTestHooks.pinIds.first()) }
        compose.waitFor(sheetMeta("Turn on location for distance"), NET_MS)
    }
}

/** LOC-05: permission granted but location switched off in system settings. */
@RunWith(AndroidJUnit4::class)
class E2eGpsOffTest {
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(E2eRule(beforeLaunch = { E2eRule.setLocationEnabled(false); Thread.sleep(2_000) }))
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun LOC_05_gpsOff_noEndlessSpinner_appUsable() {
        // (The header says "Location off · showing Mumbai" here, not "Parking near you".)
        compose.waitFor(androidx.compose.ui.test.hasTestTag(com.dhruvk.parkover.ui.TestTags.HOME_SEARCH), NET_MS)
        compose.waitUntil(NET_MS) { MapTestHooks.styleLoaded && MapTestHooks.pinIds.isNotEmpty() }
        compose.runOnUiThread { MapTestHooks.selectPin(MapTestHooks.pinIds.first()) }
        // Without a position the sheet must settle on a reason, not "Finding your location…" forever.
        compose.waitFor(sheetMeta("Turn on location for distance"), 30_000)
        compose.waitFor(hasText("Location off", substring = true, ignoreCase = true))
    }
}

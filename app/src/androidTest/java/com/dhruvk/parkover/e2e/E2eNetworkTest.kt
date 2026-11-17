package com.dhruvk.parkover.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.click
import com.dhruvk.parkover.testing.clickText
import com.dhruvk.parkover.testing.exists
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.ui.TestTags
import com.dhruvk.parkover.ui.components.MapTestHooks
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private fun sheetMetaText(compose: androidx.compose.ui.test.junit4.ComposeTestRule): String? =
    compose.onAllNodes(hasTestTag(TestTags.SPOT_SHEET_META), useUnmergedTree = true).fetchSemanticsNodes()
        .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }

/** NET-02 Wi-Fi drops on Home; NET-03 Wi-Fi off mid-booking with mobile data on. */
@RunWith(AndroidJUnit4::class)
class E2eNetworkTest {
    private val e2e = E2eRule()
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(e2e).around(compose).around(FailureArtifactsRule())

    @Before
    fun setUp() {
        E2eRule.allowLocationIfAsked(2_000)
        seedVehicle()
    }

    @Test
    fun NET_02_networkDropsOnHome_mapStaysUsable_thenRecovers() {
        compose.waitFor(hasText("PARKING NEAR YOU"), NET_MS)
        compose.waitUntil(NET_MS) { MapTestHooks.styleLoaded && MapTestHooks.pinIds.isNotEmpty() }
        val pins = MapTestHooks.pinIds
        E2eRule.setNetwork(false)
        try {
            Thread.sleep(3_000)
            assertEquals("pins stay on the map offline", pins.toSet(), MapTestHooks.pinIds.toSet())
            compose.runOnUiThread { MapTestHooks.selectPin(pins.first()) }
            // No road route offline: a labelled straight-line distance, not an endless spinner.
            compose.waitUntil(30_000) { sheetMetaText(compose)?.contains("straight-line") == true }
        } finally {
            E2eRule.setNetwork(true)
        }
        // Back online: tapping the same pin again retries and gets a real road route.
        compose.waitUntil(NET_MS) {
            runCatching { compose.runOnUiThread { MapTestHooks.selectPin(pins.first()) } }
            Thread.sleep(4_000)
            sheetMetaText(compose)?.let { Regex("\\d+(\\.\\d)? (km|m) · \\d+").containsMatchIn(it) && !it.contains("straight-line") } == true
        }
    }

    @Test
    fun NET_03_wifiOffAtConfirm_mobileDataOn_bookedOnceOrClearError() {
        compose.openGovandiDetails()
        compose.fromDetailsToPayment()
        E2eRule.shell("svc wifi disable") // mobile data stays on
        try {
            Thread.sleep(4_000)
            compose.confirmBooking()
            compose.waitUntil(45_000) {
                compose.exists(hasText("You're booked!")) || compose.exists(hasText("nothing was booked", substring = true))
            }
        } finally {
            E2eRule.shell("svc wifi enable")
        }
        Thread.sleep(5_000)
        val n = myBookings().size
        if (compose.exists(hasText("You're booked!"))) assertEquals("booked exactly once over mobile data", 1, n)
        else assertEquals("a clean failure books nothing", 0, n)
    }
}

/** NET-01: the app is opened with no network at all. */
@RunWith(AndroidJUnit4::class)
class E2eOfflineLaunchTest {
    private val e2e = E2eRule(beforeLaunch = { E2eRule.setNetwork(false); Thread.sleep(3_000) })
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(e2e).around(compose).around(FailureArtifactsRule())

    @Test
    fun NET_01_launchOffline_clearMessage_thenRetryWorks() {
        E2eRule.allowLocationIfAsked(2_000)
        compose.waitFor(hasText("PARKING NEAR YOU"), NET_MS)
        compose.waitFor(hasText("Couldn't load parking", substring = true), NET_MS)
        E2eRule.setNetwork(true)
        Thread.sleep(6_000)
        compose.clickText("Retry")
        compose.waitUntil(NET_MS) { MapTestHooks.pinIds.isNotEmpty() }
    }
}

/** AUTH-11: logging in with no network. */
@RunWith(AndroidJUnit4::class)
class E2eLoginOfflineTest {
    private val e2e = E2eRule(signIn = false, accountButSignedOut = true)
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(e2e).around(compose).around(FailureArtifactsRule())

    @Test
    fun AUTH_11_loginOffline_saysNetworkError_thenWorksOnline() {
        compose.waitFor(hasText("Get started") or hasText("Log in to ParkOver"), NET_MS)
        if (compose.exists(hasText("Get started"))) compose.clickText("Get started")
        compose.waitFor(hasText("Log in to ParkOver"))
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(e2e.email)
        fields[1].performTextInput(e2e.password)

        E2eRule.setNetwork(false)
        try {
            Thread.sleep(3_000)
            compose.click(hasText("Log in"))
            compose.waitFor(hasText("Network error", substring = true), NET_MS)
            assertNull(FirebaseAuth.getInstance().currentUser)
        } finally {
            E2eRule.setNetwork(true)
        }
        Thread.sleep(6_000)
        compose.click(hasText("Log in"))
        E2eRule.allowLocationIfAsked(3_000)
        compose.waitFor(hasText("PARKING NEAR YOU"), NET_MS)
        assertNotNull(FirebaseAuth.getInstance().currentUser)
    }
}

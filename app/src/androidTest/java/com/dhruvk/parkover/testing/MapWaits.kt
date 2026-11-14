package com.dhruvk.parkover.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.dhruvk.parkover.ui.components.MapTestHooks

/** Never wait for "idle" on Home: the map and listeners never settle. Poll for a condition. */
fun ComposeTestRule.waitForPins(vararg ids: String, timeoutMs: Long = 30_000) =
    waitUntil(timeoutMs) { MapTestHooks.styleLoaded && MapTestHooks.pinIds.containsAll(ids.toList()) }

/** Taps a pin the way a user would (through the map's own click handler). */
fun ComposeTestRule.tapPin(id: String) = runOnUiThread { MapTestHooks.selectPin(id) }

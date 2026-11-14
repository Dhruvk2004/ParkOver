package com.dhruvk.parkover.testing

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.dhruvk.parkover.ui.TestTags

/** Waits until at least one node matches. */
fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 15_000) =
    waitUntil(timeoutMs) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

/** Waits until nothing matches. */
fun ComposeTestRule.waitForGone(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) =
    waitUntil(timeoutMs) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.exists(matcher: SemanticsMatcher) =
    onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** The spot sheet's "rating · distance · time" line contains [text]. */
fun sheetMeta(text: String) = hasTestTag(TestTags.SPOT_SHEET_META) and hasText(text, substring = true)

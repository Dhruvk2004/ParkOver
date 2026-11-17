package com.dhruvk.parkover.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import com.google.firebase.auth.FirebaseAuth

/** Clicks through semantics (no touch injection), so moving layouts and keyboards don't matter. */
fun ComposeTestRule.click(matcher: SemanticsMatcher) =
    onAllNodes(matcher and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)

fun ComposeTestRule.clickText(text: String) = click(hasText(text))

/** Bottom-bar tab: unselected tabs are icon-only, labelled by content description. */
fun ComposeTestRule.openTab(label: String) =
    onAllNodes(hasClickAction() and (hasText(label) or hasAnyDescendant(hasContentDescription(label)) or hasAnyDescendant(hasText(label))), useUnmergedTree = true)
        .onFirst().performSemanticsAction(SemanticsActions.OnClick)

/** A vehicle for the signed-in emulator user. */
fun seedVehicle(id: String, number: String, default: Boolean) {
    val uid = FirebaseAuth.getInstance().currentUser!!.uid
    TestBackend.seed("vehicles/$id", mapOf("id" to id, "userId" to uid, "type" to "FOUR_WHEELER", "brand" to "Tata",
        "model" to "Nexon", "number" to number, "color" to "", "default" to default, "preset" to false))
}

fun myVehicles() = TestBackend.list("vehicles").filter { it["userId"] == FirebaseAuth.getInstance().currentUser!!.uid }

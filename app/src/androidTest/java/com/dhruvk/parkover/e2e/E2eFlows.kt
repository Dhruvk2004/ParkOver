package com.dhruvk.parkover.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.dhruvk.parkover.testing.waitFor
import com.dhruvk.parkover.ui.TestTags
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/** The real Govandi Station Parking (spot_082, 104 bays on the ground floor). */
object Govandi {
    const val ID = "spot_082"
    const val NAME = "Govandi Station Parking"
}

/** Real network: allow generous waits. */
const val NET_MS = 30_000L

fun ComposeTestRule.openGovandiSheet() {
    waitFor(hasText("PARKING NEAR YOU"), NET_MS)
    onNodeWithTag(TestTags.HOME_SEARCH).performTextInput("Govandi")
    waitFor(hasText(Govandi.NAME), NET_MS)
    onNodeWithText(Govandi.NAME).performClick()
    waitFor(hasText("View details"), NET_MS)
}

fun ComposeTestRule.openGovandiDetails() {
    openGovandiSheet()
    onNodeWithText("View details").performClick()
    waitFor(hasText("Book a spot"), NET_MS)
}

/** Details → vehicle → first free bay → time → review → payment ("Pay at parking" chosen). */
fun ComposeTestRule.fromDetailsToPayment(): String {
    onNodeWithText("Book a spot").performClick()
    waitFor(hasText("MH 01 ZZ 0001") or hasText("MH01ZZ0001"), NET_MS)
    onNodeWithText("Continue").performClick()
    val freeBay = SemanticsMatcher("a free bay") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.matches(Regex("Spot .+, free")) } == true
    }
    waitFor(freeBay, NET_MS)
    val bay = onAllNodes(freeBay, useUnmergedTree = true)[0].fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].first().removePrefix("Spot ").removeSuffix(", free")
    onNode(hasContentDescription("Spot $bay, free"), useUnmergedTree = true).performClick()
    onNodeWithText("Continue").performClick()
    waitFor(hasText("Review")); onNodeWithText("Review").performClick()
    waitFor(hasText("Continue to pay")); onNodeWithText("Continue to pay").performClick()
    waitFor(hasText("Pay at parking")); onNodeWithText("Pay at parking").performClick()
    return bay
}

fun ComposeTestRule.confirmBooking() = onNodeWithText("Confirm booking", substring = true).performClick()

// ---- Live data, read as the signed-in test user (through the real rules) ----

fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
fun uid(): String = FirebaseAuth.getInstance().currentUser!!.uid

/** A vehicle for the current user, tagged MH01ZZ0001 so test data is easy to spot. */
fun seedVehicle() {
    val id = "e2e-car-${uid().take(8)}"
    Tasks.await(db().collection("vehicles").document(id).set(mapOf(
        "id" to id, "userId" to uid(), "type" to "FOUR_WHEELER", "brand" to "Tata", "model" to "Nexon",
        "number" to "MH01ZZ0001", "color" to "", "default" to true, "preset" to false
    )), NET_MS, TimeUnit.MILLISECONDS)
}

fun myBookings(): List<Map<String, Any?>> =
    Tasks.await(db().collection("bookings").whereEqualTo("userId", uid()).get(), NET_MS, TimeUnit.MILLISECONDS)
        .documents.map { it.data.orEmpty() + ("_id" to it.id) }

/** Cars booked at Govandi in one 30-minute slot, read from the server (0 when no doc). */
fun govandiCarsInSlot(bucket: Long): Long =
    Tasks.await(db().collection("occupancy").document(com.dhruvk.parkover.data.repository.BookingRepository.occupancyId(Govandi.ID, bucket))
        .get(com.google.firebase.firestore.Source.SERVER), NET_MS, TimeUnit.MILLISECONDS)
        .getLong("FOUR_WHEELER") ?: 0

fun locksFor(bookingId: String): Int =
    Tasks.await(db().collection("slot_locks").whereEqualTo("parkingId", Govandi.ID).get(), NET_MS, TimeUnit.MILLISECONDS)
        .documents.count { it.getString("bookingId") == bookingId }

/**
 * Polls [condition] while keeping the app's frames running. Compose tests only draw frames
 * when the test waits through Compose, so a plain UIAutomator wait freezes the app (e.g. it
 * never navigates after sign-up). Use this around system dialogs.
 */
fun ComposeTestRule.pumpUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        runCatching { mainClock.advanceTimeBy(100) }
        if (condition()) return true
        Thread.sleep(100)
    }
    return false
}

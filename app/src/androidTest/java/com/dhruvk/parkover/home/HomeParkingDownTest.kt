package com.dhruvk.parkover.home

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.FakeParkingSource
import com.dhruvk.parkover.testing.TestPlaces
import com.dhruvk.parkover.testing.waitForPins
import com.dhruvk.parkover.ui.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Layer 2: the parking list can't be fetched (DNS failure, offline). */
@RunWith(AndroidJUnit4::class)
class HomeParkingDownTest {

    private val parking = FakeParkingSource(ApiResult.Error("Unable to resolve host"))
    private val app = AppTestRule(parking = parking)
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION))
        .around(app)
        .around(compose)
        .around(FailureArtifactsRule())

    @Test
    fun SPOT_02_parkingSourceDown_showsRetryBanner_andRetryRecovers() {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag(TestTags.HOME_STATUS_BANNER) and hasAnyDescendant(hasText("Couldn't load parking", substring = true)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Network comes back; Retry loads the spots.
        parking.result = ApiResult.Success(listOf(TestPlaces.SPOT_A))
        compose.onNodeWithText("Retry").performClick()
        compose.waitForPins(TestPlaces.SPOT_A.id)
    }
}

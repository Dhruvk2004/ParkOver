package com.dhruvk.parkover.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.exists
import com.dhruvk.parkover.testing.waitFor
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * E2E-1: a brand-new driver. run-e2e.sh revokes location first, so Android really asks.
 * AUTH-07 sign up, LOC-01 real permission prompt + real GPS, AUTH-13 log out.
 */
@RunWith(AndroidJUnit4::class)
class E2eFirstLaunchTest {
    private val e2e = E2eRule(signIn = false)
    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(e2e).around(compose).around(FailureArtifactsRule())

    @Test
    fun E2E_01_signUp_allowLocation_seeRealArea_thenLogOut() {
        // AUTH-07: sign up through the app
        compose.waitFor(hasText("Get started"), NET_MS)
        compose.onNodeWithText("Create an account", substring = true).performClick()
        compose.waitFor(hasText("Create your account"))
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("E2E Driver")
        fields[1].performTextInput(e2e.email)
        fields[2].performTextInput(e2e.password)
        fields[3].performTextInput(e2e.password)
        // The keyboard can swallow the tap on the button below it; close it first.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        // Invoke the button's click action directly: a simulated touch can land on empty space
        // while the layout shifts as the keyboard closes.
        compose.onNode(hasClickAction() and hasText("Create account"))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

        // LOC-01: Android asks for location on Home; allow it
        // UIAutomator taps the system dialog; pumpUntil keeps the app drawing meanwhile.
        assertTrue("Android should ask for location on first launch",
            compose.pumpUntil(NET_MS) { E2eRule.allowLocationIfAsked(0) })
        compose.waitFor(hasText("PARKING NEAR YOU"), NET_MS)
        assertNotNull(FirebaseAuth.getInstance().currentUser)
        val profile = Tasks.await(db().collection("users").document(uid()).get(), NET_MS, TimeUnit.MILLISECONDS)
        assertTrue("sign-up should create users/{uid}", profile.exists())

        // Real GPS fix replaces the Mumbai fallback in the header
        compose.waitUntil(NET_MS) { !compose.exists(hasText("Mumbai")) && !compose.exists(hasText("Location off", substring = true)) }

        // AUTH-13: log out; back must not return to Home
        // (Unselected tabs are icon-only; click through semantics so layout shifts don't matter.)
        val click = androidx.compose.ui.semantics.SemanticsActions.OnClick
        compose.onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("Profile")), useUnmergedTree = true)
            .performSemanticsAction(click)
        compose.waitFor(hasText("Log out"))
        // The menu row merges its children, so in the merged tree the row itself says "Log out".
        compose.onAllNodes(hasClickAction() and hasText("Log out")).onFirst().performSemanticsAction(click)
        compose.waitFor(hasText("Log out?"))
        compose.onAllNodes(hasClickAction() and hasText("Log out")).onLast().performSemanticsAction(click)
        // Log out lands on the Login screen (or Start); either way, signed out.
        compose.waitFor(hasText("Log in to ParkOver") or hasText("Get started"), NET_MS)
        org.junit.Assert.assertNull(FirebaseAuth.getInstance().currentUser)
        E2eRule.device().pressBack()
        Thread.sleep(1_500)
        // Back from Login leaves the app (or stays on auth); it must never reopen Home.
        assertFalse(E2eRule.device().hasObject(androidx.test.uiautomator.By.text("PARKING NEAR YOU")))

        e2e.signBackIn() // so cleanup can delete this account
    }
}

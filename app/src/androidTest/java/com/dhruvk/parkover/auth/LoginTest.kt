package com.dhruvk.parkover.auth

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruvk.parkover.ParkOverActivity
import com.dhruvk.parkover.testing.AppTestRule
import com.dhruvk.parkover.testing.FailureArtifactsRule
import com.dhruvk.parkover.testing.TestBackend
import com.dhruvk.parkover.testing.waitFor
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Layer 2 (Phase 3): signed-out start and email login, against the Auth emulator. */
@RunWith(AndroidJUnit4::class)
class LoginTest {

    private val compose = createAndroidComposeRule<ParkOverActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(AppTestRule(signIn = false, resetBackend = true))
        .around(compose)
        .around(FailureArtifactsRule())

    private fun openLogin() {
        compose.waitFor(hasText("Get started"), timeoutMs = 20_000)
        compose.onNodeWithText("Get started").performClick()
        compose.waitFor(hasText("Log in to ParkOver"))
    }

    private fun typeCredentials(email: String, password: String) {
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(email)
        fields[1].performTextInput(password)
        compose.onAllNodesWithText("Log in").onFirst().performClick()
    }

    @Test
    fun AUTH_01_signedOut_opensOnStartScreen() {
        compose.waitFor(hasText("Get started"), timeoutMs = 20_000)
        assertNull(FirebaseAuth.getInstance().currentUser)
    }

    @Test
    fun AUTH_04_wrongPassword_showsInlineErrorAndStays() {
        TestBackend.signIn(TestBackend.DRIVER_B) // make sure the account exists
        FirebaseAuth.getInstance().signOut()
        openLogin()
        typeCredentials(TestBackend.DRIVER_B, "not-the-password")
        compose.waitFor(hasText("The email or password is incorrect"), timeoutMs = 20_000)
        compose.waitFor(hasText("Log in to ParkOver"))
        assertNull(FirebaseAuth.getInstance().currentUser)
    }

    @Test
    fun AUTH_03_correctLogin_landsOnHome() {
        TestBackend.signIn(TestBackend.DRIVER_B)
        FirebaseAuth.getInstance().signOut()
        openLogin()
        typeCredentials(TestBackend.DRIVER_B, TestBackend.PASSWORD)
        compose.waitFor(hasText("PARKING NEAR YOU"), timeoutMs = 20_000)
        assertEquals(TestBackend.DRIVER_B, FirebaseAuth.getInstance().currentUser?.email)
    }
}

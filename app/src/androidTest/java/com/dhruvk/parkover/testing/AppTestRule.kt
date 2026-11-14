package com.dhruvk.parkover.testing

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dhruvk.parkover.BuildConfig
import com.dhruvk.parkover.di.AppServices
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Wrap the activity rule with this (RuleChain.outerRule(app).around(compose)) so it runs first:
 *  - installs fakes into AppServices before the app starts, and resets them afterwards;
 *  - optionally wipes the emulator first (resetBackend = true) for tests that write data;
 *  - signs in a throwaway emulator user (or the dev account from local.properties when run live).
 * Configure fakes through the constructor: the app reads them as soon as it launches.
 */
class AppTestRule(
    val routing: FakeRouting = FakeRouting(),
    val placeSearch: FakePlaceSearch = FakePlaceSearch(),
    val location: FakeLocation = FakeLocation(),
    val parking: FakeParkingSource = FakeParkingSource(),
    private val signIn: Boolean = true,
    private val resetBackend: Boolean = false
) : TestWatcher() {

    override fun starting(description: Description) {
        AppServices.routing = routing
        AppServices.placeSearch = placeSearch
        AppServices.parkingSource = parking
        AppServices.locationFactory = { location }
        if (resetBackend) TestBackend.reset()
        if (signIn) ensureSignedIn() else FirebaseAuth.getInstance().signOut()
    }

    override fun finished(description: Description) {
        AppServices.reset()
    }

    private fun ensureSignedIn() {
        if (TestBackend.usingEmulator) {
            TestBackend.signIn(TestBackend.DRIVER_A)
            return
        }
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return
        check(BuildConfig.DEV_LOGIN_EMAIL.isNotBlank()) {
            "No test account: add DEV_LOGIN_EMAIL and DEV_LOGIN_PASSWORD to local.properties"
        }
        Tasks.await(
            auth.signInWithEmailAndPassword(BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_LOGIN_PASSWORD),
            30, TimeUnit.SECONDS
        )
    }
}

/**
 * Put this INSIDE the activity rule (…around(compose).around(FailureArtifactsRule())) so it
 * runs while the app is still on screen. On failure it saves a screenshot and recent logcat to
 * /sdcard/Android/data/com.dhruvk.parkover/files/test-failures/ (pulled by run-device-tests.sh).
 */
class FailureArtifactsRule : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "test-failures").apply { mkdirs() }
        val name = "${description.testClass.simpleName}.${description.methodName}"
        runCatching { UiDevice.getInstance(instrumentation).takeScreenshot(File(dir, "$name.png")) }
        runCatching {
            val pfd = instrumentation.uiAutomation.executeShellCommand("logcat -d -t 400")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                FileOutputStream(File(dir, "$name.log")).use { input.copyTo(it) }
            }
        }
        Log.e("AppTestRule", "Saved failure artifacts for $name in $dir")
    }

}

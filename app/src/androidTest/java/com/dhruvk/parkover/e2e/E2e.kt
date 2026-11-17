package com.dhruvk.parkover.e2e

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.di.AppServices
import com.dhruvk.parkover.testing.TestBackend
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assume.assumeFalse
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Layer 3 (Phase 4) E2E against the LIVE dev project (parkover-b042b): real network, GPS,
 * routing, search and rules. Run only through scripts/run-e2e.sh.
 *
 * Every test gets brand-new throwaway accounts (e2e-<random>@parkover.test, random password
 * generated here and never stored). On finish it cancels their active bookings (bays and free
 * counts go back), deletes their vehicles and profile data, then deletes the accounts.
 * Cancelled booking documents remain: the rules forbid deleting bookings.
 */
class E2eRule(
    private val signIn: Boolean = true,
    /** Create the account, then sign out before the app starts (for login tests). */
    private val accountButSignedOut: Boolean = false,
    /** Run just before the app starts, e.g. network or GPS off. */
    private val beforeLaunch: () -> Unit = {}
) : TestWatcher() {

    val accounts = mutableListOf<Pair<FirebaseApp, String>>()
    lateinit var email: String; private set
    lateinit var password: String; private set

    override fun starting(description: Description) {
        assumeFalse("E2E runs against the live project only (scripts/run-e2e.sh)", TestBackend.usingEmulator)
        AppServices.reset()
        FirebaseAuth.getInstance().signOut()
        email = newEmail()
        password = newPassword()
        if (signIn || accountButSignedOut) createAccount(FirebaseApp.getInstance(), email, password)
        if (accountButSignedOut) FirebaseAuth.getInstance().signOut()
        beforeLaunch()
    }

    override fun finished(description: Description) {
        runCatching { setNetwork(true) }
        runCatching { setLocationEnabled(true) }
        // Signed out at the end (e.g. a login test that failed)? Sign back in so cleanup can delete.
        if (FirebaseAuth.getInstance().currentUser == null && accounts.isNotEmpty()) runCatching { signBackIn() }
        // Clean up every account this test made, including ones created through the UI.
        FirebaseAuth.getInstance().currentUser?.let { u -> if (accounts.none { it.second == u.uid }) accounts += FirebaseApp.getInstance() to u.uid }
        accounts.forEach { (app, _) -> runCatching { cleanUp(app) } }
        runCatching { setNetwork(true) }
        FirebaseAuth.getInstance().signOut()
    }

    /** Signs this test's account back in (e.g. after a UI logout) so cleanup can delete it. */
    fun signBackIn() {
        Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS)
    }

    /** A second, independent signed-in client (another driver) in this process. */
    fun otherDriver(name: String = "driverB"): FirebaseApp {
        val default = FirebaseApp.getInstance()
        val app = FirebaseApp.getApps(context()).firstOrNull { it.name == name }
            ?: FirebaseApp.initializeApp(context(), default.options, name)
        createAccount(app, newEmail(), newPassword())
        return app
    }

    private fun createAccount(app: FirebaseApp, email: String, password: String) {
        val user = Tasks.await(FirebaseAuth.getInstance(app).createUserWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS).user!!
        accounts += app to user.uid
    }

    private fun cleanUp(app: FirebaseApp) {
        val auth = FirebaseAuth.getInstance(app)
        val user = auth.currentUser ?: return
        val db = FirebaseFirestore.getInstance(app)
        val repo = com.dhruvk.parkover.data.repository.BookingRepository(db, auth)
        val mine = Tasks.await(db.collection("bookings").whereEqualTo("userId", user.uid).get(), 30, TimeUnit.SECONDS)
            .toObjects(com.dhruvk.parkover.data.model.Booking::class.java)
        mine.filter { it.bookingStatus in setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE) }
            .forEach { kotlinx.coroutines.runBlocking { repo.cancelBooking(it) } }
        Tasks.await(db.collection("vehicles").whereEqualTo("userId", user.uid).get(), 30, TimeUnit.SECONDS)
            .documents.forEach { Tasks.await(it.reference.delete(), 30, TimeUnit.SECONDS) }
        val userDoc = db.collection("users").document(user.uid)
        for (sub in listOf("saved", "savedParkings", "upi", "upiIds")) {
            runCatching {
                Tasks.await(userDoc.collection(sub).get(), 20, TimeUnit.SECONDS).documents
                    .forEach { Tasks.await(it.reference.delete(), 20, TimeUnit.SECONDS) }
            }
        }
        runCatching { Tasks.await(userDoc.delete(), 20, TimeUnit.SECONDS) }
        Tasks.await(user.delete(), 30, TimeUnit.SECONDS)
    }

    companion object {
        private val random = SecureRandom()
        private fun token(n: Int) = (1..n).map { "abcdefghjkmnpqrstuvwxyz23456789"[random.nextInt(31)] }.joinToString("")
        fun newEmail() = "e2e-${token(10)}@parkover.test"
        fun newPassword() = "E2e-${token(20)}"

        fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext
        fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        fun shell(cmd: String): String {
            val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
            return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }

        /** Real network off/on: Wi-Fi and mobile data (USB adb is unaffected). */
        fun setNetwork(on: Boolean) {
            val v = if (on) "enable" else "disable"
            shell("svc wifi $v")
            shell("svc data $v")
        }

        /** Location (GPS) on/off in system settings. */
        fun setLocationEnabled(on: Boolean) {
            shell("cmd location set-location-enabled $on")
        }

        /** Taps "Don't allow" if Android asks for location. Returns whether it asked. */
        fun denyLocationIfAsked(timeoutMs: Long = 4_000): Boolean {
            val d = device()
            val button = d.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_deny_button")), timeoutMs)
                ?: d.findObject(By.textContains("Don't allow"))
                ?: d.findObject(By.textContains("Don’t allow"))
                ?: return false
            button.click()
            return true
        }

        /** Taps "While using the app" if Android asks for location. Returns whether it asked. */
        fun allowLocationIfAsked(timeoutMs: Long = 4_000): Boolean {
            val d = device()
            val button = d.wait(Until.findObject(By.textContains("While using the app")), timeoutMs)
                ?: d.findObject(By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button"))
                ?: return false
            button.click()
            return true
        }
    }
}

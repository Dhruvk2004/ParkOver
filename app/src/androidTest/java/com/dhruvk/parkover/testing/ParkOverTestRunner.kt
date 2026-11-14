package com.dhruvk.parkover.testing

import android.app.Application
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.memoryCacheSettings

/**
 * Points Firebase at the local emulator before the app touches it, so device tests never read
 * or write the real project. scripts/run-device-tests.sh starts the emulator and forwards its
 * ports over USB (adb reverse), so 127.0.0.1 on the phone is the Mac.
 * Pass `-e firebase live` to run against the real project instead (E2E only).
 */
class ParkOverTestRunner : AndroidJUnitRunner() {

    private var live = false

    override fun onCreate(arguments: Bundle?) {
        live = arguments?.getString("firebase") == "live"
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application) {
        if (!live) {
            val firestore = FirebaseFirestore.getInstance()
            // Settings first, then the emulator; memory cache so nothing sticks between runs.
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(memoryCacheSettings {})
                .build()
            firestore.useEmulator(TestBackend.HOST, TestBackend.FIRESTORE_PORT)
            FirebaseAuth.getInstance().useEmulator(TestBackend.HOST, TestBackend.AUTH_PORT)
            TestBackend.usingEmulator = true
        }
        super.callApplicationOnCreate(app)
    }
}

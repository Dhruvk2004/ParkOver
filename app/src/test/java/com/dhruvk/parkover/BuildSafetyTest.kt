package com.dhruvk.parkover

import org.junit.Assert.assertEquals
import org.junit.Test

/** AUTH-15: release builds carry no dev-login account. Run with testReleaseUnitTest too. */
class BuildSafetyTest {
    @Test fun AUTH_15_releaseHasNoDevLogin() {
        if (!BuildConfig.DEBUG) {
            assertEquals("", BuildConfig.DEV_LOGIN_EMAIL)
            assertEquals("", BuildConfig.DEV_LOGIN_PASSWORD)
        }
    }
}

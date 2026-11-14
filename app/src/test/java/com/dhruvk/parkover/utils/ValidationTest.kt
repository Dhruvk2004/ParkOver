package com.dhruvk.parkover.utils

import com.dhruvk.parkover.viewmodels.AccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AUTH-06, VEH-02, PROF-03: input validation. */
class ValidationTest {

    private fun ok(r: ValidationResult) = r.isValid()

    @Test fun AUTH_06_emailAcceptsNormalAddresses() {
        listOf("dhruv@gmail.com", "test-driver@example.com", "a.b+tag@company.co.in", "x_y@sub.domain.org")
            .forEach { assertTrue(it, ok(ValidationUtils.validateEmail(it))) }
    }

    @Test fun AUTH_06_emailRejectsBadOrEmptyInput() {
        listOf("", "   ", "plainaddress", "@no-user.com", "user@", "user@domain", "user @gmail.com", "user@@gmail.com")
            .forEach { assertFalse("'$it' should be rejected", ok(ValidationUtils.validateEmail(it))) }
        assertEquals("Email is required", ValidationUtils.validateEmail("").getErrorOrNull())
    }

    @Test fun AUTH_06_emailToleratesSurroundingSpacesFromKeyboards() {
        // Phone keyboards often add a trailing space after autocomplete.
        assertTrue(ok(ValidationUtils.validateEmail("dhruv@gmail.com ")))
    }

    @Test fun AUTH_06_passwordNeedsSixCharacters() {
        assertFalse(ok(ValidationUtils.validatePassword("")))
        assertFalse(ok(ValidationUtils.validatePassword("12345")))
        assertTrue(ok(ValidationUtils.validatePassword("123456")))
        assertFalse(ok(ValidationUtils.validatePassword("      "))) // only spaces
    }

    @Test fun AUTH_06_confirmPasswordMustMatch() {
        assertFalse(ok(ValidationUtils.validateConfirmPassword("secret1", "secret2")))
        assertTrue(ok(ValidationUtils.validateConfirmPassword("secret1", "secret1")))
    }

    @Test fun AUTH_06_nameAndPhone() {
        assertFalse(ok(ValidationUtils.validateName(" ")))
        assertFalse(ok(ValidationUtils.validateName("A")))
        assertTrue(ok(ValidationUtils.validateName("Dhruv")))
        assertTrue(ok(ValidationUtils.validatePhone("98765 43210")))
        assertTrue(ok(ValidationUtils.validatePhone("98765-43210")))
        assertFalse(ok(ValidationUtils.validatePhone("98765")))
        assertFalse(ok(ValidationUtils.validatePhone("98765abcde")))
    }

    @Test fun VEH_02_standardPlatesInAnyCaseAndSpacing() {
        listOf("MH 02 YA 5666", "MH02YA5666", "mh02ab1234", "DL 1 C 1234", "KA 51 MN 0001", "MH-02-AB-1234")
            .forEach { assertTrue("'$it' should be valid", it.isValidVehicleNumber()) }
    }

    @Test fun VEH_02_delhiCategoryAndThreeLetterSeries() {
        // Delhi puts a category letter after the RTO number: DL 3C AB 1234
        assertTrue("DL 3C AB 1234".isValidVehicleNumber())
        assertTrue("DL 10 CAB 1234".isValidVehicleNumber())
    }

    @Test fun VEH_02_bharatSeriesPlates() {
        // BH series: YY BH #### XX
        assertTrue("22 BH 1234 AA".isValidVehicleNumber())
        assertTrue("23BH0001B".isValidVehicleNumber())
    }

    @Test fun VEH_02_rejectsJunk() {
        listOf("", "MH02", "1234", "MH02AB12345", "M02AB1234", "MH02AB", "ABCDEFGH", "MH 02 AB 12E4")
            .forEach { assertFalse("'$it' should be invalid", it.isValidVehicleNumber()) }
    }

    @Test fun PROF_03_upiIds() {
        listOf("name@okaxis", "dhruv.k@ybl", "98765@paytm").forEach { assertTrue(it, AccountViewModel.UPI_REGEX.matches(it)) }
        listOf("name", "@okaxis", "name@", "na me@okaxis", "name@ok1").forEach { assertFalse(it, AccountViewModel.UPI_REGEX.matches(it)) }
    }
}

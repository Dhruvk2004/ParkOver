package com.dhruvk.parkover.domain

import com.dhruvk.parkover.utils.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

/** LOC-07: the header never shows Devanagari. */
class AreaNameTest {
    @Test fun LOC_07_skipsDevanagariAndBlanks() {
        // What the on-device geocoder actually returned in Chembur: only the road was English.
        assertEquals("Sion - Trombay Road",
            LocationHelper.pickAreaName(listOf("चेंबूर", "मुंबई", "Sion - Trombay Road", null, "महाराष्ट्र")))
        assertEquals("Chembur", LocationHelper.pickAreaName(listOf("", "Chembur", "Mumbai")))
        assertEquals("Current Location", LocationHelper.pickAreaName(listOf("मुंबई", null, " ")))
    }
}

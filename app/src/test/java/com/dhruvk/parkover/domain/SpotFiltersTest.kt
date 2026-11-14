package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.OperatingHours
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.viewmodels.SpotFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/** FLT-01..04: Home filter chips. */
class SpotFiltersTest {

    private val centreLat = 19.0560
    private val centreLng = 72.9080
    private val near = ParkingSpot(id = "near", latitude = 19.0580, longitude = 72.9120, pricePerHourFourWheeler = 30.0,
        amenities = listOf("CCTV", "EV Charging"), operatingHours = OperatingHours(is24Hours = true))
    private val far = near.copy(id = "far", latitude = 19.2183, longitude = 72.9781) // Thane, ~19 km
    private val pricey = near.copy(id = "pricey", pricePerHourFourWheeler = 80.0, amenities = listOf("Covered"),
        operatingHours = OperatingHours(is24Hours = false))
    private val edge50 = near.copy(id = "edge50", pricePerHourFourWheeler = 50.0, amenities = emptyList())
    private val inactive = near.copy(id = "inactive", isActive = false)
    private val all = listOf(near, far, pricey, edge50, inactive)

    private fun ids(vararg f: SpotFilter) =
        SpotFilters.apply(all, f.toSet(), centreLat, centreLng, nearbyRadiusKm = 5.0).map { it.id }

    @Test fun FLT_01_nearbyHidesSpotsOutsideTheRadius() {
        assertEquals(listOf("near", "pricey", "edge50"), ids(SpotFilter.NEARBY))
        assertEquals(listOf("near", "far", "pricey", "edge50"), ids()) // off: everything active
    }

    @Test fun FLT_02_under50IncludesExactly50() {
        assertEquals(listOf("near", "far", "edge50"), ids(SpotFilter.UNDER_50))
    }

    @Test fun FLT_03_amenityAndHoursFilters() {
        assertEquals(listOf("near", "far"), ids(SpotFilter.EV))
        assertEquals(listOf("pricey"), ids(SpotFilter.COVERED))
        assertEquals(listOf("near", "far", "edge50"), ids(SpotFilter.OPEN_24H))
    }

    @Test fun FLT_04_filtersCombineAndCanMatchNothing() {
        assertEquals(listOf("near"), ids(SpotFilter.NEARBY, SpotFilter.EV, SpotFilter.UNDER_50))
        assertEquals(emptyList<String>(), ids(SpotFilter.COVERED, SpotFilter.UNDER_50))
    }

    @Test fun inactiveSpotsNeverShow() {
        assertEquals(false, SpotFilters.apply(all, emptySet(), centreLat, centreLng, 5.0).any { it.id == "inactive" })
    }
}

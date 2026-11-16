package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.Floor
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.SlotCapacity
import com.dhruvk.parkover.data.repository.SlotOccupancy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPOT-04/FREE-NOW: "free now" counts only bays booked in the current 30-minute slot. */
class FreeNowTest {

    private val govandi = ParkingSpot(
        id = "spot_082", totalSpotsTwoWheeler = 60, totalSpotsFourWheeler = 40, totalSpotsHeavy = 4,
        availableSpotsTwoWheeler = 0, availableSpotsFourWheeler = 0, availableSpotsHeavy = 0, // ignored
        floors = listOf(Floor(floorNumber = 0, name = "Ground Level", totalSpots = 104, availableSpots = 0))
    )

    @Test fun nobodyParkedNow_everythingFree_evenIfTheSourceSaidOtherwise() {
        val s = FreeNow.apply(listOf(govandi), emptyMap()).single()
        assertEquals(60, s.availableSpotsTwoWheeler)
        assertEquals(40, s.availableSpotsFourWheeler)
        assertEquals(4, s.availableSpotsHeavy)
        assertEquals(104, s.floors.single().availableSpots)
    }

    @Test fun onlyTheCurrentSlotCounts() {
        // The repository hands FreeNow just the current slot; future bookings never reach it.
        val now = SlotOccupancy(byType = mapOf(VehicleType.FOUR_WHEELER to 3, VehicleType.TWO_WHEELER to 1), byFloor = mapOf("0" to 4))
        val s = FreeNow.apply(listOf(govandi), mapOf("spot_082" to now)).single()
        assertEquals(37, s.availableSpotsFourWheeler)
        assertEquals(59, s.availableSpotsTwoWheeler)
        assertEquals(4, s.availableSpotsHeavy)
        assertEquals(100, s.floors.single().availableSpots)
    }

    @Test fun neverBelowZeroOrAboveCapacity() {
        val over = SlotOccupancy(byType = mapOf(VehicleType.HEAVY to 9, VehicleType.FOUR_WHEELER to -2), byFloor = mapOf("0" to 999))
        val s = FreeNow.apply(listOf(govandi), mapOf("spot_082" to over)).single()
        assertEquals(0, s.availableSpotsHeavy)
        assertEquals(40, s.availableSpotsFourWheeler)
        assertEquals(0, s.floors.single().availableSpots)
    }

    @Test fun otherParkingsAreUntouched() {
        val other = govandi.copy(id = "kurla", totalSpotsFourWheeler = 10)
        val now = mapOf("spot_082" to SlotOccupancy(byType = mapOf(VehicleType.FOUR_WHEELER to 5)))
        val (g, k) = FreeNow.apply(listOf(govandi, other), now)
        assertEquals(35, g.availableSpotsFourWheeler)
        assertEquals(10, k.availableSpotsFourWheeler)
    }

    @Test fun capacityForTheBookingsTypeAndFloor() {
        assertEquals(SlotCapacity(type = 40, floor = 104), FreeNow.capacityFor(govandi, VehicleType.FOUR_WHEELER, 0))
        assertEquals(SlotCapacity(type = 4, floor = null), FreeNow.capacityFor(govandi, VehicleType.HEAVY, 3))
        assertNull(FreeNow.capacityFor(null, VehicleType.FOUR_WHEELER, 0))
    }
}

package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.SlotCapacity
import com.dhruvk.parkover.data.repository.SlotOccupancy

/**
 * "Free now" = the parking's capacity minus the bays booked in the current 30-minute slot.
 * Bookings for later don't count, so today's numbers stay true.
 */
object FreeNow {

    fun apply(spots: List<ParkingSpot>, occupiedNow: Map<String, SlotOccupancy>): List<ParkingSpot> = spots.map { spot ->
        val o = occupiedNow[spot.id] ?: SlotOccupancy()
        fun free(total: Int, type: VehicleType) = (total - (o.byType[type] ?: 0)).coerceIn(0, total.coerceAtLeast(0))
        spot.copy(
            availableSpotsTwoWheeler = free(spot.totalSpotsTwoWheeler, VehicleType.TWO_WHEELER),
            availableSpotsFourWheeler = free(spot.totalSpotsFourWheeler, VehicleType.FOUR_WHEELER),
            availableSpotsHeavy = free(spot.totalSpotsHeavy, VehicleType.HEAVY),
            floors = spot.floors.map { f ->
                f.copy(availableSpots = (f.totalSpots - (o.byFloor[f.floorNumber.toString()] ?: 0)).coerceIn(0, f.totalSpots.coerceAtLeast(0)))
            }
        )
    }

    /** What a booking may take: the type's bays, and the floor's bays. */
    fun capacityFor(spot: ParkingSpot?, type: VehicleType, floorNumber: Int): SlotCapacity? {
        spot ?: return null
        val typeBays = when (type) {
            VehicleType.TWO_WHEELER -> spot.totalSpotsTwoWheeler
            VehicleType.FOUR_WHEELER -> spot.totalSpotsFourWheeler
            VehicleType.HEAVY -> spot.totalSpotsHeavy
        }
        return SlotCapacity(type = typeBays, floor = spot.floors.firstOrNull { it.floorNumber == floorNumber }?.totalSpots)
    }
}

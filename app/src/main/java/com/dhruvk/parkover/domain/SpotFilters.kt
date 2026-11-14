package com.dhruvk.parkover.domain

import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.utils.LocationHelper
import com.dhruvk.parkover.viewmodels.SpotFilter

/** Home screen filter chips, as a pure function. */
object SpotFilters {
    const val CHEAP_MAX_RUPEES = 50.0

    fun apply(
        spots: List<ParkingSpot>,
        filters: Set<SpotFilter>,
        centreLat: Double,
        centreLng: Double,
        nearbyRadiusKm: Double
    ): List<ParkingSpot> = spots.filter { spot ->
        spot.isActive &&
            (SpotFilter.NEARBY !in filters ||
                LocationHelper.calculateDistance(centreLat, centreLng, spot.latitude, spot.longitude) <= nearbyRadiusKm) &&
            (SpotFilter.UNDER_50 !in filters || spot.pricePerHourFourWheeler <= CHEAP_MAX_RUPEES) &&
            (SpotFilter.EV !in filters || spot.amenities.contains("EV Charging")) &&
            (SpotFilter.COVERED !in filters || spot.amenities.contains("Covered")) &&
            (SpotFilter.OPEN_24H !in filters || spot.operatingHours.is24Hours)
    }
}

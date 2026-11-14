package com.dhruvk.parkover.data

import com.dhruvk.parkover.data.api.ParkingSpotsResponse
import com.dhruvk.parkover.data.repository.parkingSpotsFromApi
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/** SPOT-03: the parking JSON from the API, including broken entries. */
class ParkingMappingTest {

    private val good = """
      {"id":"govandi","name":"Govandi Station Parking","address":"Govandi","latitude":19.048,"longitude":72.912,
       "pricePerHourTwoWheeler":10,"pricePerHourFourWheeler":20,"pricePerHourHeavy":40,
       "pricePerDayTwoWheeler":80,"pricePerDayFourWheeler":160,"pricePerDayHeavy":320,
       "totalSpotsTwoWheeler":60,"totalSpotsFourWheeler":40,"totalSpotsHeavy":4,
       "floors":[{"floorNumber":0,"name":"Ground Level","totalSpots":104,"priceMultiplier":1.0}],
       "amenities":["CCTV"],"images":[],"rating":3.3,"reviewCount":90,
       "operatingHours":{"is24Hours":true,"openTime":"00:00","closeTime":"23:59","closedDays":[]},"isActive":true}
    """.trimIndent()

    private fun parse(vararg spots: String) =
        Gson().fromJson("""{"parkingSpots":[${spots.joinToString(",")}],"amenitiesMaster":[]}""", ParkingSpotsResponse::class.java)

    @Test fun SPOT_03_goodEntryMapsAllFields() {
        val spot = parkingSpotsFromApi(parse(good).parkingSpots).single()
        assertEquals("govandi", spot.id)
        assertEquals(20.0, spot.pricePerHourFourWheeler, 0.0)
        assertEquals(40, spot.availableSpotsFourWheeler) // starts full-free until Firestore says otherwise
        assertEquals("Ground Level", spot.floors.single().name)
        assertEquals(true, spot.operatingHours.is24Hours)
    }

    @Test fun SPOT_03_entriesMissingListsOrHoursAreSkippedNotFatal() {
        val noFloors = good.replace("\"govandi\"", "\"no-floors\"").replace(Regex("\"floors\":\\[[^]]*],"), "")
        val noHours = good.replace("\"govandi\"", "\"no-hours\"").replace(Regex("\"operatingHours\":\\{[^}]*},"), "")
        val noName = good.replace("\"govandi\"", "\"no-name\"").replace("\"name\":\"Govandi Station Parking\",", "")
        val ids = parkingSpotsFromApi(parse(good, noFloors, noHours, noName).parkingSpots).map { it.id }
        assertEquals("good entry must survive bad neighbours", true, "govandi" in ids)
        assertEquals("entries without a name can't be shown", false, "no-name" in ids)
    }

    @Test fun SPOT_03_entriesWithoutIdOrCoordinatesAreDropped() {
        val noId = good.replace("\"id\":\"govandi\",", "")
        val zeroCoords = good.replace("\"govandi\"", "\"zero\"").replace("\"latitude\":19.048", "\"latitude\":0").replace("\"longitude\":72.912", "\"longitude\":0")
        val ids = parkingSpotsFromApi(parse(good, noId, zeroCoords).parkingSpots).map { it.id }
        assertEquals(listOf("govandi"), ids)
    }
}

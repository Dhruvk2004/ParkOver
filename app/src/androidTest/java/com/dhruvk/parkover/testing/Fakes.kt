package com.dhruvk.parkover.testing

import com.dhruvk.parkover.data.model.Floor
import com.dhruvk.parkover.data.model.OperatingHours
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.SearchResult
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.repository.RouteInfo
import com.dhruvk.parkover.di.LocationProvider
import com.dhruvk.parkover.di.ParkingSource
import com.dhruvk.parkover.di.PlaceSearchService
import com.dhruvk.parkover.di.RoutingService
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.atomic.AtomicInteger

/** Fixed places around Govandi, Mumbai, used by every fake. */
object TestPlaces {
    val USER = LatLng(19.0560, 72.9080)
    val SPOT_A = ParkingSpot(
        id = "test-spot-a", name = "Test Parking Alpha", address = "Alpha Road, Govandi",
        latitude = 19.0580, longitude = 72.9120,
        pricePerHourFourWheeler = 30.0, totalSpotsFourWheeler = 20, availableSpotsFourWheeler = 12,
        floors = listOf(Floor(floorNumber = 0, name = "Ground Level", totalSpots = 20, availableSpots = 12)),
        rating = 4.2, operatingHours = OperatingHours(is24Hours = true)
    )
    val SPOT_B = SPOT_A.copy(
        id = "test-spot-b", name = "Test Parking Bravo", address = "Bravo Lane, Govandi",
        latitude = 19.0530, longitude = 72.9040, pricePerHourFourWheeler = 60.0
    )
}

class FakeRouting(
    var result: RouteInfo? = RouteInfo(
        points = listOf(TestPlaces.USER, LatLng(19.0570, 72.9100), LatLng(19.0575, 72.9110), LatLng(19.0578, 72.9115), LatLng(TestPlaces.SPOT_A.latitude, TestPlaces.SPOT_A.longitude)),
        distanceKm = 3.2,
        durationMin = 7.0
    ),
    var delayMs: Long = 0,
    var distances: List<Double?>? = null
) : RoutingService {
    val routeCalls = AtomicInteger()
    @Volatile var lastFrom: LatLng? = null
    /** Per-destination answers (by spot latitude) for tests that need different routes. */
    var byDestination: Map<Double, Pair<Long, RouteInfo?>> = emptyMap()
    override suspend fun route(from: LatLng, to: LatLng): RouteInfo? {
        routeCalls.incrementAndGet()
        lastFrom = from
        byDestination[to.latitude]?.let { (wait, answer) -> delay(wait); return answer }
        if (delayMs > 0) delay(delayMs)
        return result
    }
    override suspend fun roadDistancesKm(from: LatLng, destinations: List<LatLng>) = distances
}

class FakePlaceSearch(var results: List<SearchResult> = emptyList(), var fail: Boolean = false) : PlaceSearchService {
    override suspend fun search(query: String, near: LatLng): List<SearchResult> {
        if (fail) throw java.io.IOException("Photon unreachable")
        return results
    }
}

class FakeLocation(
    var permission: Boolean = true,
    var fix: LatLng? = TestPlaces.USER,
    var delayMs: Long = 0,
    var name: String = "Test Area"
) : LocationProvider {
    override fun hasPermission() = permission
    override suspend fun currentLatLng(): LatLng? {
        if (delayMs > 0) delay(delayMs)
        return fix
    }
    override fun areaName(latitude: Double, longitude: Double) = name
}

class FakeParkingSource(
    var result: ApiResult<List<ParkingSpot>> = ApiResult.Success(listOf(TestPlaces.SPOT_A, TestPlaces.SPOT_B)),
    override val seedsAvailability: Boolean = false
) : ParkingSource {
    override suspend fun getParkingSpots() = result
}

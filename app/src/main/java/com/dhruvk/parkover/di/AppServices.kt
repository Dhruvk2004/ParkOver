package com.dhruvk.parkover.di

import android.content.Context
import com.dhruvk.parkover.BuildConfig
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.SearchResult
import com.dhruvk.parkover.data.repository.HttpRoutingService
import com.dhruvk.parkover.data.repository.ParkingRepository
import com.dhruvk.parkover.data.repository.PhotonPlaceSearch
import com.dhruvk.parkover.data.repository.RouteInfo
import com.dhruvk.parkover.utils.LocationHelper
import org.maplibre.android.geometry.LatLng

/** Road routes and road distances. */
interface RoutingService {
    suspend fun route(from: LatLng, to: LatLng): RouteInfo?
    /** Road distance in km from [from] to each destination, in order; null where unknown. */
    suspend fun roadDistancesKm(from: LatLng, destinations: List<LatLng>): List<Double?>?
}

/** Free-text place search. */
interface PlaceSearchService {
    suspend fun search(query: String, near: LatLng): List<SearchResult>
}

/** Where the phone is, and what the area is called. */
interface LocationProvider {
    fun hasPermission(): Boolean
    suspend fun currentLatLng(): LatLng?
    fun areaName(latitude: Double, longitude: Double): String
}

/** The list of parkings. */
interface ParkingSource {
    suspend fun getParkingSpots(): ApiResult<List<ParkingSpot>>
    /** Whether the app should create live-availability docs for these parkings. Fakes say no. */
    val seedsAvailability: Boolean get() = true
}

fun interface Clock {
    fun nowMillis(): Long
}

/**
 * The one place that decides which implementation the app uses. Tests swap these for fakes
 * before the screen under test starts, and call [reset] afterwards.
 */
object AppServices {
    @Volatile var routing: RoutingService = defaultRouting()
    @Volatile var placeSearch: PlaceSearchService = PhotonPlaceSearch()
    @Volatile var parkingSource: ParkingSource = ParkingRepository.getInstance()
    @Volatile var clock: Clock = Clock { System.currentTimeMillis() }
    @Volatile var locationFactory: (Context) -> LocationProvider = { DeviceLocationProvider(it.applicationContext) }

    fun location(context: Context): LocationProvider = locationFactory(context)

    fun reset() {
        routing = defaultRouting()
        placeSearch = PhotonPlaceSearch()
        parkingSource = ParkingRepository.getInstance()
        clock = Clock { System.currentTimeMillis() }
        locationFactory = { DeviceLocationProvider(it.applicationContext) }
    }

    private fun defaultRouting() = HttpRoutingService(
        orsKey = BuildConfig.ORS_API_KEY,
        // The public OSRM demo is fine for testing but not for production traffic.
        allowDemoFallback = BuildConfig.DEBUG
    )
}

/** Real GPS + on-device geocoder. */
class DeviceLocationProvider(context: Context) : LocationProvider {
    private val helper = LocationHelper(context)
    override fun hasPermission() = helper.hasLocationPermission()
    override suspend fun currentLatLng(): LatLng? = helper.getCurrentLocation()?.let { LatLng(it.latitude, it.longitude) }
    override fun areaName(latitude: Double, longitude: Double) = helper.getLocationNameFromCoordinates(latitude, longitude)
}

package com.dhruvk.parkover.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Location switched on in system settings (permission alone isn't enough). */
    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return false
        return androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)
    }

    /**
     * Current position, or null. Never hangs: with location services off the fused provider
     * simply never answers, so return null at once in that case and give up after
     * [FIX_TIMEOUT_MS] otherwise.
     */
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission() || !isLocationEnabled()) return null
        return kotlinx.coroutines.withTimeoutOrNull(FIX_TIMEOUT_MS) { awaitLocation() }
    }

    private suspend fun awaitLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(location)
                        } else {
                            // Request fresh location
                            val stop = requestFreshLocation { freshLocation ->
                                if (continuation.isActive) continuation.resume(freshLocation)
                            }
                            continuation.invokeOnCancellation { stop() }
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }

    /** Asks for one fresh fix; returns a function that cancels the request. */
    private fun requestFreshLocation(callback: (Location?) -> Unit): () -> Unit {
        if (!hasLocationPermission()) {
            callback(null)
            return {}
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).setMaxUpdates(1).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                callback(result.lastLocation)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            callback(null)
            return {}
        }
        return { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, GEOCODER_LOCALE)
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildString {
                    address.subLocality?.let { append("$it, ") }
                    address.locality?.let { append(it) }
                }.ifEmpty {
                    address.getAddressLine(0) ?: "Unknown location"
                }
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    fun getFullAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, GEOCODER_LOCALE)
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].getAddressLine(0) ?: "Unknown location"
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    fun getLocationNameFromCoordinates(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, GEOCODER_LOCALE)
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Prefer subLocality (neighborhood) or locality (city) over featureName (which can be street number).
                // In India the on-device geocoder often returns these in Devanagari even when asked for
                // English, so skip non-Latin values; the road name (thoroughfare) is usually English.
                pickAreaName(listOf(address.subLocality, address.locality, address.thoroughfare, address.subAdminArea, address.adminArea))
            } else {
                "Current Location"
            }
        } catch (e: Exception) {
            "Current Location"
        }
    }

    fun getShortAddress(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, GEOCODER_LOCALE)
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildString {
                    address.locality?.let { append(it) }
                    address.adminArea?.let { 
                        if (isNotEmpty()) append(", ")
                        append(it) 
                    }
                    address.countryName?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }.ifEmpty { "Unknown location" }
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    companion object {
        // The UI is English-only; the device default can return area names in a local script.
        private val GEOCODER_LOCALE = Locale("en", "IN")

        /** Longest wait for a GPS fix before falling back. */
        const val FIX_TIMEOUT_MS = 12_000L

        private fun String.isLatinScript(): Boolean = all { it.code < 0x0250 }

        /** First usable English (Latin-script) name, most specific first; never Devanagari. */
        fun pickAreaName(candidates: List<String?>): String =
            candidates.firstOrNull { !it.isNullOrBlank() && it.isLatinScript() } ?: "Current Location"

        fun calculateDistance(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val earthRadius = 6371.0 // km

            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)

            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)

            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return earthRadius * c
        }

        fun formatDistance(distanceKm: Double): String {
            return if (distanceKm < 1) {
                "${(distanceKm * 1000).toInt()} m"
            } else {
                String.format("%.1f km", distanceKm)
            }
        }
    }
}

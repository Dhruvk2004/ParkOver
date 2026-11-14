package com.dhruvk.parkover.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dhruvk.parkover.utils.Constants
import com.dhruvk.parkover.di.AppServices
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserLocation(
    val latLng: LatLng,
    val areaName: String,
    val isFallback: Boolean
)

enum class SpotFilter(val label: String) {
    NEARBY("Nearby"),
    UNDER_50("Under ₹50"),
    EV("EV"),
    COVERED("Covered"),
    OPEN_24H("24h")
}

/** Map screen state that should survive tab switches: location, filters, camera target. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val locationProvider = AppServices.location(application)

    private val fallback = UserLocation(
        latLng = LatLng(Constants.FALLBACK_LAT, Constants.FALLBACK_LNG),
        areaName = "Mumbai",
        isFallback = true
    )

    private val _location = MutableStateFlow(fallback)
    val location: StateFlow<UserLocation> = _location.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _filters = MutableStateFlow(setOf(SpotFilter.NEARBY))
    val filters: StateFlow<Set<SpotFilter>> = _filters.asStateFlow()

    /** Set once a real fix arrives so the map only auto-centres the first time. */
    var hasCentredOnUser = false

    /** Last camera position, so the map reopens where the user left it after a tab switch. */
    var lastCamera: Pair<LatLng, Double>? = null

    fun refreshLocation() {
        if (!locationProvider.hasPermission()) {
            _location.value = fallback
            return
        }
        viewModelScope.launch {
            _locating.value = true
            val fix = runCatching { locationProvider.currentLatLng() }.getOrNull()
            if (fix != null) {
                val latLng = LatLng(fix.latitude, fix.longitude)
                // Publish the position straight away; the on-device geocoder can take seconds.
                val previousName = _location.value.areaName.takeUnless { _location.value.isFallback }
                _location.value = UserLocation(latLng, previousName ?: "Current location", isFallback = false)
                _locating.value = false
                val name = withContext(Dispatchers.IO) {
                    locationProvider.areaName(fix.latitude, fix.longitude)
                }
                if (_location.value.latLng == latLng) _location.value = _location.value.copy(areaName = name)
            }
            _locating.value = false
        }
    }

    fun toggleFilter(filter: SpotFilter) {
        _filters.value = _filters.value.let { if (filter in it) it - filter else it + filter }
    }
}

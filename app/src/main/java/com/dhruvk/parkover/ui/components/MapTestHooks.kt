package com.dhruvk.parkover.ui.components

import com.dhruvk.parkover.BuildConfig

/**
 * What the map is showing, for UI tests. Pins and the route are MapLibre layers, not Compose
 * nodes, so tests read this instead of pixels. Only updated in debug builds.
 */
object MapTestHooks {
    @Volatile var styleLoaded: Boolean = false; private set
    @Volatile var pinIds: List<String> = emptyList(); private set
    @Volatile var selectedPinId: String? = null; private set
    @Volatile var routePointCount: Int = 0; private set
    @Volatile private var pinClick: ((String) -> Unit)? = null

    /** Same as a user tapping the pin. Call on the main thread. */
    fun selectPin(id: String) {
        check(id in pinIds) { "No pin $id on the map. Pins: $pinIds" }
        checkNotNull(pinClick) { "Map not ready" }.invoke(id)
    }

    internal fun onStyle(loaded: Boolean) { if (BuildConfig.DEBUG) styleLoaded = loaded }
    internal fun onPins(ids: List<String>, selected: String?) {
        if (BuildConfig.DEBUG) { pinIds = ids; selectedPinId = selected }
    }
    internal fun onRoute(points: Int) { if (BuildConfig.DEBUG) routePointCount = points }
    internal fun onPinClickHandler(handler: ((String) -> Unit)?) { if (BuildConfig.DEBUG) pinClick = handler }
}

package com.dhruvk.parkover.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhruvk.parkover.R
import com.dhruvk.parkover.data.model.AvailabilityStatus
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.WarningOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.net.URL

/** One parking pin on the map. [label] is the price text, e.g. "₹40" or "Full". */
data class MapPin(val id: String, val position: LatLng, val label: String, val status: AvailabilityStatus)

/** Lets the screen move the camera; the map registers itself once it's ready. */
class ParkMapController {
    internal var map: MapLibreMap? = null
    internal var padding = IntArray(4) // left, top, right, bottom in px

    fun animateTo(target: LatLng, zoom: Double, durationMs: Int = 700) {
        val m = map ?: return
        val position = CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .padding(padding[0].toDouble(), padding[1].toDouble(), padding[2].toDouble(), padding[3].toDouble())
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(position), durationMs)
    }

    /** Fits both points on screen; [extraBottomPx] leaves room for a sheet that is about to open. */
    fun fitBounds(a: LatLng, b: LatLng, extraPx: Int, extraBottomPx: Int = 0, durationMs: Int = 700) =
        fitPoints(listOf(a, b), extraPx, extraBottomPx, durationMs)

    /** Fits every point (e.g. a whole route, which can bend well outside its two ends). */
    fun fitPoints(points: List<LatLng>, extraPx: Int, extraBottomPx: Int = 0, durationMs: Int = 700) {
        val m = map ?: return
        if (points.size < 2) return
        val bounds = LatLngBounds.Builder().includes(points).build()
        val update = CameraUpdateFactory.newLatLngBounds(
            bounds,
            padding[0] + extraPx, padding[1] + extraPx, padding[2] + extraPx, padding[3] + extraPx + extraBottomPx
        )
        runCatching { m.animateCamera(update, durationMs) }
    }

    val cameraTarget: LatLng? get() = map?.cameraPosition?.target
    val cameraZoom: Double? get() = map?.cameraPosition?.zoom
}

private const val SRC_PINS = "pins"
private const val SRC_ROUTE = "route"
private const val SRC_USER = "user"
private const val SRC_DEST = "destination"
private const val LAYER_PINS = "pins-layer"
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/**
 * ParkOver's map: MapLibre with OpenFreeMap vector tiles (free, no key), restyled in the brand palette.
 * Pins, the user dot, the destination and the route are map layers, so they move with the map.
 */
@Composable
fun ParkMap(
    controller: ParkMapController,
    initialTarget: LatLng,
    initialZoom: Double,
    pins: List<MapPin>,
    selectedPinId: String?,
    userLocation: LatLng?,
    destination: LatLng?,
    route: List<LatLng>?,
    topPadding: Dp,
    bottomPadding: Dp,
    onPinClick: (String) -> Unit,
    onMapClick: () -> Unit,
    onCameraIdle: (LatLng, Double) -> Unit,
    onMapReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    controller.padding = with(density) { intArrayOf(0, topPadding.roundToPx(), 0, bottomPadding.roundToPx()) }

    // Forward lifecycle events; tear the map down when Home leaves composition (tab switch).
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        fun sync(state: Lifecycle.State) {
            if (state.isAtLeast(Lifecycle.State.STARTED) && !started) { mapView.onStart(); started = true }
            if (state.isAtLeast(Lifecycle.State.RESUMED) && !resumed) { mapView.onResume(); resumed = true }
            if (!state.isAtLeast(Lifecycle.State.RESUMED) && resumed) { mapView.onPause(); resumed = false }
            if (!state.isAtLeast(Lifecycle.State.STARTED) && started) { mapView.onStop(); started = false }
        }
        val observer = LifecycleEventObserver { _, _ -> sync(lifecycle.currentState) }
        lifecycle.addObserver(observer)
        sync(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            sync(Lifecycle.State.CREATED)
            controller.map = null
            MapTestHooks.onStyle(false)
            MapTestHooks.onPinClickHandler(null)
            MapTestHooks.onMapClickHandler(null)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        val styleJson = loadParkOverStyle()
        // MapLibre must only be touched on the main thread; don't rely on where we resumed.
        withContext(Dispatchers.Main) { mapView.getMapAsync { map ->
            controller.map = map
            map.uiSettings.apply {
                isCompassEnabled = false
                isTiltGesturesEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.START
                val px = with(density) { 8.dp.roundToPx() }
                setAttributionMargins(px, 0, 0, controller.padding[3] + px)
            }
            map.cameraPosition = CameraPosition.Builder().target(initialTarget).zoom(initialZoom).build()
            val builder = if (styleJson != null) Style.Builder().fromJson(styleJson) else Style.Builder().fromUri(STYLE_URL)
            map.setStyle(builder) { loaded ->
                addOverlayLayers(loaded)
                style = loaded
                MapTestHooks.onStyle(true)
                MapTestHooks.onPinClickHandler { id -> currentOnPinClick(id) }
                MapTestHooks.onMapClickHandler { currentOnMapClick() }
                currentOnMapReady()
            }
            map.addOnMapClickListener { point ->
                val screen = map.projection.toScreenLocation(point)
                val hit = map.queryRenderedFeatures(screen, LAYER_PINS).firstOrNull()?.getStringProperty("id")
                if (hit != null) currentOnPinClick(hit) else currentOnMapClick()
                true
            }
            map.addOnCameraIdleListener {
                map.cameraPosition.target?.let { currentOnCameraIdle(it, map.cameraPosition.zoom) }
            }
        } }
    }

    // Push data into the map whenever it changes.
    LaunchedEffect(style, pins, selectedPinId) {
        val s = style ?: return@LaunchedEffect
        val features = pins.map { pin ->
            val selected = pin.id == selectedPinId
            val icon = pinIconId(pin, selected)
            if (s.getImage(icon) == null) s.addImage(icon, drawPin(context, pin, selected))
            Feature.fromGeometry(Point.fromLngLat(pin.position.longitude, pin.position.latitude)).apply {
                addStringProperty("id", pin.id)
                addStringProperty("icon", icon)
                addNumberProperty("sort", if (selected) 1 else 0)
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        MapTestHooks.onPins(pins.map { it.id }, selectedPinId)
    }
    LaunchedEffect(style, route) {
        val s = style ?: return@LaunchedEffect
        val line = route?.takeIf { it.size > 1 }?.let { pts ->
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.longitude, it.latitude) })))
        } ?: FeatureCollection.fromFeatures(emptyList())
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(line)
        MapTestHooks.onRoute(route?.size ?: 0)
    }
    LaunchedEffect(style, userLocation) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(pointCollection(userLocation))
    }
    LaunchedEffect(style, destination) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_DEST)?.setGeoJson(pointCollection(destination))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun pointCollection(at: LatLng?): FeatureCollection =
    if (at == null) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(at.longitude, at.latitude)))

private fun addOverlayLayers(style: Style) {
    val purple = Purple.toArgb()
    listOf(SRC_ROUTE, SRC_USER, SRC_DEST, SRC_PINS).forEach { style.addSource(GeoJsonSource(it)) }
    style.addLayer(
        LineLayer("route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(android.graphics.Color.WHITE),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer("route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(purple),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        CircleLayer("user-halo", SRC_USER).withProperties(
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleColor(purple),
            PropertyFactory.circleOpacity(0.18f)
        )
    )
    style.addLayer(
        CircleLayer("user-dot", SRC_USER).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(purple),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
    style.addLayer(
        CircleLayer("destination-dot", SRC_DEST).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(CardDark.toArgb()),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_PINS, SRC_PINS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSortKey(Expression.get("sort"))
        )
    )
}

// ---------------- Style ----------------

private var cachedStyle: String? = null

/** Downloads OpenFreeMap's "positron" style and recolours it to the ParkOver palette. */
private suspend fun loadParkOverStyle(): String? = cachedStyle ?: withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject(URL(STYLE_URL).readText())
        val layers = json.getJSONArray("layers")
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val id = layer.getString("id")
            val type = layer.getString("type")
            val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
            when {
                type == "background" -> paint.put("background-color", "#F4F2FA")
                id.startsWith("water") && type == "fill" -> paint.put("fill-color", "#D7D0F5")
                id.startsWith("water") && type == "line" -> paint.put("line-color", "#D7D0F5")
                Regex("park|landcover_grass|landcover_wood").containsMatchIn(id) && type == "fill" -> paint.put("fill-color", "#E3EEDF")
                id.startsWith("building") && type == "fill" -> paint.put("fill-color", "#E6E2F1")
                id.startsWith("highway") && type == "line" && !id.contains("casing") ->
                    paint.put("line-color", if (Regex("motorway|trunk|major|primary").containsMatchIn(id)) "#FFFFFF" else "#FBFAFF")
                type == "symbol" && Regex("place|label|name").containsMatchIn(id) -> paint.put("text-color", "#4A4560")
            }
        }
        json.toString()
    }.getOrNull()?.also { cachedStyle = it }
}

// ---------------- Pins ----------------

private fun pinIconId(pin: MapPin, selected: Boolean) = "pin-${pin.status}-${pin.label}-$selected"

private fun statusColor(status: AvailabilityStatus): Int = when (status) {
    AvailabilityStatus.AVAILABLE -> Purple
    AvailabilityStatus.LIMITED -> WarningOrange
    AvailabilityStatus.FULL -> ErrorRed
}.toArgb()

/** Draws the price-tag pin: coloured pill with "P ₹40", white border, a pointer, and a halo when selected. */
private fun drawPin(context: Context, pin: MapPin, selected: Boolean): Bitmap {
    val d = context.resources.displayMetrics.density
    val scale = if (selected) 1.15f else 1f
    val typeface = ResourcesCompat.getFont(context, R.font.jost_semibold) ?: Typeface.DEFAULT_BOLD
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 13f * d * scale
        this.typeface = typeface
    }
    val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 11f * d * scale
        this.typeface = Typeface.create(typeface, Typeface.BOLD)
    }
    val padH = 8f * d * scale
    val padV = 5f * d * scale
    val gap = 4f * d * scale
    val border = 2f * d
    val pointer = 6f * d * scale
    val halo = if (selected) 5f * d else 0f
    val pWidth = badge.measureText("P")
    val labelWidth = text.measureText(pin.label)
    val pillW = padH * 2 + pWidth + gap + labelWidth
    val pillH = padV * 2 + text.textSize
    val w = (pillW + halo * 2).toInt() + 2
    val h = (pillH + pointer + halo * 2).toInt() + 2
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val color = statusColor(pin.status)
    val radius = 10f * d * scale

    if (selected) {
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 56 }
        c.drawRoundRect(RectF(0f, 0f, pillW + halo * 2, pillH + halo * 2), radius + halo, radius + halo, haloPaint)
    }
    val rect = RectF(halo, halo, halo + pillW, halo + pillH)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = border
    }
    // Pointer below the pill, centred.
    val cx = rect.centerX()
    val tip = Path().apply {
        moveTo(cx - pointer, rect.bottom - border)
        lineTo(cx, rect.bottom + pointer)
        lineTo(cx + pointer, rect.bottom - border)
        close()
    }
    c.drawPath(tip, fill)
    c.drawRoundRect(rect, radius, radius, fill)
    c.drawRoundRect(rect, radius, radius, stroke)

    val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2
    c.drawText("P", rect.left + padH, baseline, badge)
    c.drawText(pin.label, rect.left + padH + pWidth + gap, baseline, text)
    return bmp
}

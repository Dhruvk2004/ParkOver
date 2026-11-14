package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.di.RoutingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL

/** A driving route: the line to draw, road distance in km and drive time in minutes (no live traffic). */
data class RouteInfo(val points: List<LatLng>, val distanceKm: Double, val durationMin: Double)

/**
 * Road distances and routes over HTTP. Uses OpenRouteService (free key, 2,000 routes and 500 matrix calls a day)
 * when ORS_API_KEY is set. Debug builds without a key fall back to the public OSRM demo server,
 * which is fine for testing but not allowed for production traffic.
 */
class HttpRoutingService(
    private val orsKey: String,
    private val allowDemoFallback: Boolean,
    private val orsBaseUrl: String = "https://api.openrouteservice.org/v2",
    private val osrmBaseUrl: String = "https://router.project-osrm.org",
    private val timeoutMs: Int = 15_000
) : RoutingService {

    private val useOrs get() = orsKey.isNotBlank()
    private val enabled get() = useOrs || allowDemoFallback

    override suspend fun route(from: LatLng, to: LatLng): RouteInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        runCatching {
            if (useOrs) {
                val json = get("$orsBaseUrl/directions/driving-car?start=${from.lngLat()}&end=${to.lngLat()}", orsHeaders())
                val feature = json.getJSONArray("features").getJSONObject(0)
                val summary = feature.getJSONObject("properties").getJSONObject("summary")
                RouteInfo(
                    points = feature.getJSONObject("geometry").getJSONArray("coordinates").toLatLngs(),
                    distanceKm = summary.optDouble("distance") / 1000,
                    durationMin = summary.optDouble("duration") / 60
                )
            } else {
                val json = get("$osrmBaseUrl/route/v1/driving/${from.lngLat()};${to.lngLat()}?overview=full&geometries=geojson")
                val r = json.getJSONArray("routes").getJSONObject(0)
                RouteInfo(
                    points = r.getJSONObject("geometry").getJSONArray("coordinates").toLatLngs(),
                    distanceKm = r.getDouble("distance") / 1000,
                    durationMin = r.getDouble("duration") / 60
                )
            }
        }.getOrNull()
    }

    override suspend fun roadDistancesKm(from: LatLng, destinations: List<LatLng>): List<Double?>? = withContext(Dispatchers.IO) {
        if (!enabled || destinations.isEmpty()) return@withContext null
        runCatching {
            if (useOrs) {
                val locations = JSONArray().apply {
                    put(JSONArray().put(from.longitude).put(from.latitude))
                    destinations.forEach { put(JSONArray().put(it.longitude).put(it.latitude)) }
                }
                val body = JSONObject()
                    .put("locations", locations)
                    .put("sources", JSONArray().put(0))
                    .put("destinations", JSONArray().apply { destinations.indices.forEach { put(it + 1) } })
                    .put("metrics", JSONArray().put("distance"))
                    .put("units", "km")
                val row = post("$orsBaseUrl/matrix/driving-car", body, orsHeaders()).getJSONArray("distances").getJSONArray(0)
                List(destinations.size) { i -> row.optDouble(i).takeUnless { it.isNaN() } }
            } else {
                val coords = (listOf(from) + destinations).joinToString(";") { it.lngLat() }
                val row = get("$osrmBaseUrl/table/v1/driving/$coords?sources=0&annotations=distance")
                    .getJSONArray("distances").getJSONArray(0)
                List(destinations.size) { i -> row.optDouble(i + 1).takeUnless { it.isNaN() }?.div(1000) }
            }
        }.getOrNull()
    }

    private fun LatLng.lngLat() = "$longitude,$latitude"

    private fun JSONArray.toLatLngs(): List<LatLng> = List(length()) { i ->
        val c = getJSONArray(i)
        LatLng(c.getDouble(1), c.getDouble(0))
    }

    private fun orsHeaders() = mapOf("Authorization" to orsKey)

    private fun get(url: String, headers: Map<String, String> = emptyMap()): JSONObject = request(url, "GET", null, headers)

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): JSONObject =
        request(url, "POST", body.toString(), headers + ("Content-Type" to "application/json"))

    private fun request(url: String, method: String, body: String?, headers: Map<String, String>): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs.coerceAtMost(10_000)
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", "ParkOver-Android")
            // ORS directions only serves application/geo+json and answers 406 to a plain JSON Accept.
            setRequestProperty("Accept", "application/json, application/geo+json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        try {
            if (conn.responseCode !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
                android.util.Log.w("RoutingRepository", "$method ${url.substringBefore('?')} -> HTTP ${conn.responseCode}: $detail")
                error("HTTP ${conn.responseCode}")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}

fun formatRoadDistance(km: Double): String =
    if (km < 1) "${(km * 1000).toInt()} m" else String.format("%.1f km", km)

fun formatDriveTime(minutes: Double): String {
    val m = minutes.toInt().coerceAtLeast(1)
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

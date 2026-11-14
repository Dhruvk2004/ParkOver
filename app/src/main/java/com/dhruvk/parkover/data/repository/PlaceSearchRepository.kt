package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.SearchResult
import com.dhruvk.parkover.di.PlaceSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Rough bounding box of India (minLon, minLat, maxLon, maxLat).
private const val INDIA_BBOX = "68.1,6.5,97.4,35.7"

/**
 * Free place search with Photon (komoot's OpenStreetMap search). The public server is fair-use:
 * keep requests debounced, and self-host Photon if traffic grows.
 */
class PhotonPlaceSearch(private val baseUrl: String = "https://photon.komoot.io") : PlaceSearchService {

    override suspend fun search(query: String, near: LatLng): List<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/api/?q=$q&lat=${near.latitude}&lon=${near.longitude}" +
                "&limit=6&lang=en&bbox=$INDIA_BBOX"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "ParkOver-Android")
            }
            val json = try {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
            val features = json.getJSONArray("features")
            List(features.length()) { i ->
                val f = features.getJSONObject(i)
                val p = f.getJSONObject("properties")
                val c = f.getJSONObject("geometry").getJSONArray("coordinates")
                val name = p.optString("name").ifBlank { p.optString("street") }
                val address = listOf("street", "district", "city", "state")
                    .map { p.optString(it) }
                    .filter { it.isNotBlank() && it != name }
                    .distinct()
                    .joinToString(", ")
                SearchResult(name, address, c.getDouble(1), c.getDouble(0))
            }.filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}

package com.dhruvk.parkover.data

import com.dhruvk.parkover.data.repository.HttpRoutingService
import com.dhruvk.parkover.data.repository.formatDriveTime
import com.dhruvk.parkover.data.repository.formatRoadDistance
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng

/** Layer 1: routing HTTP parsing and failure handling, against a local fake server. */
class HttpRoutingServiceTest {

    private lateinit var server: MockWebServer
    private val from = LatLng(19.0522, 72.8977)
    private val to = LatLng(19.0868, 72.8890)

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun ors(allowDemo: Boolean = false, timeoutMs: Int = 15_000) = HttpRoutingService(
        orsKey = "test-key", allowDemoFallback = allowDemo,
        orsBaseUrl = server.url("/ors/v2").toString().trimEnd('/'),
        osrmBaseUrl = server.url("/osrm").toString().trimEnd('/'),
        timeoutMs = timeoutMs
    )

    private fun noKey(allowDemo: Boolean) = HttpRoutingService(
        orsKey = "", allowDemoFallback = allowDemo,
        orsBaseUrl = server.url("/ors/v2").toString().trimEnd('/'),
        osrmBaseUrl = server.url("/osrm").toString().trimEnd('/')
    )

    private val orsBody = """
        {"features":[{"geometry":{"coordinates":[[72.8977,19.0522],[72.8950,19.0700],[72.8890,19.0868]]},
          "properties":{"summary":{"distance":9680.0,"duration":660.0}}}]}
    """.trimIndent()

    @Test fun ROUTE_01_orsRouteIsParsedInOrderWithKmAndMinutes() = runTest {
        server.enqueue(MockResponse().setBody(orsBody))
        val route = ors().route(from, to)
        assertNotNull(route)
        assertEquals(3, route!!.points.size)
        assertEquals(19.0522, route.points.first().latitude, 1e-9) // lng,lat order flipped correctly
        assertEquals(9.68, route.distanceKm, 1e-9)
        assertEquals(11.0, route.durationMin, 1e-9)
    }

    @Test fun ROUTE_01_orsRequestSendsKeyAndAcceptsGeoJson() = runTest {
        server.enqueue(MockResponse().setBody(orsBody))
        ors().route(from, to)
        val req = server.takeRequest()
        assertEquals("test-key", req.getHeader("Authorization"))
        assertTrue("Accept was ${req.getHeader("Accept")}", req.getHeader("Accept")!!.contains("application/geo+json"))
        assertTrue(req.path!!.contains("start=72.8977,19.0522"))
    }

    @Test fun ROUTE_04_httpErrorsGiveNullNotCrash() = runTest {
        for (code in listOf(406, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("{\"error\":\"x\"}"))
            assertNull("HTTP $code", ors().route(from, to))
        }
    }

    @Test fun ROUTE_04_malformedJsonGivesNull() = runTest {
        server.enqueue(MockResponse().setBody("{\"features\":[]}"))
        assertNull(ors().route(from, to))
        server.enqueue(MockResponse().setBody("not json"))
        assertNull(ors().route(from, to))
    }

    @Test fun ROUTE_05_timeoutGivesNull() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertNull(ors(timeoutMs = 500).route(from, to))
    }

    @Test fun ROUTE_09_noKeyInReleaseMakesNoNetworkCalls() = runTest {
        assertNull(noKey(allowDemo = false).route(from, to))
        assertNull(noKey(allowDemo = false).roadDistancesKm(from, listOf(to)))
        assertEquals(0, server.requestCount)
    }

    @Test fun ROUTE_09_noKeyInDebugUsesOsrmDemo() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"routes":[{"geometry":{"coordinates":[[72.8977,19.0522],[72.8890,19.0868]]},"distance":5000,"duration":600}]}"""
        ))
        val route = noKey(allowDemo = true).route(from, to)
        assertEquals(5.0, route!!.distanceKm, 1e-9)
        assertTrue(server.takeRequest().path!!.startsWith("/osrm/route/v1/driving/"))
    }

    @Test fun ROUTE_10_matrixReturnsOneDistancePerDestinationWithNullGaps() = runTest {
        server.enqueue(MockResponse().setBody("""{"distances":[[1.5,null,7.25]]}"""))
        val km = ors().roadDistancesKm(from, listOf(to, to, to))
        assertEquals(listOf(1.5, null, 7.25), km)
        assertEquals(1, server.requestCount) // one matrix call, not one per spot
    }

    @Test fun formatsDistanceAndTime() {
        assertEquals("850 m", formatRoadDistance(0.85))
        assertEquals("9.7 km", formatRoadDistance(9.68))
        assertEquals("1 min", formatDriveTime(0.2))
        assertEquals("11 min", formatDriveTime(11.4))
        assertEquals("1 h 5 min", formatDriveTime(65.0))
    }
}

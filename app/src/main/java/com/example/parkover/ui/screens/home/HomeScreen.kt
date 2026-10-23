package com.example.parkover.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.parkover.R
import com.example.parkover.data.model.AvailabilityStatus
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.data.model.SearchResult
import com.example.parkover.data.repository.ApiResult
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.example.parkover.ui.theme.WarningOrange
import com.example.parkover.viewmodels.ParkingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

@Composable
fun HomeScreen(
    parkingViewModel: ParkingViewModel,
    onParkingSpotClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val parkingSpotsResult = parkingViewModel.parkingSpots.collectAsStateWithLifecycle().value

    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    val searchResults = remember { mutableStateListOf<SearchResult>() }
    var destinationLocation by remember { mutableStateOf<LatLng?>(null) }

    // Bottom sheet state
    var selectedSpotForSheet by remember { mutableStateOf<ParkingSpot?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Route state
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDistance by remember { mutableStateOf("") }

    // Default location (Delhi)
    val defaultLocation = LatLng(28.6139, 77.2090)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 15f)
    }

    // Places client
    val placesClient = remember {
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_key))
        }
        Places.createClient(context)
    }
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }

    val apiKey = context.getString(R.string.google_maps_key)

    // Get parking spots
    val parkingSpots: List<ParkingSpot> = when (parkingSpotsResult) {
        is ApiResult.Success<*> -> (parkingSpotsResult as ApiResult.Success<List<ParkingSpot>>).data
        else -> emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapClick = {
                showSearchResults = false
                if (showBottomSheet) {
                    showBottomSheet = false
                    routePoints = emptyList()
                }
            }
        ) {
            // Current location marker
            Marker(
                state = MarkerState(position = defaultLocation),
                title = "Your Location",
                icon = createCurrentLocationIcon()
            )

            // Destination marker (from search)
            destinationLocation?.let { dest ->
                Marker(
                    state = MarkerState(position = dest),
                    title = "Destination",
                    icon = createDestinationMarkerIcon()
                )
            }

            // Route polyline
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    color = androidx.compose.ui.graphics.Color(0xFF613EEA),
                    width = 14f,
                    geodesic = true
                )
            }

            // Parking spot markers
            parkingSpots.forEach { spot ->
                val markerColor = when (spot.getAvailabilityStatus()) {
                    AvailabilityStatus.FULL -> AndroidColor.RED
                    AvailabilityStatus.LIMITED -> AndroidColor.parseColor("#FFA500")
                    AvailabilityStatus.AVAILABLE -> AndroidColor.parseColor("#613EEA")
                }

                Marker(
                    state = MarkerState(position = LatLng(spot.latitude, spot.longitude)),
                    title = spot.name,
                    snippet = "₹${spot.pricePerHourFourWheeler.toInt()}/hr • ${spot.getTotalAvailableSpots()} spots",
                    icon = createParkingMarkerIcon(markerColor),
                    onClick = {
                        parkingViewModel.selectParkingSpot(spot)
                        selectedSpotForSheet = spot
                        showBottomSheet = true

                        // Animate camera to show both points and draw route
                        val spotLatLng = LatLng(spot.latitude, spot.longitude)
                        scope.launch {
                            // Fit both points in view
                            val bounds = LatLngBounds.Builder()
                                .include(defaultLocation)
                                .include(spotLatLng)
                                .build()
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngBounds(bounds, 120),
                                durationMs = 800
                            )

                            // Fetch route
                            val route = fetchDirectionsRoute(
                                origin = defaultLocation,
                                destination = spotLatLng,
                                apiKey = apiKey
                            )
                            routePoints = route.first
                            routeDistance = route.second
                        }
                        true
                    }
                )
            }
        }

        // Top bar with search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            if (query.length >= 2) {
                                scope.launch {
                                    delay(300)
                                    searchPlaces(placesClient, query, sessionToken) { results ->
                                        searchResults.clear()
                                        searchResults.addAll(results)
                                        showSearchResults = results.isNotEmpty()
                                    }
                                }
                            } else {
                                showSearchResults = false
                            }
                        },
                        placeholder = { Text("Search parking spots...", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; showSearchResults = false }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val user = FirebaseAuth.getInstance().currentUser
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(user?.photoUrl ?: R.drawable.ic_default_profile)
                        .crossfade(true)
                        .transformations(CircleCropTransformation())
                        .build(),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() }
                )
            }

            if (showSearchResults) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        items(searchResults.toList()) { result ->
                            SearchResultItem(result) {
                                searchQuery = result.name
                                showSearchResults = false
                                result.placeId?.let { placeId ->
                                    fetchPlaceAndMove(placesClient, placeId) { latLng ->
                                        destinationLocation = latLng
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                                            )
                                        }
                                    }
                                }
                                sessionToken = AutocompleteSessionToken.newInstance()
                            }
                        }
                    }
                }
            }
        }

        // Loading
        if (parkingSpotsResult is ApiResult.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Purple
            )
        }

        // FAB
        FloatingActionButton(
            onClick = {
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(defaultLocation, 16f))
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (showBottomSheet) 280.dp else 100.dp),
            containerColor = Color.White,
            contentColor = Purple
        ) {
            Icon(Icons.Default.MyLocation, "My Location")
        }

        // Bottom Sheet for parking spot preview
        AnimatedVisibility(
            visible = showBottomSheet && selectedSpotForSheet != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedSpotForSheet?.let { spot ->
                ParkingSpotBottomSheet(
                    spot = spot,
                    distance = routeDistance,
                    onViewDetails = {
                        onParkingSpotClick(spot.id)
                    },
                    onDismiss = {
                        showBottomSheet = false
                        routePoints = emptyList()
                    }
                )
            }
        }
    }
}

@Composable
private fun ParkingSpotBottomSheet(
    spot: ParkingSpot,
    distance: String,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE0E0E0))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Image
                val imageUrl = spot.images.firstOrNull()
                AsyncImage(
                    model = imageUrl,
                    contentDescription = spot.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spot.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = WarningOrange)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${String.format("%.1f", spot.rating)}", fontSize = 13.sp, color = TextPrimary)
                        if (distance.isNotEmpty()) {
                            Text(" • $distance", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = spot.address,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price + availability row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${spot.pricePerHourFourWheeler.toInt()}/hr",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Purple
                )
                val availColor = when (spot.getAvailabilityStatus()) {
                    AvailabilityStatus.AVAILABLE -> Color(0xFF4CAF50)
                    AvailabilityStatus.LIMITED -> WarningOrange
                    AvailabilityStatus.FULL -> Color.Red
                }
                Text(
                    text = "${spot.getTotalAvailableSpots()} spots available",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = availColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // View Details button
            Button(
                onClick = onViewDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text("View Details", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.LocationOn, null, tint = Purple, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(result.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(result.address, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// --- Route fetching ---

private suspend fun fetchDirectionsRoute(
    origin: LatLng,
    destination: LatLng,
    apiKey: String
): Pair<List<LatLng>, String> = withContext(Dispatchers.IO) {
    try {
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving&key=$apiKey"

        val response = URL(url).readText()
        val json = JSONObject(response)

        if (json.getString("status") == "OK") {
            val routes = json.getJSONArray("routes")
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val legs = route.getJSONArray("legs")
                val distance = if (legs.length() > 0) {
                    legs.getJSONObject(0).getJSONObject("distance").getString("text")
                } else ""

                val encoded = route.getJSONObject("overview_polyline").getString("points")
                return@withContext Pair(decodePolyline(encoded), distance)
            }
        }
        Pair(emptyList(), "")
    } catch (e: Exception) {
        // Fallback: straight line
        Pair(listOf(origin, destination), "")
    }
}

private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int; var shift = 0; var result = 0
        do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        shift = 0; result = 0
        do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        poly.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return poly
}

// --- Marker icons ---

private fun createParkingMarkerIcon(color: Int): BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val shadowPaint = Paint().apply { this.color = AndroidColor.argb(40, 0, 0, 0); isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2f + 4f, size / 2.8f, shadowPaint)
    val paint = Paint().apply { this.color = color; isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, paint)
    val borderPaint = Paint().apply { this.color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, borderPaint)
    val textPaint = Paint().apply { this.color = AndroidColor.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER; isFakeBoldText = true; isAntiAlias = true }
    canvas.drawText("P", size / 2f, size / 2f + 12f, textPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createCurrentLocationIcon(): BitmapDescriptor {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint().apply { color = AndroidColor.parseColor("#4D613EEA"); isAntiAlias = true })
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, Paint().apply { color = AndroidColor.parseColor("#613EEA"); isAntiAlias = true })
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, Paint().apply { color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true })
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createDestinationMarkerIcon(): BitmapDescriptor {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pinPaint = Paint().apply { color = AndroidColor.parseColor("#E53935"); isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2.5f, size / 3f, pinPaint)
    canvas.drawCircle(size / 2f, size / 2.5f, size / 7f, Paint().apply { color = AndroidColor.WHITE; isAntiAlias = true })
    val path = android.graphics.Path().apply { moveTo(size / 2f - 10f, size / 1.8f); lineTo(size / 2f + 10f, size / 1.8f); lineTo(size / 2f, size.toFloat() - 4f); close() }
    canvas.drawPath(path, pinPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// --- Places helpers ---

private fun searchPlaces(placesClient: PlacesClient, query: String, sessionToken: AutocompleteSessionToken, onResults: (List<SearchResult>) -> Unit) {
    placesClient.findAutocompletePredictions(FindAutocompletePredictionsRequest.builder().setQuery(query).setSessionToken(sessionToken).build())
        .addOnSuccessListener { response -> onResults(response.autocompletePredictions.map { SearchResult(it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString(), 0.0, 0.0, it.placeId) }) }
        .addOnFailureListener { onResults(emptyList()) }
}

private fun fetchPlaceAndMove(placesClient: PlacesClient, placeId: String, onLocation: (LatLng) -> Unit) {
    placesClient.fetchPlace(FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.LAT_LNG)))
        .addOnSuccessListener { it.place.latLng?.let(onLocation) }
}

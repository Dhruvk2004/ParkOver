package com.example.parkover.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.parkover.viewmodels.ParkingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
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
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            onMapClick = { showSearchResults = false }
        ) {
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
                        onParkingSpotClick(spot.id)
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
            // Search bar
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
                                    searchPlaces(
                                        placesClient = placesClient,
                                        query = query,
                                        sessionToken = sessionToken,
                                        onResults = { results ->
                                            searchResults.clear()
                                            searchResults.addAll(results)
                                            showSearchResults = results.isNotEmpty()
                                        }
                                    )
                                }
                            } else {
                                showSearchResults = false
                            }
                        },
                        placeholder = { Text("Search parking spots...", color = TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    showSearchResults = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
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

                // Profile avatar
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

            // Search results dropdown
            if (showSearchResults) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        items(searchResults.toList()) { result ->
                            SearchResultItem(
                                result = result,
                                onClick = {
                                    searchQuery = result.name
                                    showSearchResults = false
                                    result.placeId?.let { placeId ->
                                        fetchPlaceAndMove(
                                            placesClient = placesClient,
                                            placeId = placeId,
                                            onLocation = { latLng ->
                                                scope.launch {
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    sessionToken = AutocompleteSessionToken.newInstance()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Loading indicator
        if (parkingSpotsResult is ApiResult.Loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Purple
            )
        }

        // FAB for current location
        FloatingActionButton(
            onClick = {
                scope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(defaultLocation, 16f)
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 100.dp),
            containerColor = Color.White,
            contentColor = Purple
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My Location")
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = Purple,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = result.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = result.address,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

private fun createParkingMarkerIcon(color: Int): BitmapDescriptor {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

    val borderPaint = Paint().apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 3f, borderPaint)

    val textPaint = Paint().apply {
        this.color = AndroidColor.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText("P", size / 2f, size / 2f + 8f, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun searchPlaces(
    placesClient: PlacesClient,
    query: String,
    sessionToken: AutocompleteSessionToken,
    onResults: (List<SearchResult>) -> Unit
) {
    val request = FindAutocompletePredictionsRequest.builder()
        .setQuery(query)
        .setSessionToken(sessionToken)
        .build()

    placesClient.findAutocompletePredictions(request)
        .addOnSuccessListener { response ->
            val results = response.autocompletePredictions.map { prediction ->
                SearchResult(
                    name = prediction.getPrimaryText(null).toString(),
                    address = prediction.getSecondaryText(null).toString(),
                    latitude = 0.0,
                    longitude = 0.0,
                    placeId = prediction.placeId
                )
            }
            onResults(results)
        }
        .addOnFailureListener {
            onResults(emptyList())
        }
}

private fun fetchPlaceAndMove(
    placesClient: PlacesClient,
    placeId: String,
    onLocation: (LatLng) -> Unit
) {
    val placeFields = listOf(Place.Field.LAT_LNG)
    val request = FetchPlaceRequest.newInstance(placeId, placeFields)

    placesClient.fetchPlace(request)
        .addOnSuccessListener { response ->
            response.place.latLng?.let { onLocation(it) }
        }
}

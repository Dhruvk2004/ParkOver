package com.example.parkover.ui.fragments.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.example.parkover.R
import com.example.parkover.data.model.SearchResult
import com.example.parkover.databinding.FragmentHomeBinding
import com.example.parkover.ui.adapters.SearchResultAdapter
import com.example.parkover.utils.LocationHelper
import com.example.parkover.utils.showToast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var locationHelper: LocationHelper
    private lateinit var searchAdapter: SearchResultAdapter
    private lateinit var placesClient: PlacesClient
    
    private var googleMap: GoogleMap? = null
    private var currentLocationMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private var parkingMarkers: MutableList<Marker> = mutableListOf()
    
    private var currentLocation: LatLng? = null
    private var selectedLocation: LatLng? = null
    private var selectedLocationName: String = ""
    private var selectedLocationAddress: String = ""
    private var selectedParkingSpot: ParkingSpotData? = null
    private var currentRouteDistance: String = ""
    
    private var searchJob: Job? = null
    private var sessionToken: AutocompleteSessionToken? = null
    
    // Location details
    private var isLocationDetailsExpanded = false
    private var currentPlaceName: String = "Current Location"
    private var currentFullAddress: String = ""

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            getCurrentLocation()
        } else {
            context?.showToast("Location permission required")
            currentLocation = LatLng(28.6139, 77.2090)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f))
            addCurrentLocationMarker()
            showNearbyParkingSpots(currentLocation!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationHelper = LocationHelper(requireContext())
        
        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(requireContext())
        sessionToken = AutocompleteSessionToken.newInstance()

        setupMap()
        setupSearchAdapter()
        setupUI()
        loadUserProfile()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_view) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Apply custom dark style
        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
            )
            if (!success) {
                Log.e("HomeFragment", "Style parsing failed")
            }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Can't find style", e)
        }
        
        // Enable all gestures
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
            isScrollGesturesEnabledDuringRotateOrZoom = true
        }
        
        // Set default location
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(28.6139, 77.2090), 15f))
        
        // Check permission and get location
        checkLocationPermission()
        
        // Marker click listener for parking spots
        map.setOnMarkerClickListener { marker ->
            val parkingData = marker.tag as? ParkingSpotData
            if (parkingData != null) {
                selectedParkingSpot = parkingData
                selectedLocationName = parkingData.name
                selectedLocationAddress = parkingData.address
                selectedLocation = marker.position
                
                // Draw route to parking spot
                drawRouteToParking(marker.position)
                true
            } else {
                false
            }
        }
        
        // Map click to dismiss suggestions
        map.setOnMapClickListener {
            hideSearchResults()
            hideKeyboard()
        }
    }

    private fun setupSearchAdapter() {
        searchAdapter = SearchResultAdapter { result ->
            selectSearchResult(result)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }


    private fun setupUI() {
        binding.fabMyLocation.setOnClickListener {
            currentLocation?.let { location ->
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 17f))
            } ?: getCurrentLocation()
        }

        binding.btnVoiceSearch.setOnClickListener {
            context?.showToast("Voice search coming soon")
        }

        binding.ivProfile.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            clearRoute()
            hideSearchResults()
        }

        binding.btnShowDetails.setOnClickListener {
            showParkingDetailsBottomSheet()
        }

        // Location dropdown click handler
        binding.locationClickArea.setOnClickListener {
            toggleLocationDetails()
        }
        
        binding.ivDropdown.setOnClickListener {
            toggleLocationDetails()
        }

        // Search text watcher with Places API
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                binding.btnClear.isVisible = query.isNotEmpty()

                searchJob?.cancel()
                if (query.length >= 2) {
                    searchJob = lifecycleScope.launch {
                        delay(300)
                        searchPlaces(query)
                    }
                } else {
                    hideSearchResults()
                }
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString() ?: ""
                if (query.isNotEmpty()) {
                    searchPlaces(query)
                }
                hideKeyboard()
                true
            } else false
        }
        
        // Focus change listener to show/hide suggestions
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // Delay hiding to allow click on suggestion
                lifecycleScope.launch {
                    delay(200)
                    if (!binding.etSearch.hasFocus()) {
                        hideSearchResults()
                    }
                }
            }
        }
    }
    
    private fun toggleLocationDetails() {
        isLocationDetailsExpanded = !isLocationDetailsExpanded
        
        if (isLocationDetailsExpanded) {
            // Rotate dropdown arrow
            binding.ivDropdown.animate()
                .rotation(180f)
                .setDuration(300)
                .start()
            
            // Show location details card with animation
            binding.locationDetailsCard.apply {
                alpha = 0f
                visibility = View.VISIBLE
                translationY = -20f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start()
            }
        } else {
            // Rotate dropdown arrow back
            binding.ivDropdown.animate()
                .rotation(0f)
                .setDuration(300)
                .start()
            
            // Hide location details card with animation
            binding.locationDetailsCard.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(250)
                .withEndAction {
                    binding.locationDetailsCard.visibility = View.GONE
                }
                .start()
        }
    }

    private fun loadUserProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        
        // First try Firebase Auth photo
        user?.photoUrl?.let { photoUrl ->
            binding.ivProfile.load(photoUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_default_profile)
                error(R.drawable.ic_default_profile)
            }
        } ?: run {
            binding.ivProfile.setImageResource(R.drawable.ic_default_profile)
        }
        
        // Also check Firestore for profile photo URL
        user?.uid?.let { uid ->
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { document ->
                    document?.getString("profilePhotoUrl")?.let { url ->
                        if (url.isNotEmpty()) {
                            binding.ivProfile.load(url) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                                placeholder(R.drawable.ic_default_profile)
                                error(R.drawable.ic_default_profile)
                            }
                        }
                    }
                }
        }
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> getCurrentLocation()
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocation() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 16f))
                    addCurrentLocationMarker()
                    updateLocationInfo(it.latitude, it.longitude)
                    showNearbyParkingSpots(currentLocation!!)
                } ?: run {
                    currentLocation = LatLng(28.6139, 77.2090)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f))
                    addCurrentLocationMarker()
                    binding.tvLocationName.text = "Delhi"
                    binding.tvAddress.text = "India"
                    showNearbyParkingSpots(currentLocation!!)
                }
            } catch (e: Exception) {
                context?.showToast("Failed to get location")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun addCurrentLocationMarker() {
        currentLocation?.let { location ->
            currentLocationMarker?.remove()
            currentLocationMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(location)
                    .icon(createCurrentLocationIcon())
                    .title("Your Location")
                    .anchor(0.5f, 0.5f)
            )
        }
    }
    
    // Create purple dot for current location
    private fun createCurrentLocationIcon(): BitmapDescriptor {
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Outer circle (light purple with transparency)
        val outerPaint = android.graphics.Paint().apply {
            color = Color.parseColor("#4D613EEA") // 30% opacity purple
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, outerPaint)
        
        // Inner circle (solid purple)
        val innerPaint = android.graphics.Paint().apply {
            color = Color.parseColor("#613EEA")
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, innerPaint)
        
        // White border on inner circle
        val borderPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, borderPaint)
        
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun updateLocationInfo(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            val locationName = locationHelper.getLocationNameFromCoordinates(latitude, longitude)
            val shortAddress = locationHelper.getShortAddress(latitude, longitude)
            val fullAddress = locationHelper.getFullAddressFromLocation(latitude, longitude)
            
            currentPlaceName = locationName
            currentFullAddress = fullAddress
            
            binding.tvLocationName.text = locationName
            binding.tvAddress.text = shortAddress
            
            // Update details card
            binding.tvDetailPlaceName.text = locationName
            binding.tvDetailFullAddress.text = fullAddress
            binding.tvDetailCoordinates.text = String.format("%.6f, %.6f", latitude, longitude)
        }
    }


    private fun searchPlaces(query: String) {
        binding.progressBar.visibility = View.VISIBLE

        val bounds = currentLocation?.let {
            RectangularBounds.newInstance(
                LatLng(it.latitude - 0.5, it.longitude - 0.5),
                LatLng(it.latitude + 0.5, it.longitude + 0.5)
            )
        }

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(sessionToken)
            .apply { bounds?.let { setLocationBias(it) } }
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
                searchAdapter.submitList(results)
                if (results.isNotEmpty()) {
                    showSearchResults()
                }
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                context?.showToast("Search failed")
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun showSearchResults() {
        binding.searchResultsCard.visibility = View.VISIBLE
    }

    private fun hideSearchResults() {
        binding.searchResultsCard.visibility = View.GONE
    }

    private fun selectSearchResult(result: SearchResult) {
        // Immediately hide suggestions and clear focus
        hideSearchResults()
        binding.etSearch.setText(result.name)
        binding.etSearch.clearFocus()
        hideKeyboard()
        
        // Clear the adapter list
        searchAdapter.submitList(emptyList())

        // Fetch place details to get coordinates
        val placeId = result.placeId ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS)
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                place.latLng?.let { latLng ->
                    selectedLocation = latLng
                    selectedLocationName = place.name ?: result.name
                    selectedLocationAddress = place.address ?: result.address

                    // Move camera to selected location
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    
                    // Add destination marker
                    destinationMarker?.remove()
                    destinationMarker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .icon(getBitmapFromVector(R.drawable.ic_destination_marker))
                            .title(selectedLocationName)
                    )
                    
                    // Show nearby parking spots at this location
                    showNearbyParkingSpots(latLng)
                    
                    // Reset session token for next search
                    sessionToken = AutocompleteSessionToken.newInstance()
                }
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                context?.showToast("Failed to get location details")
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    // Show nearby parking spots (mock data for now - will be replaced with Firestore data)
    private fun showNearbyParkingSpots(center: LatLng) {
        // Clear existing parking markers
        parkingMarkers.forEach { it.remove() }
        parkingMarkers.clear()

        // Mock parking spots around the center location
        val mockParkingSpots = listOf(
            ParkingSpotData("P1", "City Center Parking", "123 Main Street", center.latitude + 0.003, center.longitude + 0.002, 50.0, 4.5f, 25),
            ParkingSpotData("P2", "Mall Parking Complex", "456 Shopping Ave", center.latitude - 0.002, center.longitude + 0.004, 40.0, 4.2f, 100),
            ParkingSpotData("P3", "Metro Station Parking", "789 Transit Road", center.latitude + 0.001, center.longitude - 0.003, 30.0, 4.0f, 50),
            ParkingSpotData("P4", "Office Tower Parking", "321 Business Blvd", center.latitude - 0.004, center.longitude - 0.001, 60.0, 4.7f, 75),
            ParkingSpotData("P5", "Hospital Parking", "555 Health Lane", center.latitude + 0.005, center.longitude + 0.001, 35.0, 3.8f, 40)
        )

        mockParkingSpots.forEach { spot ->
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(spot.latitude, spot.longitude))
                    .icon(createParkingMarkerIcon())
                    .title(spot.name)
                    .snippet("₹${spot.pricePerHour.toInt()}/hr • ${spot.availableSpots} spots")
            )
            marker?.tag = spot
            marker?.let { parkingMarkers.add(it) }
        }
    }


    private fun createParkingMarkerIcon(): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_parking_marker)
        drawable?.let {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
    }

    private fun getBitmapFromVector(vectorResId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(requireContext(), vectorResId)
        drawable?.let {
            val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }
        return BitmapDescriptorFactory.defaultMarker()
    }

    private fun drawRouteToParking(destination: LatLng) {
        val start = currentLocation ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        // Clear previous route
        routePolyline?.remove()
        
        // Use Google Directions API for real road route
        lifecycleScope.launch {
            try {
                val routePoints = getDirectionsRoute(start, destination)
                
                if (routePoints.isNotEmpty()) {
                    routePolyline = googleMap?.addPolyline(
                        PolylineOptions()
                            .addAll(routePoints)
                            .width(12f)
                            .color(Color.parseColor("#613EEA"))
                            .geodesic(true)
                    )
                    
                    // Zoom to show both points
                    val bounds = LatLngBounds.Builder()
                        .include(start)
                        .include(destination)
                        .build()
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                } else {
                    // Fallback to straight line if directions fail
                    routePolyline = googleMap?.addPolyline(
                        PolylineOptions()
                            .add(start, destination)
                            .width(10f)
                            .color(Color.parseColor("#613EEA"))
                            .geodesic(true)
                    )
                    
                    val bounds = LatLngBounds.Builder()
                        .include(start)
                        .include(destination)
                        .build()
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                }
                
                binding.btnShowDetails.visibility = View.VISIBLE
                
            } catch (e: Exception) {
                Log.e("HomeFragment", "Route error", e)
                // Fallback to straight line
                routePolyline = googleMap?.addPolyline(
                    PolylineOptions()
                        .add(start, destination)
                        .width(10f)
                        .color(Color.parseColor("#613EEA"))
                )
                binding.btnShowDetails.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    // Get route from Google Directions API
    private suspend fun getDirectionsRoute(origin: LatLng, destination: LatLng): List<LatLng> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = getString(R.string.google_maps_key)
                val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&mode=driving" +
                        "&key=$apiKey"
                
                val response = URL(url).readText()
                val jsonResponse = JSONObject(response)
                
                val status = jsonResponse.getString("status")
                if (status == "OK") {
                    val routes = jsonResponse.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val legs = route.getJSONArray("legs")
                        if (legs.length() > 0) {
                            val leg = legs.getJSONObject(0)
                            
                            // Get distance for display
                            val distance = leg.getJSONObject("distance")
                            currentRouteDistance = distance.getString("text")
                        }
                        
                        val overviewPolyline = route.getJSONObject("overview_polyline")
                        val encodedPath = overviewPolyline.getString("points")
                        return@withContext decodePolyline(encodedPath)
                    }
                }
                emptyList()
            } catch (e: Exception) {
                Log.e("HomeFragment", "Directions API error", e)
                emptyList()
            }
        }
    }
    
    // Decode polyline from Google Directions API
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun clearRoute() {
        routePolyline?.remove()
        routePolyline = null
        destinationMarker?.remove()
        destinationMarker = null
        selectedParkingSpot = null
        currentRouteDistance = ""
        binding.btnShowDetails.visibility = View.GONE
    }

    private fun showParkingDetailsBottomSheet() {
        val spot = selectedParkingSpot
        val bottomSheet = ParkingDetailsBottomSheet.newInstance(
            name = spot?.name ?: selectedLocationName,
            address = spot?.address ?: selectedLocationAddress,
            latitude = selectedLocation?.latitude ?: 0.0,
            longitude = selectedLocation?.longitude ?: 0.0,
            currentLat = currentLocation?.latitude ?: 0.0,
            currentLng = currentLocation?.longitude ?: 0.0,
            distance = currentRouteDistance
        )
        bottomSheet.show(childFragmentManager, "ParkingDetailsBottomSheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class ParkingSpotData(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val pricePerHour: Double,
    val rating: Float,
    val availableSpots: Int
)

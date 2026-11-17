package com.dhruvk.parkover.ui.screens.home

import com.dhruvk.parkover.ui.components.OfflineNotice
import com.dhruvk.parkover.ui.components.OfflineScreen
import com.dhruvk.parkover.ui.components.rememberDebouncedOffline
import com.dhruvk.parkover.ui.components.rememberIsOnline
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dhruvk.parkover.ui.TestTags
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dhruvk.parkover.data.model.AvailabilityStatus
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.SearchResult
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.repository.RouteInfo
import com.dhruvk.parkover.di.AppServices
import com.dhruvk.parkover.domain.SpotFilters
import com.dhruvk.parkover.data.repository.formatDriveTime
import com.dhruvk.parkover.data.repository.formatRoadDistance
import com.dhruvk.parkover.ui.components.AvailabilityBar
import com.dhruvk.parkover.ui.components.MapPin
import com.dhruvk.parkover.ui.components.ParkMap
import com.dhruvk.parkover.ui.components.ParkMapController
import com.dhruvk.parkover.ui.components.BrandGradient
import com.dhruvk.parkover.ui.components.ParkChip
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.components.availabilityColor
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.components.openDirections
import com.dhruvk.parkover.ui.screens.booking.isOpenNow
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleLight
import com.dhruvk.parkover.ui.theme.StarYellow
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.ui.theme.WarningOrange
import com.dhruvk.parkover.utils.Constants
import com.dhruvk.parkover.utils.LocationHelper
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.HomeViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel
import com.dhruvk.parkover.viewmodels.SpotFilter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

private const val NEARBY_RADIUS_KM = Constants.SEARCH_RADIUS_KM

private const val SPOT_RESULT_PREFIX = "spot:"

@OptIn(FlowPreview::class)
@Composable
fun HomeScreen(
    parkingViewModel: ParkingViewModel,
    homeViewModel: HomeViewModel,
    accountViewModel: AccountViewModel,
    onParkingSpotClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val spotsResult = parkingViewModel.parkingSpots.collectAsStateWithLifecycle().value
    val userLocation = homeViewModel.location.collectAsStateWithLifecycle().value
    val locating = homeViewModel.locating.collectAsStateWithLifecycle().value
    val locationServicesOn = homeViewModel.locationServicesOn.collectAsStateWithLifecycle().value
    val online by rememberIsOnline()
    val showOfflineNotice by rememberDebouncedOffline(online)
    val filters = homeViewModel.filters.collectAsStateWithLifecycle().value
    val savedIds = accountViewModel.savedIds.collectAsStateWithLifecycle().value

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<SearchResult>() }
    var showSearchResults by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<Pair<String, LatLng>?>(null) }
    var selectedSpotId by rememberSaveable { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<RouteInfo?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var routeRetry by remember { mutableStateOf(0) }
    var hasPermission by remember { mutableStateOf(AppServices.location(context).hasPermission()) }
    val mapController = remember { ParkMapController() }
    var mapReady by remember { mutableStateOf(false) }

    // ---- Location ----
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPermission = granted.values.any { it }
        homeViewModel.refreshLocation()
    }
    LaunchedEffect(Unit) {
        if (hasPermission) homeViewModel.refreshLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // Back from Android's location settings (or anywhere): if location was off, look again.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (hasPermission && !locationServicesOn) homeViewModel.refreshLocation()
    }
    LaunchedEffect(userLocation, mapReady) {
        if (mapReady && !userLocation.isFallback && !homeViewModel.hasCentredOnUser) {
            homeViewModel.hasCentredOnUser = true
            mapController.animateTo(userLocation.latLng, 14.0)
        }
    }

    // ---- Filtered spots ----
    val allSpots = (spotsResult as? ApiResult.Success)?.data.orEmpty()
    // Offline: the full illustrated state only while parkings have never loaded; once anything
    // is on screen, a drop only shows the small notice. Back online, a failed load retries itself.
    val showOfflineScreen = !online && allSpots.isEmpty() && spotsResult is ApiResult.Error
    LaunchedEffect(online) {
        if (online && parkingViewModel.parkingSpots.value is ApiResult.Error) parkingViewModel.loadParkingSpots()
    }
    val centre = destination?.second ?: userLocation.latLng
    val visibleSpots = remember(allSpots, filters, centre) {
        SpotFilters.apply(allSpots, filters, centre.latitude, centre.longitude, NEARBY_RADIUS_KM)
    }
    val pins = remember(visibleSpots) {
        visibleSpots.map { spot ->
            val status = spot.getAvailabilityStatus()
            MapPin(
                id = spot.id,
                position = LatLng(spot.latitude, spot.longitude),
                label = if (status == AvailabilityStatus.FULL) "Full" else formatRupees(spot.pricePerHourFourWheeler),
                status = status
            )
        }
    }
    val selectedSpot = allSpots.firstOrNull { it.id == selectedSpotId }

    // Route from the searched destination or a real GPS fix, never from the Mumbai fallback point.
    val routeOrigin = destination?.second ?: userLocation.latLng.takeUnless { userLocation.isFallback }

    fun selectSpot(spot: ParkingSpot) {
        // Tapping the same pin again after a failed route (e.g. it was offline) retries it.
        if (spot.id == selectedSpotId && route == null && !routeLoading) routeRetry++
        selectedSpotId = spot.id
        parkingViewModel.selectParkingSpot(spot)
    }

    // Re-runs when the spot changes or the origin changes (e.g. the GPS fix lands after the tap).
    LaunchedEffect(selectedSpotId, routeOrigin, mapReady, routeRetry) {
        route = null
        val spot = allSpots.firstOrNull { it.id == selectedSpotId } ?: return@LaunchedEffect
        val spotLatLng = LatLng(spot.latitude, spot.longitude)
        val from = routeOrigin
        if (!mapReady) return@LaunchedEffect
        if (from == null) {
            mapController.animateTo(spotLatLng, 15.0)
            return@LaunchedEffect
        }
        with(density) { mapController.fitBounds(from, spotLatLng, extraPx = 56.dp.roundToPx(), extraBottomPx = 236.dp.roundToPx()) }
        routeLoading = true
        try {
            route = AppServices.routing.route(from, spotLatLng)?.also { r ->
                with(density) { mapController.fitPoints(r.points + from + spotLatLng, extraPx = 56.dp.roundToPx(), extraBottomPx = 236.dp.roundToPx()) }
            }
        } finally {
            routeLoading = false
        }
    }

    // ---- Search: our own parkings first, then places from Photon (debounced) ----
    val latestSpots by rememberUpdatedState(allSpots)
    val latestCentre by rememberUpdatedState(centre)
    LaunchedEffect(Unit) {
        // Re-run when the parking list arrives too, so a query typed before it loaded still
        // finds our own parkings.
        snapshotFlow { searchQuery to latestSpots.size }
            .debounce(350)
            .distinctUntilChanged()
            .filter { (q, _) -> q.length >= 2 && destination?.first != q }
            .collect { (query, _) ->
                val ownSpots = latestSpots
                    .filter { it.name.contains(query, ignoreCase = true) || it.address.contains(query, ignoreCase = true) }
                    .take(3)
                    .map { SearchResult(it.name, it.address, it.latitude, it.longitude, SPOT_RESULT_PREFIX + it.id) }
                // Our own parkings show straight away; places follow when (and if) search answers.
                searchResults.clear()
                searchResults.addAll(ownSpots)
                showSearchResults = searchResults.isNotEmpty()
                val places = try {
                    AppServices.placeSearch.search(query, latestCentre)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList() // place search down: keep our own results, never crash Home
                }
                searchResults.clear()
                searchResults.addAll(ownSpots + places)
                showSearchResults = searchResults.isNotEmpty()
            }
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { searchQuery = it }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ParkMap(
            controller = mapController,
            initialTarget = homeViewModel.lastCamera?.first ?: userLocation.latLng,
            initialZoom = homeViewModel.lastCamera?.second ?: 13.5,
            pins = pins,
            selectedPinId = selectedSpotId,
            userLocation = userLocation.latLng.takeUnless { userLocation.isFallback },
            destination = destination?.second,
            route = route?.points,
            topPadding = 180.dp,
            bottomPadding = BottomNavReservedHeight,
            onPinClick = { id -> allSpots.firstOrNull { it.id == id }?.let(::selectSpot) },
            onMapClick = {
                showSearchResults = false
                focusManager.clearFocus()
                selectedSpotId = null
                route = null
            },
            onCameraIdle = { target, zoom -> homeViewModel.lastCamera = target to zoom },
            onMapReady = { mapReady = true },
            modifier = Modifier.fillMaxSize()
        )

        // ---- Top overlay: location header, search, filters ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocationHeader(
                areaName = destination?.first ?: userLocation.areaName,
                subtitle = when {
                    destination != null -> "Parking near your destination"
                    userLocation.isFallback && (!hasPermission || !locationServicesOn) -> "Location off · showing Mumbai"
                    else -> "Parking near you"
                },
                onProfileClick = onProfileClick,
                onLocationClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            )

            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    if (it.length < 2) showSearchResults = false
                },
                onClear = {
                    searchQuery = ""
                    showSearchResults = false
                    destination = null
                },
                onVoice = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Where are you going?")
                    runCatching { voiceLauncher.launch(intent) }
                }
            )

            if (showSearchResults) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 6.dp) {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(searchResults.toList()) { result ->
                            SearchResultItem(result) {
                                showSearchResults = false
                                focusManager.clearFocus()
                                searchQuery = result.name
                                val spot = result.placeId
                                    ?.takeIf { it.startsWith(SPOT_RESULT_PREFIX) }
                                    ?.removePrefix(SPOT_RESULT_PREFIX)
                                    ?.let { id -> allSpots.firstOrNull { it.id == id } }
                                if (spot != null) {
                                    selectSpot(spot)
                                } else {
                                    val latLng = LatLng(result.latitude, result.longitude)
                                    destination = result.name to latLng
                                    selectedSpotId = null
                                    route = null
                                    mapController.animateTo(latLng, 14.5)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SpotFilter.entries) { filter ->
                        ParkChip(
                            label = filter.label,
                            selected = filter in filters,
                            icon = when (filter) {
                                SpotFilter.NEARBY -> Icons.Rounded.NearMe
                                SpotFilter.EV -> Icons.Rounded.EvStation
                                else -> null
                            },
                            onClick = { homeViewModel.toggleFilter(filter) },
                            modifier = Modifier.shadow(2.dp, RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            // Status banners
            when {
                showOfflineNotice && allSpots.isNotEmpty() -> OfflineNotice()
                spotsResult is ApiResult.Error -> StatusBanner(
                    text = "Couldn't load parking. Check your connection.",
                    action = "Retry",
                    onAction = { parkingViewModel.loadParkingSpots() }
                )
                spotsResult is ApiResult.Success && visibleSpots.isEmpty() -> StatusBanner(
                    text = if (filters.isEmpty()) "No parking here yet." else "No parking matches these filters here.",
                    action = if (filters.isEmpty()) null else "Clear filters",
                    onAction = { filters.forEach(homeViewModel::toggleFilter) }
                )
            }
        }

        if (spotsResult is ApiResult.Loading) {
            CircularProgressIndicator(color = Purple, modifier = Modifier.align(Alignment.Center))
        }

        // ---- My location ----
        Surface(
            onClick = {
                if (!hasPermission) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else if (!AppServices.location(context).isLocationEnabled()) {
                    // Permission is fine but location is switched off: send them to the switch.
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } else {
                    homeViewModel.refreshLocation()
                    destination = null
                    searchQuery = ""
                    mapController.animateTo(userLocation.latLng, 15.0, 600)
                }
            },
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DividerColor),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (selectedSpot != null) BottomNavReservedHeight + 236.dp else BottomNavReservedHeight + 8.dp)
                .size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (locating) CircularProgressIndicator(color = Purple, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(Icons.Rounded.MyLocation, contentDescription = "Centre on my location", tint = Purple)
            }
        }

        // ---- Selected spot sheet ----
        AnimatedVisibility(
            visible = selectedSpot != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomNavReservedHeight - 12.dp)
        ) {
            selectedSpot?.let { spot ->
                val currentRoute = route
                // Road distance once the route arrives; straight-line only as a labelled fallback.
                val distance = when {
                    currentRoute != null -> formatRoadDistance(currentRoute.distanceKm)
                    routeLoading -> "Finding route…"
                    routeOrigin == null -> if (locating) "Finding your location…" else "Turn on location for distance"
                    else -> LocationHelper.formatDistance(
                        LocationHelper.calculateDistance(centre.latitude, centre.longitude, spot.latitude, spot.longitude)
                    ) + " straight-line"
                }
                SpotSheet(
                    spot = spot,
                    distance = distance,
                    duration = currentRoute?.let { formatDriveTime(it.durationMin) }.orEmpty(),
                    isSaved = spot.id in savedIds,
                    onToggleSave = { accountViewModel.toggleSaved(spot, spot.id in savedIds) },
                    onRoute = { openDirections(context, spot.latitude, spot.longitude, spot.name) },
                    onDetails = { onParkingSpotClick(spot.id) },
                    onDismiss = {
                        selectedSpotId = null
                        route = null
                    }
                )
            }
        }

        // Opened with no connection and nothing to show yet.
        AnimatedVisibility(visible = showOfflineScreen, enter = fadeIn(), exit = fadeOut()) {
            OfflineScreen(
                onRetry = { parkingViewModel.loadParkingSpots() },
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

// ---------------- UI pieces ----------------

@Composable
private fun LocationHeader(areaName: String, subtitle: String, onProfileClick: () -> Unit, onLocationClick: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = CardDark)
            .clip(RoundedCornerShape(18.dp))
            .background(CardDark)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onLocationClick)
        ) {
            Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    areaName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PurpleLight, Purple)))
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            val photo = user?.photoUrl
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    initials(user?.displayName ?: user?.email ?: "P"),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun initials(name: String): String =
    name.split(" ", "@", ".").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "P" }

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, onVoice: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Where are you going?", style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = SolidColor(Purple),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.HOME_SEARCH)
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextSecondary) }
        } else {
            IconButton(onClick = onVoice) { Icon(Icons.Rounded.Mic, contentDescription = "Search by voice", tint = Purple) }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Purple, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(result.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.address, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusBanner(text: String, action: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .testTag(TestTags.HOME_STATUS_BANNER)
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier
            .weight(1f)
            .padding(vertical = 12.dp))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Purple, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun SpotSheet(
    spot: ParkingSpot,
    distance: String,
    duration: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onRoute: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val available = spot.getTotalAvailableSpots()
    val total = spot.getTotalSpots().coerceAtLeast(1)
    val fraction = available.toFloat() / total
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 16.dp,
        modifier = Modifier
            .testTag(TestTags.SPOT_SHEET)
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerColor)
                    .clickable(onClick = onDismiss)
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.LocalParking, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                    spot.images.firstOrNull()?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            spot.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save parking",
                            tint = Purple,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onToggleSave)
                                .padding(2.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
                        Text(
                            buildString {
                                append(" ${String.format("%.1f", spot.rating)} · $distance")
                                if (duration.isNotEmpty()) append(" · $duration")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.testTag(TestTags.SPOT_SHEET_META)
                        )
                    }
                    Text(
                        if (spot.operatingHours.is24Hours) "Open 24 hours"
                        else if (isOpenNow(spot)) "Open · closes ${spot.operatingHours.closeTime}"
                        else "Closed · opens ${spot.operatingHours.openTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOpenNow(spot)) SuccessGreen else WarningOrange
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvailabilityBar(fraction, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (available > 0) "$available free" else "Full",
                            style = MaterialTheme.typography.labelMedium,
                            color = availabilityColor(fraction)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(formatRupees(spot.pricePerHourFourWheeler), style = MaterialTheme.typography.titleLarge)
                    Text("per hour", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                SecondaryButton(text = "Route", icon = Icons.Rounded.Directions, onClick = onRoute, height = 46.dp, modifier = Modifier.weight(1f))
                PrimaryButton(text = "View details", onClick = onDetails, modifier = Modifier
                    .weight(1.3f)
                    .height(46.dp))
            }
        }
    }
}

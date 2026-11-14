package com.dhruvk.parkover.ui.screens.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dhruvk.parkover.data.model.AvailabilityStatus
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.di.AppServices
import com.dhruvk.parkover.data.repository.formatRoadDistance
import com.dhruvk.parkover.data.repository.SavedParking
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.BrandGradient
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.StarYellow
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.HomeViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

private data class SavedItem(val saved: SavedParking, val live: ParkingSpot?, val roadDistanceKm: Double?)

@Composable
fun SavedScreen(
    parkingViewModel: ParkingViewModel,
    accountViewModel: AccountViewModel,
    homeViewModel: HomeViewModel,
    onParkingClick: (String) -> Unit,
    onExplore: () -> Unit
) {
    val saved = accountViewModel.savedParkings.collectAsStateWithLifecycle().value
    val spots = (parkingViewModel.parkingSpots.collectAsStateWithLifecycle().value as? ApiResult.Success)?.data.orEmpty()
    val location = homeViewModel.location.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Road distance (not straight-line) to each saved parking, fetched in one matrix request.
    val roadKm = remember { mutableStateMapOf<String, Double>() }
    val liveSaved = remember(saved, spots) {
        saved.orEmpty().mapNotNull { entry -> spots.firstOrNull { it.id == entry.parkingId } }
    }
    LaunchedEffect(liveSaved.map { it.id }, location.latLng) {
        val distances = AppServices.routing.roadDistancesKm(
            location.latLng,
            liveSaved.map { LatLng(it.latitude, it.longitude) }
        ) ?: return@LaunchedEffect
        liveSaved.zip(distances).forEach { (spot, km) -> if (km != null) roadKm[spot.id] = km }
    }

    // Join each saved entry with the live spot so availability and price are current
    val items = saved.orEmpty().map { entry ->
        val live = spots.firstOrNull { it.id == entry.parkingId }
        SavedItem(saved = entry, live = live, roadDistanceKm = live?.let { roadKm[it.id] })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("Saved", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                if (items.isNotEmpty()) {
                    Text(
                        "${items.size} place${if (items.size == 1) "" else "s"} · live availability",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            when {
                saved == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Purple)
                }
                items.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = BottomNavReservedHeight),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Rounded.Bookmark,
                        title = "Nothing saved yet",
                        message = "Tap the bookmark on any parking to keep it here for next time."
                    ) {
                        PrimaryButton(text = "Explore the map", icon = Icons.Rounded.Map, onClick = onExplore)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomNavReservedHeight),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.saved.parkingId }) { index, item ->
                        SwipeToRemove(
                            onRemove = {
                                accountViewModel.removeSaved(item.saved.parkingId)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = "Removed ${item.saved.name}",
                                        actionLabel = "Undo",
                                        withDismissAction = true
                                    )
                                    if (result == SnackbarResult.ActionPerformed) item.live?.let(accountViewModel::restoreSaved)
                                }
                            }
                        ) {
                            if (index == 0) {
                                FeaturedSavedCard(item) { onParkingClick(item.saved.parkingId) }
                            } else {
                                SavedRow(item) { onParkingClick(item.saved.parkingId) }
                            }
                        }
                    }
                    item {
                        Text(
                            "Swipe a card left to remove it",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomNavReservedHeight)
        )
    }
}

@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ErrorRed)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.BookmarkRemove, contentDescription = "Remove", tint = Color.White)
            }
        }
    ) { content() }
}

@Composable
private fun AvailabilityBadge(live: ParkingSpot?) {
    if (live == null) return
    val free = live.getTotalAvailableSpots()
    when (live.getAvailabilityStatus()) {
        AvailabilityStatus.AVAILABLE -> StatusBadge("$free free", BadgeTone.Success)
        AvailabilityStatus.LIMITED -> StatusBadge("$free left", BadgeTone.Warning)
        AvailabilityStatus.FULL -> StatusBadge("Full", BadgeTone.Error)
    }
}

private fun subtitle(item: SavedItem): String = buildList {
    item.saved.address.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    item.roadDistanceKm?.let { add(formatRoadDistance(it) + " by road") }
}.joinToString(" · ")

@Composable
private fun FeaturedSavedCard(item: SavedItem, onClick: () -> Unit) {
    ParkCard(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.linearGradient(BrandGradient))
        ) {
            Icon(
                Icons.Rounded.LocalParking,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
            item.saved.imageUrl.takeIf { it.isNotBlank() }?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(modifier = Modifier.padding(10.dp)) { AvailabilityBadge(item.live) }
        }
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.saved.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.live?.let {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
                        Text(" ${String.format("%.1f", it.rating)} · ", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                }
            }
            item.live?.let { PriceColumn(it.pricePerHourFourWheeler) }
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem, onClick: () -> Unit) {
    ParkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(BrandGradient))
            ) {
                item.saved.imageUrl.takeIf { it.isNotBlank() }?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.saved.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
                Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AvailabilityBadge(item.live)
                item.live?.let {
                    Text("${formatRupees(it.pricePerHourFourWheeler)}/hr", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PriceColumn(price: Double) {
    Column(horizontalAlignment = Alignment.End) {
        Text(formatRupees(price), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text("per hour", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

package com.dhruvk.parkover.ui.screens.booking

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Roofing
import androidx.compose.material.icons.rounded.RoomService
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.ui.components.AvailabilityBar
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.BrandGradient
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.IconTile
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.SquareIconButton
import com.dhruvk.parkover.ui.components.SystemBarIcons
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.StarYellow
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.WarningOrange
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParkingDetailsScreen(
    parkingId: String,
    parkingViewModel: ParkingViewModel,
    accountViewModel: AccountViewModel,
    flowViewModel: BookingFlowViewModel,
    onBack: () -> Unit,
    onBookParking: () -> Unit
) {
    val context = LocalContext.current
    val spotsResult = parkingViewModel.parkingSpots.collectAsStateWithLifecycle().value
    val savedIds = accountViewModel.savedIds.collectAsStateWithLifecycle().value
    // Load by route argument so this works from the map, Saved, or a deep link
    val spot = (spotsResult as? ApiResult.Success)?.data?.firstOrNull { it.id == parkingId }

    LaunchedEffect(spot) { spot?.let(flowViewModel::updateSpot) }

    SystemBarIcons(lightIcons = true)

    if (spot == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            if (spotsResult is ApiResult.Loading) {
                CircularProgressIndicator(color = Purple)
            } else {
                EmptyState(
                    icon = Icons.Rounded.LocalParking,
                    title = "Parking not found",
                    message = "This parking may have been removed. Go back and pick another one."
                )
            }
            SquareIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
        return
    }

    val isSaved = spot.id in savedIds
    val totalAvailable = spot.getTotalAvailableSpots()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero carousel
            Box(modifier = Modifier.height(260.dp)) {
                val images = spot.images
                val pager = rememberPagerState { images.size.coerceAtLeast(1) }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(BrandGradient))
                    ) {
                        Icon(
                            Icons.Rounded.LocalParking,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .size(96.dp)
                                .align(Alignment.Center)
                        )
                        images.getOrNull(page)?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = spot.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SquareIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, bordered = false)
                    Spacer(Modifier.weight(1f))
                    SquareIconButton(
                        Icons.Rounded.Share, "Share", bordered = false,
                        onClick = {
                            val text = "${spot.name}, ${spot.address}\nhttps://maps.google.com/?q=${spot.latitude},${spot.longitude}"
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                    "Share parking"
                                )
                            )
                        }
                    )
                    SquareIconButton(
                        icon = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isSaved) "Remove from saved" else "Save parking",
                        tint = Purple,
                        bordered = false,
                        onClick = { accountViewModel.toggleSaved(spot, isSaved) }
                    )
                }
                if (images.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(images.size) { i ->
                            Box(
                                Modifier
                                    .height(4.dp)
                                    .width(if (i == pager.currentPage) 16.dp else 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f))
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .offset(y = (-16).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BackgroundLight)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(spot.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(spot.address, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(Icons.Rounded.Star, "${String.format("%.1f", spot.rating)} (${spot.reviewCount})", StarYellow)
                    val open = isOpenNow(spot)
                    InfoChip(
                        Icons.Rounded.Schedule,
                        if (spot.operatingHours.is24Hours) "Open 24h"
                        else "${spot.operatingHours.openTime}–${spot.operatingHours.closeTime}",
                        if (open) SuccessGreen else WarningOrange
                    )
                    if (spot.floors.isNotEmpty()) InfoChip(Icons.Rounded.Layers, "${spot.floors.size} floors", Purple)
                }

                ParkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Free now")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$totalAvailable of ${spot.getTotalSpots()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    VehicleRow(Icons.Rounded.TwoWheeler, "Bike", spot.availableSpotsTwoWheeler, spot.totalSpotsTwoWheeler, spot.pricePerHourTwoWheeler)
                    VehicleRow(Icons.Rounded.DirectionsCar, "Car", spot.availableSpotsFourWheeler, spot.totalSpotsFourWheeler, spot.pricePerHourFourWheeler)
                    VehicleRow(Icons.Rounded.LocalShipping, "Heavy", spot.availableSpotsHeavy, spot.totalSpotsHeavy, spot.pricePerHourHeavy)
                }

                if (spot.amenities.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("Amenities")
                        spot.amenities.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { amenity ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconTile(amenityIcon(amenity), size = 44.dp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            amenity,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 2
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                if (spot.floors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Floors")
                        ParkCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                            spot.floors.sortedBy { it.floorNumber }.forEachIndexed { index, floor ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        BookingRepository.floorLabel(floor.floorNumber),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Purple,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(floor.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    if (floor.priceMultiplier > 1.0) {
                                        Text(
                                            "${floor.priceMultiplier}× rate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarningOrange,
                                            modifier = Modifier.padding(end = 10.dp)
                                        )
                                    }
                                    Text(
                                        "${floor.availableSpots} free",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (floor.availableSpots > 0) SuccessGreen else TextSecondary
                                    )
                                }
                                if (index < spot.floors.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        BottomActionBar(
            summary = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatRupees(spot.pricePerHourFourWheeler), style = MaterialTheme.typography.titleLarge)
                    Text("/hr", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text("Car rate · + tax", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        ) {
            PrimaryButton(
                text = if (totalAvailable > 0) "Book a spot" else "Full right now",
                enabled = totalAvailable > 0 && spot.isActive,
                onClick = {
                    flowViewModel.initBooking(spot)
                    onBookParking()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

@Composable
private fun VehicleRow(icon: ImageVector, label: String, available: Int, total: Int, rate: Double) {
    if (total <= 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier
            .padding(start = 8.dp)
            .width(48.dp))
        AvailabilityBar(fraction = available.toFloat() / total, modifier = Modifier.weight(1f))
        Text(
            "$available",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(start = 10.dp)
                .width(36.dp)
        )
        Text("${formatRupees(rate)}/h", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

private fun amenityIcon(name: String): ImageVector = when (name) {
    "CCTV" -> Icons.Rounded.Videocam
    "Covered" -> Icons.Rounded.Roofing
    "EV Charging" -> Icons.Rounded.EvStation
    "Washroom" -> Icons.Rounded.Wc
    "24/7 Security" -> Icons.Rounded.Security
    "Wheelchair Access" -> Icons.Rounded.Accessible
    "Valet Parking" -> Icons.Rounded.RoomService
    "Car Wash" -> Icons.Rounded.LocalCarWash
    else -> Icons.Rounded.CheckCircle
}

fun isOpenNow(spot: ParkingSpot): Boolean {
    val hours = spot.operatingHours
    if (hours.is24Hours) return true
    val now = Calendar.getInstance()
    val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    fun parse(t: String) = t.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val open = parse(hours.openTime)
    val close = parse(hours.closeTime)
    return if (close > open) minutes in open until close else minutes >= open || minutes < close
}

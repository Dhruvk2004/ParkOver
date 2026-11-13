package com.dhruvk.parkover.ui.screens.booking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.R
import com.dhruvk.parkover.data.model.FloorData
import com.dhruvk.parkover.data.model.ParkingSlot
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.ui.components.BookingStepper
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.ParkChip
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleDark
import com.dhruvk.parkover.ui.theme.PurpleLight
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel

@Composable
fun SelectFloorScreen(
    flowViewModel: BookingFlowViewModel,
    parkingViewModel: ParkingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val data = flowViewModel.bookingData.collectAsStateWithLifecycle().value
    val floors = flowViewModel.floors.collectAsStateWithLifecycle().value
    val loading = flowViewModel.floorsLoading.collectAsStateWithLifecycle().value
    val spots = parkingViewModel.parkingSpots.collectAsStateWithLifecycle().value

    // Refresh with the latest live availability, then build the floor plan
    LaunchedEffect(data.parkingId, data.vehicleType) {
        (spots as? ApiResult.Success)?.data?.firstOrNull { it.id == data.parkingId }?.let(flowViewModel::updateSpot)
        if (floors.isEmpty()) flowViewModel.loadFloors()
    }

    var selectedFloorIndex by remember(floors) {
        mutableIntStateOf(floors.indexOfFirst { it.floorNumber == data.floorNumber && data.spotNumber.isNotEmpty() }
            .takeIf { it >= 0 } ?: floors.indexOfFirst { it.availableSpots > 0 }.coerceAtLeast(0))
    }
    val currentFloor = floors.getOrNull(selectedFloorIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        ParkTopBar(title = "Pick your spot", onBack = onBack)
        BookingStepper(step = 2, total = 5, label = "Spot", context = data.vehicleName)
        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
            floors.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.LocalParking,
                    title = "No floor plan",
                    message = "This parking hasn't shared its floors yet. Go back and pick another parking."
                )
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(floors, key = { _, f -> f.floorNumber }) { index, floor ->
                        ParkChip(
                            label = "${BookingRepository.floorLabel(floor.floorNumber)} · ${floor.availableSpots} free",
                            selected = index == selectedFloorIndex,
                            onClick = { selectedFloorIndex = index }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Legend()
                Spacer(Modifier.height(8.dp))

                currentFloor?.let { floor ->
                    Text(
                        floor.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    FloorPlan(
                        floor = floor,
                        selectedSpotId = data.spotId,
                        onSelect = { slot -> flowViewModel.selectSpot(floor, slot) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        BottomActionBar(
            summary = {
                if (data.spotNumber.isNotEmpty()) {
                    Text("${data.floorName} · ${data.spotNumber}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatRupees(data.pricePerHour)}/hr" + if (data.floorMultiplier > 1.0) " · ${data.floorMultiplier}× floor rate" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Text("Tap a free bay", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        ) {
            PrimaryButton(
                text = "Continue",
                enabled = data.spotNumber.isNotEmpty(),
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(Color.White, PurpleLight, "Free")
        LegendSwatch(Color(0xFFF3F4F6), Color.Transparent, "Taken")
        LegendSwatch(Purple, Purple, "Yours")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EvStation, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("EV", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun LegendSwatch(fill: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(fill)
                .border(1.5.dp, border, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** Two rows of bays facing a centre driving aisle, like a real parking lane. */
@Composable
private fun FloorPlan(
    floor: FloorData,
    selectedSpotId: String,
    onSelect: (ParkingSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = floor.spots.chunked(2)
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(16.dp)),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                "Entry ramp",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        items(rows, key = { it.first().id }) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Bay(pair[0], pair[0].id == selectedSpotId, mirror = false, onSelect = onSelect, modifier = Modifier.weight(1f))
                Aisle()
                if (pair.size > 1) {
                    Bay(pair[1], pair[1].id == selectedSpotId, mirror = true, onSelect = onSelect, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Aisle() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(DividerColor)
        )
        Icon(
            Icons.Rounded.ArrowUpward,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier
                .size(14.dp)
                .background(Color.White)
        )
    }
}

@Composable
private fun Bay(
    slot: ParkingSlot,
    selected: Boolean,
    mirror: Boolean,
    onSelect: (ParkingSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val description = when {
        selected -> "Spot ${slot.spotNumber}, selected"
        slot.isAvailable -> "Spot ${slot.spotNumber}, free"
        else -> "Spot ${slot.spotNumber}, taken"
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                when {
                    selected -> Purple
                    slot.isAvailable -> Color.White
                    else -> Color(0xFFF3F4F6)
                }
            )
            .border(
                width = if (slot.isAvailable && !selected) 1.5.dp else 0.dp,
                color = if (slot.hasEvCharging) SuccessGreen.copy(alpha = 0.6f) else PurpleLight.copy(alpha = 0.5f),
                shape = shape
            )
            .clickable(enabled = slot.isAvailable) { onSelect(slot) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (!slot.isAvailable) {
            Image(
                painter = painterResource(R.drawable.car_top_view),
                contentDescription = null,
                modifier = Modifier
                    .height(34.dp)
                    .rotate(if (mirror) 180f else 0f)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    slot.spotNumber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else PurpleDark
                )
                if (slot.hasEvCharging && !selected) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Rounded.EvStation, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                }
                if (slot.hasWheelchairAccess && !selected) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Rounded.Accessible, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

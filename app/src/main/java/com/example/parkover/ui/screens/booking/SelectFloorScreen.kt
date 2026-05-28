package com.example.parkover.ui.screens.booking

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parkover.data.model.FloorData
import com.example.parkover.data.model.ParkingSlot
import com.example.parkover.data.model.VehicleType
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.ui.theme.ErrorRed
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.SuccessGreen
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectFloorScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val repository = remember { BookingRepository() }
    var floors by remember { mutableStateOf<List<FloorData>>(emptyList()) }
    var selectedFloorIndex by remember { mutableStateOf(0) }
    var selectedSpot by remember { mutableStateOf<ParkingSlot?>(null) }

    LaunchedEffect(Unit) {
        floors = repository.getFloorsWithSpots("", VehicleType.FOUR_WHEELER)
        if (floors.isNotEmpty()) selectedFloorIndex = 0
    }

    val currentFloor = floors.getOrNull(selectedFloorIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Select Floor & Spot") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Floor chips
        if (floors.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(floors) { index, floor ->
                    FilterChip(
                        selected = index == selectedFloorIndex,
                        onClick = {
                            selectedFloorIndex = index
                            selectedSpot = null
                        },
                        label = { Text(floor.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Purple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selected spot info
        Text(
            text = selectedSpot?.let { "Selected: ${currentFloor?.name} - Spot ${it.spotNumber}" }
                ?: "Select a parking spot",
            fontSize = 14.sp,
            color = if (selectedSpot != null) Purple else TextSecondary,
            fontWeight = if (selectedSpot != null) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(color = SuccessGreen, label = "Available")
            LegendItem(color = ErrorRed, label = "Occupied")
            LegendItem(color = Purple, label = "Selected")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Parking spots grid
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            currentFloor?.spots?.chunked(2)?.let { rows ->
                items(rows) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { spot ->
                            ParkingSpotCell(
                                spot = spot,
                                isSelected = spot.id == selectedSpot?.id,
                                onClick = {
                                    if (spot.isAvailable) {
                                        selectedSpot = spot
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if odd number
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            enabled = selectedSpot != null
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ParkingSpotCell(
    spot: ParkingSlot,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> Purple
        spot.isAvailable -> SuccessGreen.copy(alpha = 0.15f)
        else -> ErrorRed.copy(alpha = 0.15f)
    }
    val borderColor = when {
        isSelected -> Purple
        spot.isAvailable -> SuccessGreen
        else -> ErrorRed
    }
    val textColor = when {
        isSelected -> Color.White
        spot.isAvailable -> TextPrimary
        else -> ErrorRed
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = spot.isAvailable, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = spot.spotNumber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
    }
}

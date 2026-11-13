package com.dhruvk.parkover.ui.screens.booking

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.model.label
import com.dhruvk.parkover.ui.components.AddVehicleSheet
import com.dhruvk.parkover.ui.components.BookingStepper
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.NumberPlate
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.VehicleThumb
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleLight
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import com.dhruvk.parkover.viewmodels.VehicleViewModel

@Composable
fun SelectVehicleScreen(
    flowViewModel: BookingFlowViewModel,
    vehicleViewModel: VehicleViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val data = flowViewModel.bookingData.collectAsStateWithLifecycle().value
    val spot = flowViewModel.spot.collectAsStateWithLifecycle().value
    val vehicles = flowViewModel.vehicles.collectAsStateWithLifecycle().value
    val loading = flowViewModel.vehiclesLoading.collectAsStateWithLifecycle().value
    val catalog = vehicleViewModel.catalog.collectAsStateWithLifecycle().value
    val saving = vehicleViewModel.saving.collectAsStateWithLifecycle().value
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { flowViewModel.loadVehicles() }
    // No saved vehicles yet: open the add sheet straight away
    LaunchedEffect(loading, vehicles.isEmpty()) {
        if (!loading && vehicles.isEmpty()) showAddSheet = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        ParkTopBar(title = "Choose vehicle", onBack = onBack)
        BookingStepper(step = 1, total = 5, label = "Vehicle", context = data.parkingName)
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (loading) {
                CircularProgressIndicator(color = Purple, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        val rate = when (vehicle.type) {
                            VehicleType.TWO_WHEELER -> spot?.pricePerHourTwoWheeler
                            VehicleType.FOUR_WHEELER -> spot?.pricePerHourFourWheeler
                            VehicleType.HEAVY -> spot?.pricePerHourHeavy
                        }
                        val supported = when (vehicle.type) {
                            VehicleType.TWO_WHEELER -> (spot?.totalSpotsTwoWheeler ?: 1) > 0
                            VehicleType.FOUR_WHEELER -> (spot?.totalSpotsFourWheeler ?: 1) > 0
                            VehicleType.HEAVY -> (spot?.totalSpotsHeavy ?: 1) > 0
                        }
                        VehicleOption(
                            vehicle = vehicle,
                            selected = vehicle.id == data.vehicleId,
                            rate = rate,
                            supported = supported,
                            onClick = { if (supported) flowViewModel.selectVehicle(vehicle) }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAddSheet = true },
                            border = BorderStroke(1.5.dp, PurpleLight.copy(alpha = 0.6f)),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Purple)
                            Spacer(Modifier.width(6.dp))
                            Text("Add a vehicle", color = Purple, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        BottomActionBar(
            summary = {
                if (data.vehicleId.isNotEmpty()) {
                    Text("${formatRupees(data.pricePerHour)}/hr", style = MaterialTheme.typography.titleLarge)
                    Text(data.vehicleType.label + " rate", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                } else {
                    Text("Pick a vehicle", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        ) {
            PrimaryButton(
                text = "Continue",
                enabled = data.vehicleId.isNotEmpty(),
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showAddSheet) {
        AddVehicleSheet(
            catalog = catalog,
            saving = saving,
            onDismiss = { showAddSheet = false },
            onSave = { type, brand, model, number, makeDefault ->
                vehicleViewModel.addVehicle(type, brand, model, number, makeDefault) { saved ->
                    flowViewModel.onVehicleAdded(saved)
                    showAddSheet = false
                }
            }
        )
    }
}

@Composable
private fun VehicleOption(
    vehicle: Vehicle,
    selected: Boolean,
    rate: Double?,
    supported: Boolean,
    onClick: () -> Unit
) {
    ParkCard(selected = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VehicleThumb(vehicle, highlighted = selected)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        vehicle.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (supported) TextPrimary else TextSecondary
                    )
                    if (vehicle.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        StatusBadge("Default", BadgeTone.Brand)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vehicle.number.isNotBlank()) NumberPlate(vehicle.number)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !supported -> "Not accepted here"
                            rate != null -> "${formatRupees(rate)}/h"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (supported) TextSecondary else MaterialTheme.colorScheme.error
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = supported,
                colors = RadioButtonDefaults.colors(selectedColor = Purple)
            )
        }
    }
}

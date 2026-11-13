package com.dhruvk.parkover.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.label
import com.dhruvk.parkover.ui.components.AddVehicleSheet
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.NumberPlate
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.VehicleThumb
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.VehicleViewModel
import kotlinx.coroutines.launch

@Composable
fun SavedVehiclesScreen(vehicleViewModel: VehicleViewModel, onBack: () -> Unit) {
    val vehicles = vehicleViewModel.vehicles.collectAsStateWithLifecycle().value
    val loading = vehicleViewModel.loading.collectAsStateWithLifecycle().value
    val catalog = vehicleViewModel.catalog.collectAsStateWithLifecycle().value
    val saving = vehicleViewModel.saving.collectAsStateWithLifecycle().value
    val message = vehicleViewModel.message.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vehicleViewModel.refresh() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vehicleViewModel.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParkTopBar(title = "My vehicles", onBack = onBack)
            when {
                loading && vehicles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Purple)
                }
                vehicles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.DirectionsCar,
                        title = "No vehicles yet",
                        message = "Add your car or bike once and pick it in one tap when you book."
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        SwipeToDelete(onDelete = {
                            vehicleViewModel.deleteVehicle(vehicle)
                            scope.launch {
                                val result = snackbar.showSnackbar("${vehicle.displayName} deleted", actionLabel = "Undo", withDismissAction = true)
                                if (result == SnackbarResult.ActionPerformed) vehicleViewModel.restoreVehicle(vehicle)
                            }
                        }) {
                            VehicleCard(vehicle, onMakeDefault = { vehicleViewModel.setDefault(vehicle) })
                        }
                    }
                    item {
                        Text(
                            "Swipe left to delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            containerColor = Purple,
            contentColor = Color.White,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Add vehicle") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    if (showAdd) {
        AddVehicleSheet(
            catalog = catalog,
            saving = saving,
            onDismiss = { showAdd = false },
            onSave = { type, brand, model, number, makeDefault ->
                vehicleViewModel.addVehicle(type, brand, model, number, makeDefault) { showAdd = false }
            }
        )
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            true
        } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ErrorRed)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    ) { content() }
}

@Composable
private fun VehicleCard(vehicle: Vehicle, onMakeDefault: () -> Unit) {
    ParkCard(selected = vehicle.isDefault) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VehicleThumb(vehicle, highlighted = vehicle.isDefault)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    if (vehicle.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        StatusBadge("Default", BadgeTone.Brand)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vehicle.number.isNotBlank()) NumberPlate(vehicle.number)
                    Spacer(Modifier.width(8.dp))
                    Text(vehicle.type.label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            if (!vehicle.isDefault) {
                TextButton(onClick = onMakeDefault) { Text("Make default", color = Purple) }
            }
        }
    }
}

package com.example.parkover.ui.screens.booking

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parkover.R
import com.example.parkover.data.model.Vehicle
import com.example.parkover.data.model.VehicleType
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectVehicleScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var selectedVehicleId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            try {
                val result = FirebaseFirestore.getInstance()
                    .collection("vehicles")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                val userVehicles = result.toObjects(Vehicle::class.java)
                vehicles = userVehicles.ifEmpty { createDefaultVehicles(userId) }
            } catch (_: Exception) {
                vehicles = createDefaultVehicles(userId ?: "")
            }
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Select Vehicle") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Purple)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vehicles, key = { it.id }) { vehicle ->
                    VehicleSelectionCard(
                        vehicle = vehicle,
                        isSelected = vehicle.id == selectedVehicleId,
                        onClick = { selectedVehicleId = vehicle.id }
                    )
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                enabled = selectedVehicleId != null
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VehicleSelectionCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, Purple, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = vehicle.imageResId),
                contentDescription = vehicle.model,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${vehicle.brand} ${vehicle.model}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vehicle.number,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = Purple)
            )
        }
    }
}

private suspend fun createDefaultVehicles(userId: String): List<Vehicle> {
    val timestamp = System.currentTimeMillis()
    val prefix = userId.take(8)

    val defaults = listOf(
        Vehicle(
            id = "${prefix}_v1_$timestamp",
            userId = userId,
            type = VehicleType.FOUR_WHEELER,
            brand = "4x4",
            model = "Truck",
            number = "HGE 5295",
            imageResId = R.drawable.car_truck
        ),
        Vehicle(
            id = "${prefix}_v2_$timestamp",
            userId = userId,
            type = VehicleType.FOUR_WHEELER,
            brand = "Toyota",
            model = "Land Cruiser",
            number = "AFD 6397",
            imageResId = R.drawable.car_land_cruiser
        ),
        Vehicle(
            id = "${prefix}_v3_$timestamp",
            userId = userId,
            type = VehicleType.FOUR_WHEELER,
            brand = "KIA",
            model = "SELTOS",
            number = "HUC 2957",
            imageResId = R.drawable.car_suv_orange
        ),
        Vehicle(
            id = "${prefix}_v4_$timestamp",
            userId = userId,
            type = VehicleType.FOUR_WHEELER,
            brand = "Honda",
            model = "City",
            number = "GTK 6294",
            imageResId = R.drawable.car_sedan_white
        )
    )

    // Save to Firestore
    try {
        val firestore = FirebaseFirestore.getInstance()
        defaults.forEach { vehicle ->
            firestore.collection("vehicles").document(vehicle.id).set(vehicle).await()
        }
    } catch (_: Exception) {}

    return defaults
}

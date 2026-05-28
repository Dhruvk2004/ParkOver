package com.example.parkover.ui.screens.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.parkover.data.model.AvailabilityStatus
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.ui.theme.ErrorRed
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.SuccessGreen
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.example.parkover.ui.theme.WarningOrange
import com.example.parkover.viewmodels.ParkingViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingDetailsScreen(
    parkingId: String,
    parkingViewModel: ParkingViewModel,
    onBack: () -> Unit,
    onBookParking: () -> Unit
) {
    val spot = parkingViewModel.selectedParkingSpot.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    var isSaved by remember { mutableStateOf(false) }

    // Check if saved
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(parkingId) {
        if (userId != null && parkingId.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("savedParkings")
                    .document(parkingId)
                    .get()
                    .await()
                isSaved = doc.exists()
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Parking Details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        toggleSaved(parkingId, spot, isSaved) { newState ->
                            isSaved = newState
                        }
                    }
                }) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save",
                        tint = Purple
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Parking image
            val imageUrl = spot?.images?.firstOrNull()
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = spot?.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Name and address
            Text(
                text = spot?.name ?: "Parking Spot",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = spot?.address ?: "",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val availabilityColor = when (spot?.getAvailabilityStatus()) {
                    AvailabilityStatus.FULL -> ErrorRed
                    AvailabilityStatus.LIMITED -> WarningOrange
                    else -> SuccessGreen
                }
                StatItem(
                    label = "Available",
                    value = "${spot?.getTotalAvailableSpots() ?: 0}",
                    color = availabilityColor
                )
                StatItem(
                    label = "Total",
                    value = "${spot?.getTotalSpots() ?: 0}",
                    color = TextPrimary
                )
                StatItem(
                    label = "Rating",
                    value = String.format("%.1f", spot?.rating ?: 0.0),
                    color = WarningOrange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Price per hour", fontSize = 14.sp, color = TextSecondary)
                    Text(
                        text = "₹${String.format("%.0f", spot?.pricePerHourFourWheeler ?: 0.0)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildDescription(spot),
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            // Amenities
            val amenities = spot?.amenities
            if (!amenities.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Amenities", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = amenities.joinToString(" • "),
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Book button
        Button(
            onClick = onBookParking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text("Book Parking", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
    }
}

private fun buildDescription(spot: ParkingSpot?): String {
    if (spot == null) return "This parking facility offers secure and convenient parking spaces."
    val floorsText = if (spot.floors.isNotEmpty()) {
        "This facility has ${spot.floors.size} floors with a total of ${spot.getTotalSpots()} parking spots. "
    } else ""
    return "${floorsText}The facility is equipped with professional staff and modern amenities for your convenience."
}

private suspend fun toggleSaved(
    parkingId: String,
    spot: ParkingSpot?,
    currentlySaved: Boolean,
    onResult: (Boolean) -> Unit
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    if (parkingId.isEmpty()) return

    val savedRef = FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .collection("savedParkings")

    try {
        if (currentlySaved) {
            savedRef.document(parkingId).delete().await()
            onResult(false)
        } else {
            val data = mapOf(
                "parkingId" to parkingId,
                "name" to (spot?.name ?: ""),
                "address" to (spot?.address ?: ""),
                "latitude" to (spot?.latitude ?: 0.0),
                "longitude" to (spot?.longitude ?: 0.0),
                "pricePerHour" to (spot?.pricePerHourFourWheeler ?: 0.0),
                "rating" to (spot?.rating ?: 0.0),
                "imageUrl" to (spot?.images?.firstOrNull() ?: ""),
                "savedAt" to Timestamp.now()
            )
            savedRef.document(parkingId).set(data).await()
            onResult(true)
        }
    } catch (_: Exception) {}
}

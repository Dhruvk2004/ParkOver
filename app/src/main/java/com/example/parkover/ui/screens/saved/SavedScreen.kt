package com.example.parkover.ui.screens.saved

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.example.parkover.ui.theme.WarningOrange
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SavedScreen(
    onParkingClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var savedParkings by remember { mutableStateOf<List<ParkingSpot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loadSavedParkings { parkings ->
            savedParkings = parkings
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Saved Parkings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
        } else if (savedParkings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Saved Parkings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your saved parking spots\nwill appear here",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedParkings, key = { it.id }) { spot ->
                    SavedParkingCard(
                        spot = spot,
                        onClick = { onParkingClick(spot.id) },
                        onRemove = {
                            scope.launch {
                                removeSavedParking(spot.id)
                                savedParkings = savedParkings.filter { it.id != spot.id }
                            }
                        }
                    )
                }
                // Bottom padding for nav bar
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SavedParkingCard(
    spot: ParkingSpot,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image
            AsyncImage(
                model = spot.images.firstOrNull(),
                contentDescription = spot.name,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = spot.address,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = WarningOrange
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", spot.rating),
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "₹${spot.pricePerHourFourWheeler.toInt()}/hr",
                        fontSize = 12.sp,
                        color = Purple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.BookmarkRemove,
                    contentDescription = "Remove",
                    tint = Purple
                )
            }
        }
    }
}

private suspend fun loadSavedParkings(onResult: (List<ParkingSpot>) -> Unit) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
        onResult(emptyList())
        return
    }

    try {
        val savedDocs = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("savedParkings")
            .get()
            .await()

        val parkings = savedDocs.documents.mapNotNull { doc ->
            try {
                ParkingSpot(
                    id = doc.getString("parkingId") ?: doc.id,
                    name = doc.getString("name") ?: "",
                    address = doc.getString("address") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    pricePerHourFourWheeler = doc.getDouble("pricePerHour") ?: 0.0,
                    rating = doc.getDouble("rating") ?: 0.0,
                    images = listOfNotNull(doc.getString("imageUrl"))
                )
            } catch (e: Exception) {
                null
            }
        }
        onResult(parkings)
    } catch (e: Exception) {
        onResult(emptyList())
    }
}

private suspend fun removeSavedParking(parkingId: String) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    try {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("savedParkings")
            .document(parkingId)
            .delete()
            .await()
    } catch (_: Exception) {}
}

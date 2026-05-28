package com.example.parkover.ui.screens.booking

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parkover.data.model.Booking
import com.example.parkover.data.model.BookingStatus
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.SuccessGreen
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.example.parkover.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BookingListScreen() {
    val repository = remember { BookingRepository() }
    var isOngoing by remember { mutableStateOf(true) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(isOngoing) {
        isLoading = true
        bookings = if (isOngoing) {
            repository.getOngoingBookings()
        } else {
            repository.getCompletedBookings()
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 16.dp)
    ) {
        Text(
            text = "My Bookings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isOngoing) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple)
                ) {
                    Text("Ongoing", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { isOngoing = false },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF0F0F0),
                        contentColor = TextSecondary
                    )
                ) {
                    Text("Completed")
                }
            } else {
                Button(
                    onClick = { isOngoing = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF0F0F0),
                        contentColor = TextSecondary
                    )
                ) {
                    Text("Ongoing")
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple)
                ) {
                    Text("Completed", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
        } else if (bookings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isOngoing) "No Ongoing Bookings" else "No Completed Bookings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isOngoing)
                            "Your active parking bookings\nwill appear here"
                        else
                            "Your past parking bookings\nwill appear here",
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
                items(bookings, key = { it.id }) { booking ->
                    BookingCard(booking = booking)
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun BookingCard(booking: Booking) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val statusColor = when (booking.bookingStatus) {
        BookingStatus.CONFIRMED, BookingStatus.ACTIVE -> SuccessGreen
        BookingStatus.PENDING -> WarningOrange
        BookingStatus.COMPLETED -> Purple
        BookingStatus.CANCELLED -> Color.Red
        BookingStatus.EXPIRED -> TextSecondary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = booking.parkingName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = booking.bookingStatus.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = booking.parkingAddress,
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Column {
                    Text("Date", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = dateFormat.format(booking.entryTime.toDate()),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text("Time", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "${timeFormat.format(booking.entryTime.toDate())} - ${timeFormat.format(booking.exitTime.toDate())}",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${booking.floorName} • Spot ${booking.spotNumber}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = "₹${String.format("%.0f", booking.totalPrice)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Purple
                )
            }
        }
    }
}

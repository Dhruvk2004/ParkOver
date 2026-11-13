package com.dhruvk.parkover.ui.screens.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.PaymentMethod
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.QrCode
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.SquareIconButton
import com.dhruvk.parkover.ui.components.SystemBarIcons
import com.dhruvk.parkover.ui.components.formatPlate
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.components.openDirections
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BookingSuccessScreen(
    bookingId: String,
    onViewBooking: () -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    var booking by remember { mutableStateOf<Booking?>(null) }
    var loading by remember { mutableStateOf(true) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    LaunchedEffect(bookingId) {
        booking = BookingRepository().getBooking(bookingId)
        loading = false
    }

    SystemBarIcons(lightIcons = true)
    BackHandler(onBack = onBackHome)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Purple)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SquareIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Back to home",
                    onClick = onBackHome,
                    tint = Color.White,
                    container = Color.White.copy(alpha = 0.15f),
                    bordered = false,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("You're booked!", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            booking?.let {
                Text(
                    "${dateFormat.format(it.entryTime.toDate())} – ${timeFormat.format(it.exitTime.toDate())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QrCode(data = booking?.qrCodeData?.ifEmpty { null } ?: "PARKOVER:$bookingId", size = 190.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    bookingId,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    color = TextPrimary
                )
                Text(
                    "Show this at the entry gate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (loading) {
                    CircularProgressIndicator(color = Purple, modifier = Modifier.size(28.dp))
                } else {
                    booking?.let { b ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row {
                                Field("Parking", b.parkingName, Modifier.weight(1f))
                                Field("Spot", "${b.floorName} · ${b.spotNumber}", Modifier.weight(1f))
                            }
                            Row {
                                Field("Vehicle", formatPlate(b.vehicleNumber), Modifier.weight(1f))
                                Field(
                                    "To pay",
                                    "${formatRupees(b.totalPrice, true)} · ${if (b.paymentMethod == PaymentMethod.CASH) "at parking" else "UPI at gate"}",
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                PrimaryButton(
                    text = "Directions",
                    icon = Icons.Rounded.Directions,
                    onClick = {
                        val b = booking
                        openDirections(context, null, null, listOfNotNull(b?.parkingName, b?.parkingAddress).joinToString(", "))
                    },
                    container = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "My bookings",
                    onClick = onViewBooking,
                    container = Color.White,
                    content = Purple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        SectionLabel(label)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

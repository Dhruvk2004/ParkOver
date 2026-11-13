package com.dhruvk.parkover.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.data.model.label
import com.dhruvk.parkover.navigation.Screen
import com.dhruvk.parkover.ui.components.BookingStepper
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.formatPlate
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.utils.Constants
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReviewSummaryScreen(
    flowViewModel: BookingFlowViewModel,
    onBack: () -> Unit,
    onEdit: (route: String) -> Unit,
    onContinue: () -> Unit
) {
    val data = flowViewModel.bookingData.collectAsStateWithLifecycle().value
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        ParkTopBar(title = "Review booking", onBack = onBack)
        BookingStepper(step = 4, total = 5, label = "Review")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Ticket
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardDark)
                        .padding(16.dp)
                ) {
                    Text("PARKING", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text(data.parkingName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(data.parkingAddress, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row {
                        TicketField("Date", dateFormat.format(Date(data.checkInTime)), Modifier.weight(1f)) {
                            onEdit(Screen.ConfirmBooking.route)
                        }
                        TicketField(
                            "Time",
                            "${timeFormat.format(Date(data.checkInTime))} – ${timeFormat.format(Date(data.checkOutTime))}",
                            Modifier.weight(1f)
                        ) { onEdit(Screen.ConfirmBooking.route) }
                    }
                    Row {
                        TicketField("Spot", "${data.floorName} · ${data.spotNumber}", Modifier.weight(1f)) {
                            onEdit(Screen.SelectFloor.route)
                        }
                        TicketField(
                            "Vehicle",
                            data.vehicleNumber.takeIf { it.isNotBlank() }?.let(::formatPlate) ?: data.vehicleName,
                            Modifier.weight(1f)
                        ) { onEdit(Screen.SelectVehicle.route) }
                    }
                    Row {
                        TicketField("Duration", "${data.durationHours} hour${if (data.durationHours == 1) "" else "s"}", Modifier.weight(1f)) {
                            onEdit(Screen.ConfirmBooking.route)
                        }
                        TicketField("Type", data.vehicleType.label, Modifier.weight(1f)) {
                            onEdit(Screen.SelectVehicle.route)
                        }
                    }
                }

                Perforation()

                Column(modifier = Modifier.padding(16.dp)) {
                    PriceLine("Parking · ${data.durationHours}h × ${formatRupees(data.pricePerHour)}", formatRupees(data.basePrice, true))
                    PriceLine("Taxes (${(Constants.BOOKING_TAX_RATE * 100).toInt()}%)", formatRupees(data.taxAmount, true))
                    Spacer(Modifier.height(6.dp))
                    PriceLine("Total", formatRupees(data.totalPrice, true), emphasize = true)
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Tap any detail to change it. You can cancel from My bookings any time before check-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        BottomActionBar(
            summary = {
                Text(formatRupees(data.totalPrice, true), style = MaterialTheme.typography.titleLarge)
                Text("incl. tax", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        ) {
            PrimaryButton(text = "Continue to pay", onClick = onContinue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TicketField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
    ) {
        SectionLabel(label)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** Dashed tear line with notches, like a paper ticket. */
@Composable
private fun Perforation() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 12.dp, height = 24.dp)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(BackgroundLight)
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = DividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                    )
                }
        )
        Box(
            Modifier
                .size(width = 12.dp, height = 24.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(BackgroundLight)
        )
    }
}

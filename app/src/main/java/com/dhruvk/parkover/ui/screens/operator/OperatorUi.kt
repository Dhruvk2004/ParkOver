package com.dhruvk.parkover.ui.screens.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.PaymentStatus
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.domain.OperatorRules
import com.dhruvk.parkover.domain.TodayTab
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.NumberPlate
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Small formatting helpers shared by the operator screens.

private fun timeFormat() = SimpleDateFormat("h:mm a", Locale.getDefault())
private fun dayFormat() = SimpleDateFormat("EEE d MMM", Locale.getDefault())

fun clockTime(ms: Long): String = timeFormat().format(Date(ms))

fun Booking.entryMs(): Long = entryTime.toDate().time
fun Booking.exitMs(): Long = exitTime.toDate().time

/** "6:00 – 8:00 PM", with the day in front when it isn't today. */
fun Booking.timeRange(now: Long): String {
    val range = "${clockTime(entryMs())} – ${clockTime(exitMs())}"
    return if (OperatorRules.sameDay(entryMs(), now)) range else "${dayFormat().format(Date(entryMs()))}, $range"
}

fun VehicleType.shortName(): String = when (this) {
    VehicleType.TWO_WHEELER -> "Bike"
    VehicleType.FOUR_WHEELER -> "Car"
    VehicleType.HEAVY -> "Heavy vehicle"
}

/** "Paid ₹118" or "Not paid · ₹118" (payments come in the next part). */
fun Booking.paymentLine(): String =
    if (paymentStatus == PaymentStatus.COMPLETED) "Paid ${formatRupees(totalPrice)}" else "Not paid yet · ${formatRupees(totalPrice)}"

fun Booking.displayName(): String = when {
    walkIn -> driverName.ifBlank { "Blocked by you" }
    driverName.isNotBlank() -> driverName
    else -> "Driver"
}

/** Status words the operator and driver both see: Booked → Arrived → Exited, or Didn't come. */
fun Booking.statusBadge(): Pair<String, BadgeTone> = when (bookingStatus) {
    BookingStatus.PENDING, BookingStatus.CONFIRMED -> "Booked" to BadgeTone.Brand
    BookingStatus.ACTIVE -> (if (walkIn) "Blocked" else "Parked") to BadgeTone.Success
    BookingStatus.COMPLETED -> (if (walkIn) "Freed" else "Exited") to BadgeTone.Neutral
    BookingStatus.CANCELLED -> "Cancelled" to BadgeTone.Error
    BookingStatus.EXPIRED -> "Didn't come" to BadgeTone.Error
}

/** The short time note on the right of a card: "in 12 min", "Late 18 min", "Due out 8:00 PM". */
fun Booking.whenNote(tab: TodayTab, now: Long): Pair<String, BadgeTone> = when (tab) {
    TodayTab.Arriving -> {
        val until = entryMs() - now
        (if (until > 60 * 60_000L) "at ${clockTime(entryMs())}" else if (until > 0) "in ${OperatorRules.formatSpan(until)}" else "now") to BadgeTone.Brand
    }
    TodayTab.Late -> "Late ${OperatorRules.formatSpan(now - entryMs())}" to BadgeTone.Warning
    TodayTab.Parked -> if (now > exitMs()) "Over ${OperatorRules.formatSpan(now - exitMs())}" to BadgeTone.Error
                       else "Due out ${clockTime(exitMs())}" to BadgeTone.Success
    TodayTab.Done -> statusBadge()
}

/**
 * One booking on the Today list: plate first (what the operator matches at the gate), then who,
 * which bay, when and paid, and the one next action for its tab.
 */
@Composable
fun OperatorBookingCard(
    booking: Booking,
    tab: TodayTab,
    now: Long,
    onOpen: () -> Unit,
    onScan: () -> Unit,
    onCheckOut: () -> Unit
) {
    ParkCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (booking.vehicleNumber.isNotBlank()) NumberPlate(booking.vehicleNumber)
            else Text(booking.displayName(), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            val (note, tone) = booking.whenNote(tab, now)
            StatusBadge(note, tone)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${booking.displayName()} · Bay ${booking.spotNumber}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (booking.walkIn) "Until ${clockTime(booking.exitMs())}"
            else "${booking.timeRange(now)} · ${booking.vehicleType.shortName()} · ${booking.paymentLine()}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        when (tab) {
            TodayTab.Arriving -> {
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Scan to check in", onClick = onScan, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.height(46.dp))
            }
            TodayTab.Late -> {
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Open booking", onClick = onOpen, height = 46.dp, modifier = Modifier.fillMaxWidth())
            }
            TodayTab.Parked -> {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (booking.walkIn) "Free this bay" else "Check out", onClick = onCheckOut, height = 46.dp, modifier = Modifier.fillMaxWidth())
            }
            TodayTab.Done -> if (booking.extraCharges > 0) {
                Spacer(Modifier.height(6.dp))
                Text("Extra collected: ${formatRupees(booking.extraCharges)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

/** A label above a value, used in the small tiles on the booking and scan screens. */
@Composable
fun InfoTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

package com.dhruvk.parkover.ui.screens.operator

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.PaymentStatus
import com.dhruvk.parkover.data.repository.BlockReason
import com.dhruvk.parkover.domain.OperatorRules
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.NumberPlate
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.OperatorViewModel

/**
 * One booking, for the operator (page 4: A's status steps with B's "time left" while parked).
 * The bottom button is the next step: Mark arrived, or Check out.
 */
@Composable
fun OperatorBookingScreen(
    bookingId: String,
    viewModel: OperatorViewModel,
    onBack: () -> Unit
) {
    val bookings by viewModel.bookings.collectAsState()
    val now by viewModel.now.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var fallback by remember { mutableStateOf<Booking?>(null) }
    var loading by remember { mutableStateOf(true) }
    val booking = bookings?.firstOrNull { it.id == bookingId } ?: fallback
    var confirmCheckOut by remember { mutableStateOf(false) }

    LaunchedEffect(bookingId, bookings == null) {
        if (booking == null && bookings != null) fallback = viewModel.findBooking(bookingId)
        loading = bookings == null
    }

    Column(Modifier.fillMaxSize().background(BackgroundLight)) {
        ParkTopBar(title = "Booking #$bookingId", onBack = onBack)
        if (booking == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(color = Purple)
                else EmptyState(Icons.Rounded.SearchOff, "Booking not found", "It may belong to another parking place.")
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ParkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(booking.displayName(), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        if (booking.vehicleNumber.isNotBlank()) NumberPlate(booking.vehicleNumber)
                    }
                    val (label, tone) = booking.statusBadge()
                    StatusBadge(label, tone)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Bay", booking.spotNumber, booking.floorName.ifBlank { null }, Modifier.weight(1f))
                Tile(if (booking.walkIn) "Until" else "Booked", if (booking.walkIn) clockTime(booking.exitMs()) else booking.timeRange(now).substringAfter(", "), null, Modifier.weight(1f))
                if (!booking.walkIn) {
                    Tile(
                        if (booking.paymentStatus == PaymentStatus.COMPLETED) "Paid" else "To pay",
                        formatRupees(booking.totalPrice),
                        null,
                        Modifier.weight(1f)
                    )
                }
            }

            if (booking.bookingStatus == BookingStatus.ACTIVE) TimeLeft(booking, now)

            ParkCard {
                Text("Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Steps(booking, now)
            }
        }

        val waiting = booking.bookingStatus == BookingStatus.PENDING || booking.bookingStatus == BookingStatus.CONFIRMED
        when {
            waiting && now <= booking.exitMs() -> BottomActionBar {
                PrimaryButton("Mark arrived", onClick = { viewModel.checkIn(booking) }, loading = busy)
            }
            booking.bookingStatus == BookingStatus.ACTIVE -> BottomActionBar {
                PrimaryButton(if (booking.walkIn) "Free this bay" else "Check out", onClick = { confirmCheckOut = true }, loading = busy)
            }
        }
    }

    if (confirmCheckOut && booking != null) {
        CheckOutDialog(
            booking = booking,
            now = now,
            onConfirm = { confirmCheckOut = false; viewModel.checkOut(booking) },
            onDismiss = { confirmCheckOut = false }
        )
    }
}

/** "Check out MH01 AB 1234?" with what to collect, worked out from the time now. */
@Composable
fun CheckOutDialog(booking: Booking, now: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val extra = OperatorRules.overstayCharge(booking, now)
    val title = if (booking.walkIn) "Free bay ${booking.spotNumber}?" else "Check out ${booking.vehicleNumber.ifBlank { booking.spotNumber }}?"
    val body = when {
        booking.walkIn -> "The bay goes back on sale in the app straight away."
        extra > 0 -> "They're leaving ${OperatorRules.formatSpan(now - booking.exitMs())} after ${clockTime(booking.exitMs())}. " +
            "Collect ${formatRupees(extra)} extra at the gate."
        else -> "No extra charge. The rest of their time goes back on sale."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (booking.walkIn) "Free bay" else "Check out", color = Purple) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet", color = TextSecondary) } },
        containerColor = Color.White
    )
}

@Composable
private fun Tile(label: String, value: String, sub: String?, modifier: Modifier) {
    ParkCard(modifier = modifier) { InfoTile(label, value, sub = sub) }
}

/** Page 4B's block: how long is left, and what leaving now or late costs. */
@Composable
private fun TimeLeft(booking: Booking, now: Long) {
    val start = booking.checkedInAt?.toDate()?.time ?: booking.entryMs()
    val end = booking.exitMs()
    val over = now > end
    val progress = ((now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    val extraNow = OperatorRules.overstayCharge(booking, now)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (over) Color(0xFFFEF2F2) else PurpleTint)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (over) "Over time" else "Time left", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            if (over) "Over by ${OperatorRules.formatSpan(now - end)}" else OperatorRules.formatSpan(end - now),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (over) ErrorRed else TextPrimary
        )
        LinearProgressIndicator(
            progress = { progress },
            color = if (over) ErrorRed else Purple,
            trackColor = Color.White,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        )
        Row {
            Text("Parked ${clockTime(start)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("ends ${clockTime(end)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (!booking.walkIn) {
            Text(
                "If they leave now: " + if (extraNow > 0) "${formatRupees(extraNow)} extra" else "no extra charge",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                "After ${clockTime(end)} (10 minutes' grace), it's ${formatRupees(OperatorRules.overstayStepPrice(booking))} " +
                    "per extra half hour, collected at the gate.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        } else {
            Text(
                "Blocked: ${BlockReason.entries.firstOrNull { it.name == booking.blockReason }?.label ?: "by you"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

private data class Step(val title: String, val detail: String, val done: Boolean, val bad: Boolean = false)

/** Booked → Arrived → Exited (or Cancelled / Didn't come), with times. */
@Composable
private fun Steps(booking: Booking, now: Long) {
    val booked = Step(
        if (booking.walkIn) "Blocked" else "Booked",
        "${clockTime(booking.createdAt.toDate().time)} · " + if (booking.paymentStatus == PaymentStatus.COMPLETED) "paid" else "not paid yet",
        done = true
    )
    val checkedIn = booking.checkedInAt?.toDate()?.time
    val arrived = when {
        checkedIn != null -> Step("Arrived", "${clockTime(checkedIn)} · checked in", done = true)
        booking.bookingStatus == BookingStatus.CANCELLED -> Step("Cancelled", "by the driver · ${clockTime(booking.updatedAt.toDate().time)}", done = true, bad = true)
        booking.bookingStatus == BookingStatus.EXPIRED -> Step("Didn't come", "marked at ${clockTime(booking.updatedAt.toDate().time)}", done = true, bad = true)
        now >= booking.entryMs() + OperatorRules.LATE_AFTER_MS -> Step("Arrived", "Not yet · late by ${OperatorRules.formatSpan(now - booking.entryMs())}", done = false, bad = true)
        else -> Step("Arrived", "Expected ${clockTime(booking.entryMs())}", done = false)
    }
    val exitedAt = booking.actualExitTime?.toDate()?.time
    val exited = when {
        exitedAt != null -> Step(
            if (booking.walkIn) "Freed" else "Exited",
            clockTime(exitedAt) + if (booking.extraCharges > 0) " · ${formatRupees(booking.extraCharges)} extra" else "",
            done = true
        )
        else -> Step(if (booking.walkIn) "Freed" else "Exited", "Due by ${clockTime(booking.exitMs())}", done = false)
    }
    val steps = if (arrived.bad && arrived.done) listOf(booked, arrived) else listOf(booked, arrived, exited)

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        steps.forEachIndexed { i, step ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(
                            when {
                                step.done && step.bad -> ErrorRed
                                step.done -> SuccessGreen
                                else -> Color.White
                            }
                        ).then(if (!step.done) Modifier.background(Color.White) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (step.done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        else Box(Modifier.size(20.dp).clip(CircleShape).background(DividerColor).padding(3.dp).clip(CircleShape).background(Color.White))
                    }
                    if (i < steps.lastIndex) {
                        Box(Modifier.width(2.dp).height(30.dp).background(if (steps[i + 1].done) SuccessGreen else DividerColor))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(step.detail, style = MaterialTheme.typography.bodySmall, color = if (step.bad && !step.done) Color(0xFFB45309) else TextSecondary)
                }
            }
        }
    }
}

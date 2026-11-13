package com.dhruvk.parkover.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.repository.ApiResult
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.QrCode
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.formatPlate
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.components.openDirections
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.ParkingViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BookingListScreen(
    accountViewModel: AccountViewModel,
    parkingViewModel: ParkingViewModel,
    onFindParking: () -> Unit
) {
    val context = LocalContext.current
    val bookings = accountViewModel.bookings.collectAsStateWithLifecycle().value
    val busy = accountViewModel.busy.collectAsStateWithLifecycle().value
    val message = accountViewModel.message.collectAsStateWithLifecycle().value
    val spots = (parkingViewModel.parkingSpots.collectAsStateWithLifecycle().value as? ApiResult.Success)?.data.orEmpty()
    val snackbar = remember { SnackbarHostState() }

    var showPast by rememberSaveable { mutableStateOf(false) }
    var qrFor by remember { mutableStateOf<Booking?>(null) }
    var cancelFor by remember { mutableStateOf<Booking?>(null) }
    var extendFor by remember { mutableStateOf<Booking?>(null) }

    // Ticks once a minute so countdowns and Upcoming/Past stay current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            accountViewModel.clearMessage()
        }
    }

    val upcoming = bookings.orEmpty().filter { AccountViewModel.isUpcoming(it) }.sortedBy { it.entryTime.toDate() }
    val past = bookings.orEmpty().filterNot { AccountViewModel.isUpcoming(it) }
    val list = if (showPast) past else upcoming

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("Bookings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEEF0F4))
                        .padding(4.dp)
                ) {
                    SegmentTab("Upcoming · ${upcoming.size}", !showPast, Modifier.weight(1f)) { showPast = false }
                    SegmentTab("Past · ${past.size}", showPast, Modifier.weight(1f)) { showPast = true }
                }
            }

            when {
                bookings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Purple)
                }
                list.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = BottomNavReservedHeight),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Rounded.CalendarMonth,
                        title = if (showPast) "No past bookings" else "No upcoming bookings",
                        message = if (showPast) "Bookings you've finished or cancelled show up here."
                        else "Book a spot and it'll show up here with its QR code."
                    ) {
                        if (!showPast) PrimaryButton(text = "Find parking", icon = Icons.Rounded.Map, onClick = onFindParking)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomNavReservedHeight),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.id }) { booking ->
                        val spot = spots.firstOrNull { it.id == booking.parkingId }
                        BookingCard(
                            booking = booking,
                            now = now,
                            onShowQr = { qrFor = booking },
                            onExtend = { extendFor = booking },
                            onCancel = { cancelFor = booking },
                            onDirections = {
                                openDirections(context, spot?.latitude, spot?.longitude, "${booking.parkingName}, ${booking.parkingAddress}")
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomNavReservedHeight)
        )
    }

    qrFor?.let { booking ->
        AlertDialog(
            onDismissRequest = { qrFor = null },
            containerColor = Color.White,
            title = { Text(booking.parkingName, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    QrCode(booking.qrCodeData.ifEmpty { "PARKOVER:${booking.id}" }, size = 220.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(booking.id, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text("${booking.floorName} · ${booking.spotNumber}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { qrFor = null }) { Text("Done", color = Purple) } }
        )
    }

    cancelFor?.let { booking ->
        AlertDialog(
            onDismissRequest = { cancelFor = null },
            containerColor = Color.White,
            title = { Text("Cancel this booking?") },
            text = { Text("${booking.parkingName}, ${booking.floorName} · ${booking.spotNumber}. The spot will be released for others.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    accountViewModel.cancelBooking(booking)
                    cancelFor = null
                }) { Text("Cancel booking", color = ErrorRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { cancelFor = null }) { Text("Keep it", color = TextSecondary) } }
        )
    }

    extendFor?.let { booking ->
        val rate = if (booking.durationHours > 0) booking.basePrice / booking.durationHours else 0.0
        AlertDialog(
            onDismissRequest = { extendFor = null },
            containerColor = Color.White,
            title = { Text("Add 1 hour?") },
            text = { Text("Your booking will end an hour later. That adds ${formatRupees(rate * 1.1, true)} including tax.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    accountViewModel.extendBooking(booking, 1)
                    extendFor = null
                }) { Text("Add 1 hour", color = Purple, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { extendFor = null }) { Text("Not now", color = TextSecondary) } }
        )
    }
}

@Composable
private fun SegmentTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    now: Long,
    onShowQr: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    onDirections: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val start = booking.entryTime.toDate().time
    val end = booking.exitTime.toDate().time
    val upcoming = AccountViewModel.isUpcoming(booking)
    val activeNow = upcoming && start <= now

    ParkCard(selected = activeNow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                activeNow -> StatusBadge("Active · ${formatRemaining(end - now)} left", BadgeTone.Success)
                upcoming -> StatusBadge("${dateFormat.format(booking.entryTime.toDate())} · ${timeFormat.format(booking.entryTime.toDate())}", BadgeTone.Brand)
                booking.bookingStatus == BookingStatus.CANCELLED -> StatusBadge("Cancelled", BadgeTone.Error)
                else -> StatusBadge("Completed", BadgeTone.Neutral)
            }
            Spacer(Modifier.weight(1f))
            Text(formatRupees(booking.totalPrice), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(booking.parkingName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            "${booking.floorName} · ${booking.spotNumber} · ${formatPlate(booking.vehicleNumber)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (activeNow) {
            Spacer(Modifier.height(10.dp))
            val progress = ((now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DividerColor)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Purple)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeFormat.format(booking.entryTime.toDate()), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(timeFormat.format(booking.exitTime.toDate()), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        } else if (!upcoming) {
            Text(
                "${dateFormat.format(booking.entryTime.toDate())} · ${timeFormat.format(booking.entryTime.toDate())} – ${timeFormat.format(booking.exitTime.toDate())}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (upcoming) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Show QR",
                    icon = Icons.Rounded.QrCode2,
                    onClick = onShowQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                )
                if (activeNow) {
                    SecondaryButton(text = "Extend", icon = Icons.Rounded.MoreTime, onClick = onExtend, height = 44.dp, modifier = Modifier.weight(1f))
                } else {
                    SecondaryButton(text = "Directions", icon = Icons.Rounded.Directions, onClick = onDirections, height = 44.dp, modifier = Modifier.weight(1f))
                }
            }
            if (!activeNow) {
                Text(
                    "Cancel booking",
                    style = MaterialTheme.typography.labelLarge,
                    color = ErrorRed,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancel)
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

package com.dhruvk.parkover.ui.screens.operator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.repository.BlockReason
import com.dhruvk.parkover.data.repository.BookingRepository
import com.dhruvk.parkover.domain.BayState
import com.dhruvk.parkover.domain.OperatorRules
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.ParkChip
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.BayView
import com.dhruvk.parkover.viewmodels.FloorView
import com.dhruvk.parkover.viewmodels.OperatorViewModel

private data class BayColors(val bg: Color, val fg: Color, val border: Color)

private fun colorsFor(state: BayState) = when (state) {
    BayState.Free -> BayColors(Color.White, TextPrimary, DividerColor)
    BayState.Booked -> BayColors(PurpleTint, Color(0xFF4A2BC7), Color(0xFFC9BDF8))
    BayState.Parked -> BayColors(Color(0xFFDCFCE7), Color(0xFF166534), Color(0xFF86EFAC))
    BayState.Blocked -> BayColors(Color(0xFFF3F4F6), Color(0xFF4B5563), Color(0xFF9CA3AF))
}

private fun BayState.word() = when (this) {
    BayState.Free -> "free"
    BayState.Booked -> "booked"
    BayState.Parked -> "parked"
    BayState.Blocked -> "blocked"
}

/**
 * Bays right now (page 6A): one floor at a time, each bay coloured by what's in it. Tap a free
 * bay to block it (a walk-in car, a repair, staff), a blocked one to free it, any other to see
 * its booking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaysScreen(
    viewModel: OperatorViewModel,
    onOpenBooking: (String) -> Unit
) {
    val spot by viewModel.spot.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val locks by viewModel.locks.collectAsState()
    val now by viewModel.now.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val floors = remember(spot, bookings, locks, now) { viewModel.floors(spot, bookings.orEmpty(), locks, now) }
    var floorIndex by rememberSaveable { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Pair<FloorView, BayView>?>(null) }

    Column(Modifier.fillMaxSize().background(BackgroundLight)) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("Bays · now", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(clockTime(now), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (floors.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (spot == null) CircularProgressIndicator(color = Purple)
                else EmptyState(Icons.Rounded.LocalParking, "No floors set up", "This parking place has no bays listed yet.")
            }
            return@Column
        }
        val floor = floors[floorIndex.coerceIn(0, floors.lastIndex)]

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            floors.forEachIndexed { i, f ->
                val free = f.bays.count { it.state == BayState.Free }
                ParkChip(label = "${f.label} · $free free", selected = i == floorIndex, onClick = { floorIndex = i })
            }
        }

        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BayState.entries.forEach { s ->
                val c = colorsFor(s)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.bg).border(1.dp, c.border, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.size(5.dp))
                    Text(s.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = BottomNavReservedHeight + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(floor.bays, key = { it.spotNumber }) { bay ->
                val c = colorsFor(bay.state)
                Column(
                    Modifier
                        .aspectRatio(0.9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.bg)
                        .border(BorderStroke(1.dp, c.border), RoundedCornerShape(12.dp))
                        .clickable {
                            when (bay.state) {
                                BayState.Free, BayState.Blocked -> picked = floor to bay
                                else -> bay.booking?.let { onOpenBooking(it.id) }
                            }
                        }
                        .semantics { contentDescription = "Bay ${bay.spotNumber}, ${bay.state.word()}" }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(bay.shortLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.fg)
                    Text(bay.state.word(), style = MaterialTheme.typography.labelSmall, color = c.fg)
                }
            }
        }
    }

    picked?.let { (floor, bay) ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { picked = null }, sheetState = sheetState, containerColor = Color.White) {
            if (bay.state == BayState.Blocked && bay.booking != null) {
                FreeBaySheet(bay, busy) { viewModel.checkOut(bay.booking) { picked = null } }
            } else {
                BlockBaySheet(bay, now, busy) { until, reason, plate ->
                    viewModel.blockBay(floor.floor, floor.label, bay.spotNumber, until, reason, plate) { picked = null }
                }
            }
        }
    }
}

/** A choice of how long to block: a label and the end time. */
private data class Until(val label: String, val ms: Long)

/**
 * The ways a bay can be blocked from now: never past the next driver's booking on it, so a
 * walk-in car can't take a bay that's already sold.
 */
private fun untilChoices(now: Long, nextBookingAt: Long?): List<Until> {
    val endOfDay = OperatorRules.endOfDay(now)
    val cap = minOf(nextBookingAt ?: Long.MAX_VALUE, endOfDay)
    val hours = listOf(1, 2, 4).map { h -> Until("$h hour${if (h > 1) "s" else ""}", now + h * 3_600_000L) }.filter { it.ms <= cap }
    val last = if (nextBookingAt != null && nextBookingAt < endOfDay) Until("Until ${clockTime(nextBookingAt)}", nextBookingAt)
               else Until("Rest of today", endOfDay)
    return (hours + last).filter { it.ms - now >= BookingRepository.LOCK_BUCKET_MS / 2 }.distinctBy { it.ms }
}

@Composable
private fun BlockBaySheet(bay: BayView, now: Long, busy: Boolean, onBlock: (Long, BlockReason, String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf(BlockReason.WalkIn) }
    var plate by rememberSaveable { mutableStateOf("") }
    val choices = remember(now, bay.nextBookingAt) { untilChoices(now, bay.nextBookingAt) }
    var untilIndex by rememberSaveable { mutableIntStateOf(0) }
    val until = choices.getOrNull(untilIndex.coerceAtMost(choices.lastIndex))

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Bay ${bay.spotNumber}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                "Free now" + (bay.nextBookingAt?.let { " · next booking ${clockTime(it)}" } ?: " · no more bookings today"),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Text("Why block it?", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockReason.entries.forEach { r -> ParkChip(r.label, selected = r == reason, onClick = { reason = r }) }
        }
        if (reason == BlockReason.WalkIn) {
            ParkTextField(
                value = plate,
                onValueChange = { plate = it.take(13) },
                label = "Car number (optional)",
                placeholder = "MH01AB1234",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                monospace = true
            )
        }

        if (choices.isEmpty()) {
            Text(
                "A driver has this bay from ${bay.nextBookingAt?.let(::clockTime) ?: "soon"}, so it can't be blocked now.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB45309)
            )
        } else {
            Text("Blocked until", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEachIndexed { i, c -> ParkChip(c.label, selected = i == untilIndex, onClick = { untilIndex = i }) }
            }
            if (bay.nextBookingAt != null) {
                Text(
                    "Booked at ${clockTime(bay.nextBookingAt)}, so it can be blocked until then at most.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            PrimaryButton(
                text = "Block ${bay.spotNumber}",
                onClick = { until?.let { onBlock(it.ms, reason, plate) } },
                enabled = until != null,
                loading = busy
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FreeBaySheet(bay: BayView, busy: Boolean, onFree: () -> Unit) {
    val block = bay.booking ?: return
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Bay ${bay.spotNumber} is blocked", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        val reason = BlockReason.entries.firstOrNull { it.name == block.blockReason }?.label ?: "Blocked by you"
        Text(
            buildString {
                append(reason)
                if (block.vehicleNumber.isNotBlank()) append(" · ${block.vehicleNumber}")
                append(" · until ${clockTime(block.exitMs())}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        PrimaryButton("Free this bay", onClick = onFree, loading = busy)
    }
}

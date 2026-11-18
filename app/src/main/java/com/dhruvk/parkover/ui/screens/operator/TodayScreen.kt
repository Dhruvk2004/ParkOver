package com.dhruvk.parkover.ui.screens.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.domain.TodayTab
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.ParkChip
import com.dhruvk.parkover.ui.components.OfflineNotice
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleBackground
import com.dhruvk.parkover.ui.theme.TextOnDarkSecondary
import com.dhruvk.parkover.ui.theme.WarningOrange
import java.util.Calendar

/**
 * Operator home (pages 1C + 2B): who and where, today's three numbers, then today's bookings
 * split by what the operator has to do next.
 */
@Composable
fun TodayScreen(
    operatorName: String,
    parkingName: String,
    bookings: List<Booking>?,
    loadError: String?,
    now: Long,
    grouped: Map<TodayTab, List<Booking>>,
    onOpen: (Booking) -> Unit,
    onScan: () -> Unit,
    onCheckOut: (Booking) -> Unit
) {
    // Late first if anyone is late: that's what needs the operator now.
    var tab by rememberSaveable { mutableStateOf<TodayTab?>(null) }
    val selected = tab ?: if (grouped[TodayTab.Late].orEmpty().isNotEmpty()) TodayTab.Late else TodayTab.Arriving
    val list = grouped[selected].orEmpty()

    Column(Modifier.fillMaxSize().background(BackgroundLight)) {
        Header(operatorName, parkingName, now, grouped)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TodayTab.entries.forEach { t ->
                val count = grouped[t].orEmpty().size
                ParkChip(
                    label = if (count > 0) "${t.label} $count" else t.label,
                    selected = t == selected,
                    onClick = { tab = t }
                )
            }
        }

        if (loadError != null) OfflineNotice(message = loadError, modifier = Modifier.padding(horizontal = 20.dp))

        when {
            bookings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = BottomNavReservedHeight), contentAlignment = Alignment.Center) {
                val (icon, title, message) = emptyFor(selected)
                EmptyState(icon = icon, title = title, message = message)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomNavReservedHeight + 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.id }) { booking ->
                    OperatorBookingCard(
                        booking = booking,
                        tab = selected,
                        now = now,
                        onOpen = { onOpen(booking) },
                        onScan = onScan,
                        onCheckOut = { onCheckOut(booking) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(operatorName: String, parkingName: String, now: Long, grouped: Map<TodayTab, List<Booking>>) {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val today = grouped.values.sumOf { it.size }
    val soon = grouped[TodayTab.Arriving].orEmpty().count { it.entryMs() - now <= 60 * 60_000L }
    val late = grouped[TodayTab.Late].orEmpty().size

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(PurpleBackground)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("OPERATOR · ${clockTime(now)}", style = MaterialTheme.typography.labelSmall, color = TextOnDarkSecondary)
        Text(
            "$greeting${operatorName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(parkingName.ifBlank { "Your parking place" }, style = MaterialTheme.typography.bodyMedium, color = TextOnDarkSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("$today", "Today", Modifier.weight(1f))
            Stat("$soon", "Arriving in 1 h", Modifier.weight(1f))
            Stat("$late", "Running late", Modifier.weight(1f), highlight = late > 0)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (highlight) WarningOrange else Color.White)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextOnDarkSecondary, maxLines = 1)
    }
}

private fun emptyFor(tab: TodayTab) = when (tab) {
    TodayTab.Arriving -> Triple(Icons.Rounded.EventAvailable, "Nobody else due today", "New bookings show up here as soon as a driver books.")
    TodayTab.Late -> Triple(Icons.Rounded.Schedule, "Nobody is late", "Drivers move here 15 minutes after their start time.")
    TodayTab.Parked -> Triple(Icons.Rounded.DirectionsCar, "No cars parked", "Scan a driver's QR code to check them in.")
    TodayTab.Done -> Triple(Icons.Rounded.CheckCircle, "Nothing finished yet", "Cars you check out today are listed here.")
}

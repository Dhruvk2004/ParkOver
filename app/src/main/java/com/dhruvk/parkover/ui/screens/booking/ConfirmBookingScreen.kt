package com.dhruvk.parkover.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.ui.components.BookingStepper
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkChip
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.utils.Constants
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val durationOptions = listOf(1, 2, 3, 4, 6, 8, 12, 24)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBookingScreen(
    flowViewModel: BookingFlowViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val data = flowViewModel.bookingData.collectAsStateWithLifecycle().value
    val spot = flowViewModel.spot.collectAsStateWithLifecycle().value
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val longDate = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    var showTimePicker by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val checkIn = data.checkInTime
    val inPast = checkIn < now - 60_000
    val days = remember {
        (0 until 7).map { offset ->
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
    val selectedDayStart = Calendar.getInstance().apply {
        timeInMillis = checkIn
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun moveToDay(dayStart: Long) {
        val time = Calendar.getInstance().apply { timeInMillis = checkIn }
        val target = Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, time.get(Calendar.MINUTE))
        }.timeInMillis
        flowViewModel.setCheckInTime(if (target < System.currentTimeMillis()) BookingFlowViewModel.nextHalfHour() else target)
    }

    val hasEv = spot?.amenities?.contains("EV Charging") == true
    val hasWheelchair = spot?.amenities?.contains("Wheelchair Access") == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        ParkTopBar(title = "When are you parking?", onBack = onBack)
        BookingStepper(step = 3, total = 5, label = "Time", context = "${data.floorName} · ${data.spotNumber}")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Day", Modifier.padding(horizontal = 20.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(days) { dayStart ->
                        val selected = dayStart == selectedDayStart
                        Column(
                            modifier = Modifier
                                .width(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Purple else Color.White)
                                .border(1.dp, if (selected) Purple else DividerColor, RoundedCornerShape(14.dp))
                                .clickable(role = Role.RadioButton) { moveToDay(dayStart) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (dayStart == days.first()) "Today" else dayFormat.format(Date(dayStart)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else TextSecondary
                            )
                            Text(
                                dateFormat.format(Date(dayStart)),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (selected) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }

            ParkCard(modifier = Modifier.padding(horizontal = 20.dp), onClick = { showTimePicker = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel("Check-in")
                        Text(timeFormat.format(Date(checkIn)), style = MaterialTheme.typography.headlineSmall, color = Purple)
                        Text("Tap to change", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TextTertiary)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        SectionLabel("Check-out")
                        Text(timeFormat.format(Date(data.checkOutTime)), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text(longDate.format(Date(data.checkOutTime)), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
                if (inPast) {
                    Spacer(Modifier.height(6.dp))
                    Text("That time has passed. Pick a later check-in.", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Duration")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    durationOptions.forEach { hours ->
                        ParkChip(
                            label = if (hours == Constants.MAX_BOOKING_HOURS) "Full day" else "${hours}h",
                            selected = data.durationHours == hours,
                            onClick = { flowViewModel.setDuration(hours) }
                        )
                    }
                }
            }

            if (hasEv || hasWheelchair) {
                ParkCard(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    if (hasWheelchair) {
                        ToggleRow(Icons.Rounded.Accessible, "Wheelchair-access bay", data.needsWheelchairAccess) {
                            flowViewModel.setSpecifications(it, data.needsEvCharging)
                        }
                    }
                    if (hasEv) {
                        ToggleRow(Icons.Rounded.EvStation, "EV charging", data.needsEvCharging) {
                            flowViewModel.setSpecifications(data.needsWheelchairAccess, it)
                        }
                    }
                }
            }

            ParkCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                PriceLine("Parking · ${data.durationHours}h × ${formatRupees(data.pricePerHour)}", formatRupees(data.basePrice, true))
                PriceLine("Taxes (${(Constants.BOOKING_TAX_RATE * 100).toInt()}%)", formatRupees(data.taxAmount, true))
            }
        }

        BottomActionBar(
            summary = {
                Text(formatRupees(data.totalPrice, true), style = MaterialTheme.typography.titleLarge)
                Text("${data.durationHours}h · incl. tax", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        ) {
            PrimaryButton(
                text = "Review",
                enabled = !inPast,
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showTimePicker) {
        val initial = Calendar.getInstance().apply { timeInMillis = checkIn }
        val state = rememberTimePickerState(
            initialHour = initial.get(Calendar.HOUR_OF_DAY),
            initialMinute = initial.get(Calendar.MINUTE),
            is24Hour = false
        )
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color.White,
            title = { Text("Check-in time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(
                        state = state,
                        colors = TimePickerDefaults.colors(
                            selectorColor = Purple,
                            timeSelectorSelectedContainerColor = PurpleTint,
                            timeSelectorSelectedContentColor = Purple,
                            periodSelectorSelectedContainerColor = PurpleTint,
                            periodSelectorSelectedContentColor = Purple
                        )
                    )
                    error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val picked = Calendar.getInstance().apply {
                        timeInMillis = selectedDayStart
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                    }.timeInMillis
                    if (picked < System.currentTimeMillis()) {
                        error = "That time has passed today. Pick a later time or another day."
                    } else {
                        flowViewModel.setCheckInTime(picked)
                        showTimePicker = false
                    }
                }) { Text("Set time", color = Purple, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Purple))
    }
}

@Composable
internal fun PriceLine(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) TextPrimary else TextSecondary
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) Purple else TextPrimary,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

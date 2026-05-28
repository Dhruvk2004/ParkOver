package com.example.parkover.ui.screens.booking

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBookingScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    // Default check-in: next hour
    val defaultCheckIn = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
    }

    var checkInTime by remember { mutableLongStateOf(defaultCheckIn) }
    var duration by remember { mutableFloatStateOf(4f) }
    var wheelchairAccess by remember { mutableStateOf(false) }
    var evCharging by remember { mutableStateOf(false) }

    val checkOutTime = checkInTime + (duration.toLong() * 3600000L)
    val pricePerHour = 50.0 // Will come from booking data
    val basePrice = pricePerHour * duration.toInt()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Confirm Booking") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            // Check-in time
            Text("Check-in Time", fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = checkInTime }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val selected = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                            }
                            if (selected.timeInMillis > System.currentTimeMillis()) {
                                checkInTime = selected.timeInMillis
                            }
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = timeFormat.format(Date(checkInTime)),
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Check-out time (calculated)
            Text("Check-out Time", fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeFormat.format(Date(checkOutTime)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Duration slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duration", fontSize = 14.sp, color = TextSecondary)
                Text(
                    text = "${duration.toInt()} hours - ₹${String.format("%.0f", basePrice)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Purple
                )
            }

            Slider(
                value = duration,
                onValueChange = { duration = it },
                valueRange = 1f..24f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = Purple,
                    activeTrackColor = Purple
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Specifications
            Text(
                text = "Specifications",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wheelchair Access", fontSize = 14.sp, color = TextPrimary)
                        Switch(
                            checked = wheelchairAccess,
                            onCheckedChange = { wheelchairAccess = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Purple)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EV Charging", fontSize = 14.sp, color = TextPrimary)
                        Switch(
                            checked = evCharging,
                            onCheckedChange = { evCharging = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Purple)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

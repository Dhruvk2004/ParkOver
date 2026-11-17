package com.dhruvk.parkover.ui.screens.booking

import com.dhruvk.parkover.ui.components.OfflineNotice
import com.dhruvk.parkover.ui.components.rememberDebouncedOffline
import com.dhruvk.parkover.ui.components.rememberIsOnline
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.data.model.PaymentMethod
import com.dhruvk.parkover.ui.components.BookingStepper
import com.dhruvk.parkover.ui.components.BottomActionBar
import com.dhruvk.parkover.ui.components.MenuDivider
import com.dhruvk.parkover.ui.components.MenuRow
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.formatRupees
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.viewmodels.AccountViewModel
import com.dhruvk.parkover.viewmodels.BookingFlowViewModel
import com.dhruvk.parkover.viewmodels.SubmitState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentScreen(
    flowViewModel: BookingFlowViewModel,
    accountViewModel: AccountViewModel,
    onBack: () -> Unit,
    onAddUpi: () -> Unit,
    onPaymentSuccess: (bookingId: String) -> Unit
) {
    val data = flowViewModel.bookingData.collectAsStateWithLifecycle().value
    val submit = flowViewModel.submitState.collectAsStateWithLifecycle().value
    val online by rememberIsOnline()
    val showOfflineNotice by rememberDebouncedOffline(online)
    val upiIds = accountViewModel.upiIds.collectAsStateWithLifecycle().value
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Default UPI ID preselected
    LaunchedEffect(upiIds) {
        if (data.paymentMethod == PaymentMethod.UPI && data.upiId.isEmpty()) {
            upiIds.firstOrNull()?.let { flowViewModel.setPaymentMethod(PaymentMethod.UPI, it.vpa) }
        }
    }
    LaunchedEffect(submit) {
        if (submit is SubmitState.Done) onPaymentSuccess(submit.bookingId)
    }

    val holdUntil = data.checkInTime + 15 * 60_000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        ParkTopBar(title = "Payment", onBack = onBack)
        BookingStepper(step = 5, total = 5, label = "Pay")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SectionLabel("Amount to pay")
                Text(formatRupees(data.totalPrice, true), style = MaterialTheme.typography.displayLarge, color = TextPrimary)
                Text(
                    "${data.parkingName} · ${data.spotNumber} · ${data.durationHours}h",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            SectionLabel("UPI")
            ParkCard(contentPadding = PaddingValues(0.dp)) {
                upiIds.forEachIndexed { index, upi ->
                    MethodRow(
                        icon = Icons.Rounded.QrCode2,
                        title = upi.vpa,
                        subtitle = if (upi.isDefault) "Default" else null,
                        selected = data.paymentMethod == PaymentMethod.UPI && data.upiId == upi.vpa,
                        onClick = { flowViewModel.setPaymentMethod(PaymentMethod.UPI, upi.vpa) }
                    )
                    if (index < upiIds.lastIndex) MenuDivider()
                }
                if (upiIds.isNotEmpty()) MenuDivider()
                MenuRow(
                    icon = Icons.Rounded.Add,
                    title = if (upiIds.isEmpty()) "Add a UPI ID" else "Use another UPI ID",
                    titleColor = Purple,
                    onClick = onAddUpi,
                    trailing = null
                )
            }

            SectionLabel("Other ways")
            ParkCard(contentPadding = PaddingValues(0.dp)) {
                MethodRow(
                    icon = Icons.Rounded.Payments,
                    title = "Pay at parking",
                    subtitle = "Cash or UPI at the gate · spot held until ${timeFormat.format(Date(holdUntil))}",
                    selected = data.paymentMethod == PaymentMethod.CASH,
                    onClick = { flowViewModel.setPaymentMethod(PaymentMethod.CASH) }
                )
                MenuDivider()
                MethodRow(
                    icon = Icons.Rounded.CreditCard,
                    title = "Card",
                    subtitle = "Coming with online payments",
                    selected = false,
                    enabled = false,
                    onClick = {}
                )
                MenuDivider()
                MethodRow(
                    icon = Icons.Rounded.AccountBalance,
                    title = "Net banking",
                    subtitle = "Coming with online payments",
                    selected = false,
                    enabled = false,
                    onClick = {}
                )
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "No money is taken in the app yet. Confirming reserves your spot, and you pay at the parking with the method you picked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (submit is SubmitState.Error) {
                Text(submit.message, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
            }
            // Your choices stay; booking waits until the connection is back.
            AnimatedVisibility(visible = showOfflineNotice) {
                OfflineNotice(message = "Your choices are kept. You can confirm once you're back online.")
            }
        }

        BottomActionBar {
            PrimaryButton(
                text = "Confirm booking · ${formatRupees(data.totalPrice, true)}",
                enabled = online,
                loading = submit is SubmitState.Submitting,
                onClick = {
                    flowViewModel.clearSubmitError()
                    flowViewModel.confirmBooking()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    MenuRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { if (enabled) onClick() },
        titleColor = if (enabled) TextPrimary else TextTertiary,
        tint = if (enabled) Purple else TextTertiary,
        trailing = {
            if (enabled) {
                RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Purple))
            } else {
                StatusBadge("Soon", BadgeTone.Neutral)
            }
        }
    )
}

package com.dhruvk.parkover.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.MenuDivider
import com.dhruvk.parkover.ui.components.MenuRow
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.viewmodels.AccountViewModel

/**
 * Saved UPI IDs. Cards are deliberately not collected here: once a payment gateway is added,
 * cards are entered in the gateway's own tokenised sheet and only the last 4 digits are stored.
 */
@Composable
fun PaymentMethodsScreen(accountViewModel: AccountViewModel, onBack: () -> Unit) {
    val upiIds = accountViewModel.upiIds.collectAsStateWithLifecycle().value
    val message = accountViewModel.message.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }
    var showAddUpi by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            accountViewModel.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParkTopBar(title = "Payment methods", onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("UPI IDs")
                ParkCard(contentPadding = PaddingValues(0.dp)) {
                    upiIds.forEach { upi ->
                        MenuRow(
                            icon = Icons.Rounded.QrCode2,
                            title = upi.vpa,
                            subtitle = if (upi.isDefault) "Default for bookings" else null,
                            onClick = {},
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (upi.isDefault) StatusBadge("Default", BadgeTone.Brand)
                                    IconButton(onClick = { accountViewModel.removeUpiId(upi.id) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove ${upi.vpa}", tint = TextTertiary)
                                    }
                                }
                            }
                        )
                        MenuDivider()
                    }
                    MenuRow(
                        icon = Icons.Rounded.Add,
                        title = "Add UPI ID",
                        titleColor = Purple,
                        onClick = { showAddUpi = true },
                        trailing = null
                    )
                }

                SectionLabel("Cards", Modifier.padding(top = 8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Purple, CardDark)))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    Text("Cards arrive with online payments", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "You'll add a card in the payment gateway's secure sheet at checkout. ParkOver will only keep the last 4 digits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Your default UPI ID is preselected when you book. No money is taken in the app yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showAddUpi) {
        var vpa by remember { mutableStateOf("") }
        var makeDefault by remember { mutableStateOf(upiIds.isEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddUpi = false },
            containerColor = Color.White,
            title = { Text("Add UPI ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParkTextField(
                        value = vpa,
                        onValueChange = { vpa = it.trim(); error = null },
                        label = "UPI ID",
                        placeholder = "name@okaxis",
                        error = error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = makeDefault,
                            onCheckedChange = { makeDefault = it },
                            colors = CheckboxDefaults.colors(checkedColor = Purple)
                        )
                        Text("Use for bookings by default", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        !AccountViewModel.UPI_REGEX.matches(vpa) -> error = "Enter a UPI ID like name@okaxis"
                        upiIds.any { it.vpa.equals(vpa, ignoreCase = true) } -> error = "You've already added this UPI ID"
                        else -> {
                            accountViewModel.addUpiId(vpa, makeDefault)
                            showAddUpi = false
                        }
                    }
                }) { Text("Add", color = Purple, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showAddUpi = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

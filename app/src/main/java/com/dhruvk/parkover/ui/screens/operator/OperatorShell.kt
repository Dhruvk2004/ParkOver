package com.dhruvk.parkover.ui.screens.operator

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.repository.OperatorProfile
import com.dhruvk.parkover.ui.components.MenuDivider
import com.dhruvk.parkover.ui.components.MenuRow
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.screens.main.BottomNavReservedHeight
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.NavInactive
import com.dhruvk.parkover.ui.theme.NavSurface
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleDark
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.OperatorViewModel

enum class OperatorTab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.Today),
    Bays("Bays", Icons.Rounded.GridView),
    Account("Account", Icons.Rounded.Person)
}

/**
 * The operator's side of the app (page 1C: the account decides; drivers never see this).
 * Tabs: Today, Bays, Account, with Scan in the middle of the bar because it's the most used
 * action at the gate.
 */
@Composable
fun OperatorShell(
    profile: OperatorProfile,
    viewModel: OperatorViewModel,
    onScan: () -> Unit,
    onOpenBooking: (String) -> Unit,
    onDriverMode: () -> Unit,
    onLogout: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(OperatorTab.Today) }
    val bookings by viewModel.bookings.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val now by viewModel.now.collectAsState()
    val spot by viewModel.spot.collectAsState()
    var checkOut by remember { mutableStateOf<Booking?>(null) }
    val grouped = remember(bookings, now) { viewModel.today(bookings.orEmpty(), now) }

    Box(Modifier.fillMaxSize().background(BackgroundLight)) {
        when (tab) {
            OperatorTab.Today -> TodayScreen(
                operatorName = profile.name,
                parkingName = profile.parkingName.ifBlank { spot?.name.orEmpty() },
                bookings = bookings,
                loadError = loadError,
                now = now,
                grouped = grouped,
                onOpen = { onOpenBooking(it.id) },
                onScan = onScan,
                onCheckOut = { checkOut = it }
            )
            OperatorTab.Bays -> BaysScreen(viewModel = viewModel, onOpenBooking = onOpenBooking)
            OperatorTab.Account -> OperatorAccount(
                profile = profile,
                parkingName = profile.parkingName.ifBlank { spot?.name.orEmpty() },
                address = spot?.address.orEmpty(),
                onDriverMode = onDriverMode,
                onLogout = onLogout
            )
        }

        OperatorNavBar(
            selected = tab,
            onSelect = { tab = it },
            onScan = onScan,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    checkOut?.let { b ->
        CheckOutDialog(
            booking = b,
            now = now,
            onConfirm = { checkOut = null; viewModel.checkOut(b) },
            onDismiss = { checkOut = null }
        )
    }
}

/** Same floating pill as the driver's bar, with a round Scan button in the middle. */
@Composable
private fun OperatorNavBar(
    selected: OperatorTab,
    onSelect: (OperatorTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(8.dp, CircleShape, spotColor = PurpleDark)
                .clip(CircleShape)
                .background(NavSurface)
                .selectableGroup()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(OperatorTab.Today, selected == OperatorTab.Today) { onSelect(OperatorTab.Today) }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PurpleDark)
                    .selectable(selected = false, onClick = onScan, role = Role.Button),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan a driver's QR code", tint = Color.White)
            }
            NavItem(OperatorTab.Bays, selected == OperatorTab.Bays) { onSelect(OperatorTab.Bays) }
            NavItem(OperatorTab.Account, selected == OperatorTab.Account) { onSelect(OperatorTab.Account) }
        }
    }
}

@Composable
private fun NavItem(tab: OperatorTab, isSelected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (isSelected) Purple else Color.Transparent, tween(200), label = "op_nav_bg")
    Row(
        Modifier
            .height(48.dp)
            .defaultMinSize(minWidth = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .selectable(selected = isSelected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = if (isSelected) null else tab.label, tint = if (isSelected) Color.White else NavInactive, modifier = Modifier.size(22.dp))
        if (isSelected) {
            Spacer(Modifier.width(6.dp))
            Text(tab.label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun OperatorAccount(
    profile: OperatorProfile,
    parkingName: String,
    address: String,
    onDriverMode: () -> Unit,
    onLogout: () -> Unit
) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = BottomNavReservedHeight),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        ParkCard {
            Text(profile.name.ifBlank { "Operator" }, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Operator of $parkingName", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(
            "ParkOver set this account up as the operator of this parking place, so the app opens here every time.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        SectionLabel("Switch")
        ParkCard(contentPadding = PaddingValues(0.dp)) {
            MenuRow(
                icon = Icons.Rounded.DirectionsCar,
                title = "Book parking as a driver",
                subtitle = "Find and book parking anywhere, like any driver",
                onClick = onDriverMode
            )
            MenuDivider()
            MenuRow(
                icon = Icons.AutoMirrored.Rounded.Logout,
                title = "Log out",
                tint = ErrorRed,
                titleColor = ErrorRed,
                iconContainer = Color(0xFFFEE2E2),
                onClick = { confirmLogout = true },
                trailing = null
            )
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("Cars can't be checked in from this phone until you log in again.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Log out", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = Color.White
        )
    }
}

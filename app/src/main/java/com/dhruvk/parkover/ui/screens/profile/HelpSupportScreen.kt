package com.dhruvk.parkover.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.IconTile
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.components.SectionLabel
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.viewmodels.AccountViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val faqs = listOf(
    "How do I cancel a booking?" to "Open Bookings, find the booking under Upcoming and tap Cancel booking. The spot is released straight away.",
    "My QR code didn't scan" to "Turn your screen brightness up and hold the phone flat under the scanner. If it still fails, show the booking ID printed under the QR code to the attendant.",
    "When do I pay?" to "Payments aren't taken in the app yet. You pay at the parking using the method you picked when booking.",
    "Can I extend my parking time?" to "Yes. While a booking is active, tap Extend on its card in Bookings to add an hour.",
    "How do I change my vehicle?" to "Go to Profile → My vehicles to add, delete or set a default vehicle. For a booking you've already made, cancel it and book again with the other vehicle.",
    "Why is the map showing Mumbai?" to "ParkOver uses your location when you allow it. If location is off, the map opens on Mumbai, where our parkings are."
)

private val categories = listOf(
    "Booking issue", "Payment problem", "App bug or crash", "Account issue", "Parking feedback", "Feature request", "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(accountViewModel: AccountViewModel, onBack: () -> Unit) {
    val bookings = accountViewModel.bookings.collectAsStateWithLifecycle().value
    val busy = accountViewModel.busy.collectAsStateWithLifecycle().value
    val message = accountViewModel.message.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    var formFor by remember { mutableStateOf<Pair<String, Booking?>?>(null) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            accountViewModel.clearMessage()
        }
    }

    val latestBooking = bookings?.maxByOrNull { it.entryTime.toDate() }
    val shownFaqs = faqs.withIndex().filter { (_, faq) ->
        query.isBlank() || faq.first.contains(query, true) || faq.second.contains(query, true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParkTopBar(title = "Help", onBack = onBack)

            if (submitted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.CheckCircle,
                        title = "We've got your request",
                        message = "Our team usually replies within 24 hours, by email."
                    ) {
                        PrimaryButton(text = "Back to profile", onClick = onBack)
                    }
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search help", style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                            cursorBrush = SolidColor(Purple),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                latestBooking?.let { booking ->
                    val format = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PurpleTint)
                            .clickable { formFor = "Booking issue" to booking }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(Icons.Rounded.ReceiptLong, container = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Problem with a booking?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "${booking.parkingName} · ${format.format(booking.entryTime.toDate())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Purple)
                    }
                }

                SectionLabel("Common questions", Modifier.padding(top = 4.dp))
                ParkCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                    if (shownFaqs.isEmpty()) {
                        Text(
                            "No answers match “$query”. Write to us below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    shownFaqs.forEachIndexed { position, (index, faq) ->
                        val open = expanded == index
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = if (open) null else index }
                                .padding(vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    faq.first,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (open) "Collapse" else "Expand",
                                    tint = if (open) Purple else TextTertiary
                                )
                            }
                            AnimatedVisibility(visible = open) {
                                Text(
                                    faq.second,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        if (position < shownFaqs.lastIndex) HorizontalDivider(color = DividerColor)
                    }
                }

                SectionLabel("Still stuck?", Modifier.padding(top = 4.dp))
                SecondaryButton(
                    text = "Write to us",
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    onClick = { formFor = "" to null }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    formFor?.let { (initialCategory, booking) ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var category by remember { mutableStateOf(initialCategory) }
        var subject by remember { mutableStateOf(booking?.let { "Booking ${it.id} at ${it.parkingName}" } ?: "") }
        var description by remember { mutableStateOf("") }
        var menuOpen by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        ModalBottomSheet(onDismissRequest = { formFor = null }, sheetState = sheetState, containerColor = Color.White) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Write to us", style = MaterialTheme.typography.titleLarge)
                ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                    ParkTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = "Topic",
                        placeholder = "Pick a topic",
                        trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Color.White) {
                        categories.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { category = option; menuOpen = false })
                        }
                    }
                }
                ParkTextField(value = subject, onValueChange = { subject = it }, label = "Subject")
                ParkTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = "What happened?",
                    placeholder = "Tell us what you tried and what you expected",
                    singleLine = false,
                    minLines = 4,
                    error = error
                )
                PrimaryButton(
                    text = "Send",
                    loading = busy,
                    onClick = {
                        when {
                            category.isBlank() -> error = "Pick a topic first"
                            description.trim().length < 10 -> error = "Add a few more details (at least 10 characters)"
                            else -> accountViewModel.submitTicket(category, subject.trim(), description.trim(), booking?.id) {
                                formFor = null
                                submitted = true
                            }
                        }
                    }
                )
            }
        }
    }
}

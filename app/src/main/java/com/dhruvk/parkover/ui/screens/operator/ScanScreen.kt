package com.dhruvk.parkover.ui.screens.operator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.PaymentStatus
import com.dhruvk.parkover.domain.OperatorRules
import com.dhruvk.parkover.ui.components.BadgeTone
import com.dhruvk.parkover.ui.components.EmptyState
import com.dhruvk.parkover.ui.components.NumberPlate
import com.dhruvk.parkover.ui.components.ParkCard
import com.dhruvk.parkover.ui.components.ParkTextField
import com.dhruvk.parkover.ui.components.ParkTopBar
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SecondaryButton
import com.dhruvk.parkover.ui.components.StatusBadge
import com.dhruvk.parkover.ui.components.SystemBarIcons
import com.dhruvk.parkover.ui.components.formatPlate
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.viewmodels.OperatorViewModel
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Check-in (page 3, A then B): a full-screen camera for the driver's QR code; once it reads one,
 * the booking slides up with the plate to match and the bay to send them to. If the code won't
 * scan, the operator types the car number instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: OperatorViewModel,
    parkingId: String,
    onClose: () -> Unit,
    onOpenBooking: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var typing by rememberSaveable { mutableStateOf(false) }
    var found by remember { mutableStateOf<Booking?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsState()
    val now by viewModel.now.collectAsState()

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedCamera by rememberSaveable { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        askedCamera = true
        if (!granted) typing = true
    }
    LaunchedEffect(Unit) {
        if (!hasCamera && !askedCamera) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    fun onCode(raw: String) {
        if (found != null || lookingUp) return
        val id = OperatorRules.bookingIdFromQr(raw)
        if (id == null) {
            problem = "That isn't a ParkOver booking code."
            return
        }
        lookingUp = true
        scope.launch {
            val booking = viewModel.findBooking(id)
            lookingUp = false
            when {
                booking == null || booking.parkingId != parkingId ->
                    problem = "This booking isn't for your parking place."
                else -> { problem = null; found = booking }
            }
        }
    }

    // A problem message clears itself so scanning carries on.
    LaunchedEffect(problem) {
        if (problem != null) {
            delay(3_000)
            problem = null
        }
    }

    if (typing || !hasCamera) {
        TypeCarNumber(
            viewModel = viewModel,
            now = now,
            canScan = hasCamera,
            onBack = onClose,
            onScan = {
                if (hasCamera) typing = false else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onPick = { found = it }
        )
    } else {
        Scanner(
            paused = found != null || lookingUp,
            problem = problem,
            lookingUp = lookingUp,
            onCode = ::onCode,
            onClose = onClose,
            onType = { typing = true }
        )
    }

    found?.let { booking ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Fresh from the live list, so a check-in made a moment ago shows straight away.
        val live = viewModel.booking(booking.id) ?: booking
        ModalBottomSheet(
            onDismissRequest = { found = null },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            FoundSheet(
                booking = live,
                now = now,
                busy = busy,
                onMarkArrived = { viewModel.checkIn(live) { found = null; onClose() } },
                onOpen = { found = null; onOpenBooking(live.id) },
                onScanAgain = { found = null }
            )
        }
    }
}

@Composable
private fun Scanner(
    paused: Boolean,
    problem: String?,
    lookingUp: Boolean,
    onCode: (String) -> Unit,
    onClose: () -> Unit,
    onType: () -> Unit
) {
    SystemBarIcons(lightIcons = true)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember {
        DecoratedBarcodeView(context).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            setStatusText("")
        }
    }
    val latestOnCode = androidx.compose.runtime.rememberUpdatedState(onCode)

    DisposableEffect(view) {
        view.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                result.text?.let { latestOnCode.value(it) }
            }
        })
        onDispose { view.pause() }
    }
    DisposableEffect(lifecycleOwner, paused) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!paused) view.resume()
                Lifecycle.Event.ON_PAUSE -> view.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (paused) view.pause()
        else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close scanner", tint = Color.White)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text("Scan booking QR", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ask the driver to open their booking and show the QR code.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val note = when {
                lookingUp -> "Finding the booking…"
                problem != null -> problem
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (problem != null) Color(0xCCB91C1C) else Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            SecondaryButton(
                text = "Type car number instead",
                onClick = onType,
                icon = Icons.Rounded.Keyboard,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** State B: the booking the code belongs to, and the one thing to do with it. */
@Composable
private fun FoundSheet(
    booking: Booking,
    now: Long,
    busy: Boolean,
    onMarkArrived: () -> Unit,
    onOpen: () -> Unit,
    onScanAgain: () -> Unit
) {
    val waiting = booking.bookingStatus == BookingStatus.PENDING || booking.bookingStatus == BookingStatus.CONFIRMED
    val today = OperatorRules.sameDay(booking.entryMs(), now)
    val earlyBy = booking.entryMs() - now
    val blocker: String? = when {
        booking.bookingStatus == BookingStatus.ACTIVE ->
            "Already checked in at ${booking.checkedInAt?.toDate()?.time?.let(::clockTime) ?: "—"}."
        booking.bookingStatus == BookingStatus.CANCELLED -> "The driver cancelled this booking."
        !waiting -> "This booking is already closed."
        !today && earlyBy > 0 -> "This booking is for ${booking.timeRange(now)}, not today."
        now > booking.exitMs() -> "This booking ended at ${clockTime(booking.exitMs())}."
        else -> null
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Booking found", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.size(8.dp))
            if (booking.paymentStatus == PaymentStatus.COMPLETED) StatusBadge("Paid", BadgeTone.Success)
            else StatusBadge("Not paid", BadgeTone.Warning)
            Spacer(Modifier.weight(1f))
            Text("#${booking.id}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PurpleTint).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Check the number plate matches", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            // Big plate: read from a metre away at the gate.
            Text(
                formatPlate(booking.vehicleNumber),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 2.sp,
                color = TextPrimary,
                modifier = Modifier
                    .border(BorderStroke(2.dp, TextPrimary), RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile("Send them to", booking.spotNumber, Modifier.weight(1f), sub = booking.floorName.ifBlank { null })
            InfoTile("Booked for", booking.timeRange(now), Modifier.weight(1f), sub = "${booking.displayName()} · ${booking.vehicleType.shortName()}")
        }

        if (blocker != null) {
            Text(blocker, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB91C1C))
            SecondaryButton("Open booking", onClick = onOpen, modifier = Modifier.fillMaxWidth())
        } else {
            if (earlyBy > 30 * 60_000L) {
                Text(
                    "${OperatorRules.formatSpan(earlyBy)} early: bay ${booking.spotNumber} may still be in use until ${clockTime(booking.entryMs())}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309)
                )
            }
            PrimaryButton("Mark arrived", onClick = onMarkArrived, loading = busy)
        }
        TextButton(onClick = onScanAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Wrong car? Scan again", color = Purple)
        }
    }
}

/** The fallback when the QR won't scan: find the booking by its number plate. */
@Composable
private fun TypeCarNumber(
    viewModel: OperatorViewModel,
    now: Long,
    canScan: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onPick: (Booking) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val bookings by viewModel.bookings.collectAsState()
    val matches = remember(query, bookings, now) { viewModel.searchByPlate(query) }

    Column(Modifier.fillMaxSize().background(BackgroundLight)) {
        ParkTopBar(title = "Find by car number", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ParkTextField(
                value = query,
                onValueChange = { query = it.take(13) },
                label = "Car number",
                placeholder = "e.g. MH01AB1234 or just 1234",
                leading = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                monospace = true
            )
            if (!canScan) {
                Text(
                    "The camera is off for ParkOver, so scanning won't work. You can still find bookings here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            SecondaryButton(
                text = if (canScan) "Scan QR instead" else "Allow camera to scan",
                onClick = onScan,
                icon = Icons.Rounded.QrCodeScanner,
                modifier = Modifier.fillMaxWidth()
            )
        }
        when {
            query.length < 2 -> Unit
            matches.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "No booking with that number",
                    message = "Only today's bookings that haven't checked in are searched."
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(matches, key = { it.id }) { b ->
                    ParkCard(onClick = { onPick(b) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberPlate(b.vehicleNumber)
                            Spacer(Modifier.weight(1f))
                            Text("Bay ${b.spotNumber}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${b.displayName()} · ${b.timeRange(now)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

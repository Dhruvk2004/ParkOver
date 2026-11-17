package com.dhruvk.parkover.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruvk.parkover.ui.theme.BackgroundLight
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.Jost
import com.dhruvk.parkover.ui.theme.ParkOverTheme
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleDark
import com.dhruvk.parkover.ui.theme.PurpleLight
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.StarYellow
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.ui.theme.WarningOrange
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Connectivity
// ---------------------------------------------------------------------------

/**
 * Live "is there usable internet" flag for the default network.
 *
 * Uses [ConnectivityManager.registerDefaultNetworkCallback] and treats a network as online only
 * when it has INTERNET and VALIDATED (so a captive portal or a dead Wi-Fi reads as offline).
 * The callback is unregistered when the caller leaves composition. Always `true` in previews.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    if (LocalInspectionMode.current) return remember { mutableStateOf(true) }

    val context = LocalContext.current.applicationContext
    val cm = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val online = remember(cm) { mutableStateOf(cm.isCurrentlyOnline()) }

    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online.value = caps.isUsable()
            }

            override fun onLost(network: Network) {
                // For the default-network callback this means there is no default network at all.
                online.value = false
            }
        }
        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
        // Re-sync in case the network changed between the initial read and registration.
        online.value = cm.isCurrentlyOnline()
        onDispose {
            if (registered) runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
    return online
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val network = activeNetwork ?: return false
    return getNetworkCapabilities(network)?.isUsable() == true
}

private fun NetworkCapabilities.isUsable(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

/**
 * `true` only after [isOnline] has stayed `false` for [delayMillis]; goes back to `false`
 * immediately when the connection returns. Use it to gate [OfflineNotice] so brief blips
 * (lift, tunnel, Wi-Fi to mobile data handover) never flash anything on screen.
 */
@Composable
fun rememberDebouncedOffline(isOnline: Boolean, delayMillis: Long = 1_500L): State<Boolean> {
    val offline = remember { mutableStateOf(false) }
    LaunchedEffect(isOnline) {
        if (isOnline) {
            offline.value = false
        } else {
            delay(delayMillis)
            offline.value = true
        }
    }
    return offline
}

// ---------------------------------------------------------------------------
// Illustration
// ---------------------------------------------------------------------------

/**
 * A purple car waiting beside a "P" parking signpost whose signal arcs are greyed out, with a
 * slow purple pulse running up the arcs as if it is looking for signal. Decorative only: the
 * text next to it carries the meaning, so it exposes no semantics.
 */
@Composable
fun OfflineIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "offline-signal")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "offline-signal-pulse"
    )
    val textMeasurer = rememberTextMeasurer()
    val signStyle = remember {
        TextStyle(fontFamily = Jost, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
    }

    // Drawn on a 220 x 160 grid and scaled to whatever size the modifier gives.
    Canvas(modifier = modifier.size(width = 220.dp, height = 160.dp)) {
        val s = size.width / 220f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)

        // Soft backdrop blob
        drawCircle(PurpleTint, radius = 72f * s, center = o(110f, 86f))

        // Ground with faded bay markings
        drawLine(DividerColor, o(14f, 140f), o(206f, 140f), strokeWidth = 3f * s, cap = StrokeCap.Round)
        drawLine(
            TextTertiary.copy(alpha = 0.55f), o(26f, 148f), o(194f, 148f),
            strokeWidth = 2f * s, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * s, 8f * s))
        )

        drawSignpost(s, ::o, ::sz, pulse)
        drawCar(s, ::o, ::sz)

        // "P" on the sign board
        val layout = textMeasurer.measure("P", signStyle.copy(fontSize = (22f * s).toSp()))
        drawText(
            layout,
            topLeft = o(170f, 56f) - Offset(layout.size.width / 2f, layout.size.height / 2f)
        )
    }
}

private fun DrawScope.drawSignpost(
    s: Float,
    o: (Float, Float) -> Offset,
    sz: (Float, Float) -> Size,
    pulse: Float
) {
    // Pole
    drawRoundRect(TextSecondary, topLeft = o(168f, 72f), size = sz(4f, 68f), cornerRadius = CornerRadius(2f * s))
    // Board
    drawRoundRect(CardDark, topLeft = o(151f, 37f), size = sz(38f, 38f), cornerRadius = CornerRadius(9f * s))

    // Signal source dot + three arcs opening upwards. Base is grey (no signal); a purple pulse
    // travels outward over each arc in turn, like the sign is searching.
    val centre = o(170f, 30f)
    drawCircle(TextTertiary, radius = 2.6f * s, center = centre)
    val radii = floatArrayOf(7f, 13f, 19f)
    radii.forEachIndexed { i, r ->
        val phase = pulse * 4f - i // each arc lights up in its own quarter, the 4th quarter is rest
        val glow = if (phase in 0f..1f) 1f - kotlin.math.abs(phase - 0.5f) * 2f else 0f
        val color = androidx.compose.ui.graphics.lerp(TextTertiary.copy(alpha = 0.7f), PurpleLight, glow)
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(centre.x - r * s, centre.y - r * s),
            size = Size(2 * r * s, 2 * r * s),
            style = Stroke(width = 3f * s, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawCar(s: Float, o: (Float, Float) -> Offset, sz: (Float, Float) -> Size) {
    // Shadow
    drawOval(Color.Black.copy(alpha = 0.06f), topLeft = o(34f, 135f), size = sz(112f, 9f))

    // Cabin
    val cabin = Path().apply {
        moveTo(o(54f, 102f).x, o(54f, 102f).y)
        lineTo(o(68f, 80f).x, o(68f, 80f).y)
        quadraticTo(o(70f, 77f).x, o(70f, 77f).y, o(74f, 77f).x, o(74f, 77f).y)
        lineTo(o(108f, 77f).x, o(108f, 77f).y)
        quadraticTo(o(112f, 77f).x, o(112f, 77f).y, o(115f, 80f).x, o(115f, 80f).y)
        lineTo(o(132f, 102f).x, o(132f, 102f).y)
        close()
    }
    drawPath(cabin, PurpleDark)

    // Body
    drawRoundRect(Purple, topLeft = o(36f, 98f), size = sz(110f, 32f), cornerRadius = CornerRadius(11f * s))

    // Windows
    val window = Color.White.copy(alpha = 0.88f)
    val rear = Path().apply {
        moveTo(o(64f, 100f).x, o(64f, 100f).y)
        lineTo(o(73f, 84f).x, o(73f, 84f).y)
        lineTo(o(89f, 84f).x, o(89f, 84f).y)
        lineTo(o(89f, 100f).x, o(89f, 100f).y)
        close()
    }
    val front = Path().apply {
        moveTo(o(95f, 100f).x, o(95f, 100f).y)
        lineTo(o(95f, 84f).x, o(95f, 84f).y)
        lineTo(o(109f, 84f).x, o(109f, 84f).y)
        lineTo(o(121f, 100f).x, o(121f, 100f).y)
        close()
    }
    drawPath(rear, window)
    drawPath(front, window)

    // Door line and handle
    drawLine(PurpleDark.copy(alpha = 0.5f), o(92f, 102f), o(92f, 124f), strokeWidth = 1.5f * s)
    drawRoundRect(Color.White.copy(alpha = 0.7f), topLeft = o(97f, 107f), size = sz(8f, 2.5f), cornerRadius = CornerRadius(1.25f * s))

    // Lights
    drawRoundRect(StarYellow, topLeft = o(139f, 105f), size = sz(6f, 6f), cornerRadius = CornerRadius(2f * s))
    drawRoundRect(WarningOrange, topLeft = o(37f, 105f), size = sz(4f, 6f), cornerRadius = CornerRadius(1.5f * s))

    // Wheels
    listOf(64f, 118f).forEach { x ->
        drawCircle(CardDark, radius = 11f * s, center = o(x, 129f))
        drawCircle(DividerColor, radius = 4.5f * s, center = o(x, 129f))
    }
}

// ---------------------------------------------------------------------------
// Full-screen state
// ---------------------------------------------------------------------------

const val OfflineTitle = "Stuck in a no-signal zone"
const val OfflineMessage = "Check your mobile data or Wi-Fi. We'll reconnect on our own once you're back online."

/**
 * Full-screen offline state for a screen that opened without a connection and has nothing to show.
 * Recovery should be automatic (see [rememberIsOnline]); [onRetry] is a manual fallback.
 */
@Composable
fun OfflineScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = OfflineTitle,
    message: String = OfflineMessage
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OfflineIllustration()
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            SecondaryButton(
                text = "Try again",
                onClick = onRetry,
                icon = Icons.Rounded.Refresh,
                contentColor = Purple,
                height = 48.dp,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Inline notice
// ---------------------------------------------------------------------------

/**
 * Calm inline notice for when the connection drops while the user is already looking at content.
 * Same footprint as Home's StatusBanner so it can take that slot under the search/filter row.
 * Announced politely by TalkBack.
 */
@Composable
fun OfflineNotice(
    modifier: Modifier = Modifier,
    title: String = "You're offline",
    message: String = "Showing what we last loaded. We'll refresh when you're back."
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerColor),
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PurpleTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Purple, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFF9FAFB)
@Composable
private fun OfflineIllustrationPreview() {
    ParkOverTheme { OfflineIllustration(Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OfflineScreenPreview() {
    ParkOverTheme { OfflineScreen(onRetry = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9FAFB, widthDp = 360)
@Composable
private fun OfflineNoticePreview() {
    ParkOverTheme { OfflineNotice(Modifier.padding(16.dp)) }
}

package com.dhruvk.parkover.ui.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dhruvk.parkover.ui.theme.CardDark
import com.dhruvk.parkover.ui.theme.DividerColor
import com.dhruvk.parkover.ui.theme.ErrorRed
import com.dhruvk.parkover.ui.theme.Purple
import com.dhruvk.parkover.ui.theme.PurpleDark
import com.dhruvk.parkover.ui.theme.PurpleLight
import com.dhruvk.parkover.ui.theme.PurpleTint
import com.dhruvk.parkover.ui.theme.SuccessGreen
import com.dhruvk.parkover.ui.theme.TextPrimary
import com.dhruvk.parkover.ui.theme.TextSecondary
import com.dhruvk.parkover.ui.theme.TextTertiary
import com.dhruvk.parkover.ui.theme.WarningOrange

/** Status-bar icon colour for screens with a dark top (splash, onboarding, success). */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(lightIcons) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        // Count requests instead of saving/restoring: during a transition the incoming screen's
        // effect runs before the outgoing one is disposed, so a restore would undo the new screen.
        if (lightIcons) lightIconRequests++
        controller.isAppearanceLightStatusBars = lightIconRequests == 0
        onDispose {
            if (lightIcons) lightIconRequests--
            controller.isAppearanceLightStatusBars = lightIconRequests == 0
        }
    }
}

private var lightIconRequests = 0

/** Top bar used by every pushed screen: square back button + title + optional actions. */
@Composable
fun ParkTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            SquareIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    container: Color = Color.White,
    bordered: Boolean = true
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .then(if (bordered) Modifier.border(1.dp, DividerColor, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    container: Color = Purple,
    content: Color = Color.White
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.35f),
            disabledContentColor = content.copy(alpha = 0.8f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = content, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    contentColor: Color = TextPrimary,
    height: Dp = 54.dp,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, DividerColor),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp)
    }
}

@Composable
fun ParkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    monospace: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = TextTertiary) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = trailing,
        leadingIcon = leading,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge.let {
            if (monospace) it.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) else it
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple,
            focusedLabelColor = Purple,
            cursorColor = Purple,
            unfocusedBorderColor = DividerColor,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            errorContainerColor = Color.White
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ParkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) Purple else Color.White)
            .then(if (selected) Modifier else Modifier.border(1.dp, DividerColor, shape))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1
        )
    }
}

enum class BadgeTone { Success, Warning, Error, Brand, Neutral }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        BadgeTone.Success -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        BadgeTone.Warning -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        BadgeTone.Error -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        BadgeTone.Brand -> PurpleTint to PurpleDark
        BadgeTone.Neutral -> Color(0xFFF3F4F6) to TextSecondary
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** Indian-style number plate chip. */
@Composable
fun NumberPlate(number: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.5.dp, TextPrimary, RoundedCornerShape(5.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1D4ED8))
                .padding(horizontal = 3.dp, vertical = 5.dp)
        ) {
            Text("IND", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = formatPlate(number),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

fun formatPlate(raw: String): String {
    val clean = raw.replace(" ", "").uppercase()
    val match = Regex("^([A-Z]{2})([0-9]{1,2})([A-Z]{1,2})([0-9]{4})$").find(clean) ?: return raw.uppercase()
    return match.groupValues.drop(1).joinToString(" ")
}

@Composable
fun AvailabilityBar(fraction: Float, modifier: Modifier = Modifier, color: Color = availabilityColor(fraction)) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(DividerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

fun availabilityColor(fraction: Float): Color = when {
    fraction <= 0f -> ErrorRed
    fraction < 0.2f -> WarningOrange
    else -> SuccessGreen
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        letterSpacing = 0.8.sp,
        modifier = modifier
    )
}

@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Purple,
    container: Color = PurpleTint,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Bordered card with no shadow: the default container for content blocks. */
@Composable
fun ParkCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = if (selected) Color(0xFFFBFAFF) else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Purple else DividerColor),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun MenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = Purple,
    titleColor: Color = TextPrimary,
    iconContainer: Color = PurpleTint,
    trailing: (@Composable () -> Unit)? = {
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextTertiary)
    }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon = icon, tint = tint, container = iconContainer, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun MenuDivider() {
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 64.dp))
}

/** "Step 2 of 5 · Spot" caption + segmented progress used across the booking flow. */
@Composable
fun BookingStepper(step: Int, total: Int, label: String, context: String = "", modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Step $step of $total · $label",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = context,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < step) Purple else DividerColor)
                )
            }
        }
    }
}

/** Sticky bottom bar: summary on the left, primary action on the right. */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    summary: (@Composable ColumnScope.() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit
) {
    Surface(color = Color.White, shadowElevation = 12.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = DividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (summary != null) {
                    Column(content = summary)
                }
                action()
            }
        }
    }
}

@Composable
fun PriceText(amount: Double, suffix: String = "", modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(
            text = formatRupees(amount),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (suffix.isNotEmpty()) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }
    }
}

fun formatRupees(amount: Double, decimals: Boolean = false): String =
    if (decimals || amount % 1.0 != 0.0) "₹${String.format("%.2f", amount)}" else "₹${amount.toLong()}"

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PurpleTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Purple, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Dark identity/summary card (XML dark_card colour). */
@Composable
fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardDark, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Purple brand gradient used for parking hero/placeholder images. */
val BrandGradient = listOf(PurpleLight, PurpleDark)

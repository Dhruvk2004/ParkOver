package com.dhruvk.parkover.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.R
import com.dhruvk.parkover.ui.components.PrimaryButton
import com.dhruvk.parkover.ui.components.SystemBarIcons
import com.dhruvk.parkover.ui.theme.NavSurface

private val Night = Color(0xFF120B26)
private val Lavender = Color(0xFFB9A8F7)

@Composable
fun StartScreen(
    onGetStarted: () -> Unit,
    onCreateAccount: () -> Unit = onGetStarted
) {
    SystemBarIcons(lightIcons = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        // XML onboarding photo, restored
        Image(
            painter = painterResource(R.drawable.onboardimage),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Night.copy(alpha = 0.35f),
                        0.22f to Night.copy(alpha = 0f),
                        0.5f to Night.copy(alpha = 0.6f),
                        0.7f to Night
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavSurface.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = NavSurface, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Live availability in Mumbai", style = MaterialTheme.typography.labelMedium, color = NavSurface)
            }

            Text(
                text = "Park in seconds,\nnot in circles.",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "See free spots in real time, book a bay ahead and drive straight to it.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.72f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Feature(Icons.Rounded.LocationOn, "Nearby")
                Feature(Icons.Rounded.Schedule, "Pre-book")
                Feature(Icons.Rounded.QrCode2, "QR entry")
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(text = "Get started", onClick = onGetStarted)

            Text(
                text = buildAnnotatedString {
                    append("New to ParkOver? ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append("Create an account") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCreateAccount)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Lavender, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
    }
}

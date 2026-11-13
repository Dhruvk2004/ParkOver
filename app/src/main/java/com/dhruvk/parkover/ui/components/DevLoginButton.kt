package com.dhruvk.parkover.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.BuildConfig

/**
 * Small "Dev login" pill for the top corner of the auth screens.
 * Only rendered in debug builds; release builds (BuildConfig.DEBUG == false) never show it,
 * and the release DEV_LOGIN_* fields are empty strings.
 */
@Composable
fun DevLoginButton(
    onDevLogin: (email: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!BuildConfig.DEBUG) return

    Surface(
        onClick = { onDevLogin(BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_LOGIN_PASSWORD) },
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, Color(0xFFFDBA74)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.BugReport, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Dev login",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC2410C)
            )
        }
    }
}

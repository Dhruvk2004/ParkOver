package com.dhruvk.parkover.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.ui.theme.NavInactive
import com.dhruvk.parkover.ui.theme.PurpleTint

/** Vehicle picture on a tinted tile; bikes and unknown models get an icon. */
@Composable
fun VehicleThumb(vehicle: Vehicle, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) PurpleTint else Color(0xFFF3F4F6)),
        contentAlignment = Alignment.Center
    ) {
        val res = vehicle.imageRes
        if (res != null) {
            Image(painterResource(res), contentDescription = null, modifier = Modifier.size(width = 58.dp, height = 40.dp))
        } else {
            Icon(
                if (vehicle.type == VehicleType.HEAVY) Icons.Rounded.LocalShipping else Icons.Rounded.TwoWheeler,
                contentDescription = null,
                tint = NavInactive,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

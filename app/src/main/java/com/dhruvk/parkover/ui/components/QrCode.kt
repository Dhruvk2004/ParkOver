package com.dhruvk.parkover.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

@Composable
fun QrCode(data: String, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val bitmap = remember(data) { generateQrBitmap(data) }
    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = "Booking QR code", modifier = modifier.size(size))
    }
}

fun generateQrBitmap(data: String, sizePx: Int = 512): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.rgb(31, 41, 55) else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
} catch (e: Exception) {
    null
}

package com.example.parkover.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// View Extensions
fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.toggleVisibility() {
    visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
}

// Context Extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

// String Extensions
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun String.isValidPassword(): Boolean {
    return this.length >= Constants.MIN_PASSWORD_LENGTH
}

fun String.isValidVehicleNumber(): Boolean {
    val cleanNumber = this.replace(" ", "").uppercase()
    return cleanNumber.matches(Regex(Constants.VEHICLE_NUMBER_REGEX))
}

fun String.formatVehicleNumber(): String {
    val clean = this.replace(" ", "").uppercase()
    return if (clean.length >= 10) {
        "${clean.substring(0, 2)} ${clean.substring(2, 4)} ${clean.substring(4, 6)} ${clean.substring(6)}"
    } else {
        clean
    }
}

// Date Extensions
fun Date.formatToDisplay(): String {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return formatter.format(this)
}

fun Date.formatToTime(): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return formatter.format(this)
}

fun Date.formatToDateTime(): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return formatter.format(this)
}

fun Timestamp.toFormattedDate(): String {
    return this.toDate().formatToDisplay()
}

fun Timestamp.toFormattedTime(): String {
    return this.toDate().formatToTime()
}

fun Timestamp.toFormattedDateTime(): String {
    return this.toDate().formatToDateTime()
}

// Number Extensions
fun Double.formatPrice(): String {
    return "₹${String.format("%.2f", this)}"
}

fun Double.formatDistance(): String {
    return if (this < 1) {
        "${(this * 1000).toInt()} m"
    } else {
        String.format("%.1f km", this)
    }
}

fun Int.formatDuration(): String {
    return when {
        this < 1 -> "Less than an hour"
        this == 1 -> "1 hour"
        this < 24 -> "$this hours"
        this == 24 -> "1 day"
        else -> "${this / 24} days ${this % 24} hours"
    }
}

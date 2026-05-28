package com.example.parkover.ui.screens.booking

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parkover.data.model.Booking
import com.example.parkover.data.model.BookingStatus
import com.example.parkover.data.model.PaymentMethod
import com.example.parkover.data.model.PaymentStatus
import com.example.parkover.data.model.VehicleType
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.ui.theme.Purple
import com.example.parkover.ui.theme.TextPrimary
import com.example.parkover.ui.theme.TextSecondary
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    onPaymentSuccess: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { BookingRepository() }

    var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var isProcessing by remember { mutableStateOf(false) }
    val totalAmount = 220.0 // Will come from BookingFlowViewModel

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Payment") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            // Total amount
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Amount", fontSize = 16.sp, color = TextSecondary)
                    Text(
                        text = "₹${String.format("%.2f", totalAmount)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Select Payment Method",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Payment methods
            PaymentMethodCard(
                icon = Icons.Default.QrCode,
                title = "UPI",
                subtitle = "Google Pay, PhonePe, Paytm",
                isSelected = selectedMethod == PaymentMethod.UPI,
                onClick = { selectedMethod = PaymentMethod.UPI }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PaymentMethodCard(
                icon = Icons.Default.AccountBalance,
                title = "Net Banking",
                subtitle = "All major banks supported",
                isSelected = selectedMethod == PaymentMethod.NET_BANKING,
                onClick = { selectedMethod = PaymentMethod.NET_BANKING }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PaymentMethodCard(
                icon = Icons.Default.Money,
                title = "Cash",
                subtitle = "Pay at parking",
                isSelected = selectedMethod == PaymentMethod.CASH,
                onClick = { selectedMethod = PaymentMethod.CASH }
            )
        }

        Button(
            onClick = {
                isProcessing = true
                scope.launch {
                    delay(1500) // Simulate payment
                    // Create booking
                    val booking = Booking(
                        parkingId = "",
                        parkingName = "Parking Spot",
                        parkingAddress = "Address",
                        vehicleId = "",
                        vehicleNumber = "XX00XX0000",
                        vehicleType = VehicleType.FOUR_WHEELER,
                        floorNumber = 1,
                        floorName = "1st Floor",
                        spotNumber = "A01",
                        entryTime = Timestamp(Date(System.currentTimeMillis() + 3600000)),
                        exitTime = Timestamp(Date(System.currentTimeMillis() + 18000000)),
                        durationHours = 4,
                        basePrice = 200.0,
                        taxAmount = 20.0,
                        totalPrice = totalAmount,
                        paymentMethod = selectedMethod,
                        paymentStatus = if (selectedMethod == PaymentMethod.CASH) PaymentStatus.PENDING else PaymentStatus.COMPLETED,
                        bookingStatus = BookingStatus.CONFIRMED,
                        qrCodeData = "PARKOVER-${System.currentTimeMillis()}"
                    )
                    val result = repository.createBooking(booking)
                    result.onSuccess { savedBooking ->
                        onPaymentSuccess(savedBooking.id)
                    }.onFailure {
                        isProcessing = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            enabled = !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (selectedMethod == PaymentMethod.CASH) "Confirm Booking"
                    else "Pay ₹${String.format("%.2f", totalAmount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, Purple, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Purple,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = Purple)
            )
        }
    }
}

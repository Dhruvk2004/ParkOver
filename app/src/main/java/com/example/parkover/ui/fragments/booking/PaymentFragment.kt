package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.parkover.R
import com.example.parkover.data.model.Booking
import com.example.parkover.data.model.BookingData
import com.example.parkover.data.model.BookingStatus
import com.example.parkover.data.model.PaymentMethod
import com.example.parkover.data.model.PaymentStatus
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.databinding.FragmentPaymentBinding
import com.example.parkover.utils.showToast
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

class PaymentFragment : Fragment() {

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!
    
    private var bookingData: BookingData? = null
    private var selectedPaymentMethod = PaymentMethod.UPI
    private val repository = BookingRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bookingData = arguments?.getParcelable("booking_data")
        
        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        val data = bookingData ?: return
        
        binding.tvTotalAmount.text = "₹${String.format("%.2f", data.totalPrice)}"
        binding.btnPay.text = "Pay ₹${String.format("%.2f", data.totalPrice)}"
        
        updatePaymentMethodSelection()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.cardUpi.setOnClickListener {
            selectedPaymentMethod = PaymentMethod.UPI
            updatePaymentMethodSelection()
        }
        
        binding.cardNetBanking.setOnClickListener {
            selectedPaymentMethod = PaymentMethod.NET_BANKING
            updatePaymentMethodSelection()
        }
        
        binding.cardCash.setOnClickListener {
            selectedPaymentMethod = PaymentMethod.CASH
            updatePaymentMethodSelection()
        }
        
        binding.btnPay.setOnClickListener {
            processPayment()
        }
    }

    private fun updatePaymentMethodSelection() {
        // Reset all cards
        val defaultStroke = ContextCompat.getColor(requireContext(), R.color.button_border)
        val selectedStroke = ContextCompat.getColor(requireContext(), R.color.mainpurple)
        
        binding.cardUpi.strokeColor = defaultStroke
        binding.cardNetBanking.strokeColor = defaultStroke
        binding.cardCash.strokeColor = defaultStroke
        
        binding.radioUpi.isChecked = false
        binding.radioNetBanking.isChecked = false
        binding.radioCash.isChecked = false
        
        // Highlight selected
        when (selectedPaymentMethod) {
            PaymentMethod.UPI -> {
                binding.cardUpi.strokeColor = selectedStroke
                binding.cardUpi.strokeWidth = 2
                binding.radioUpi.isChecked = true
            }
            PaymentMethod.NET_BANKING -> {
                binding.cardNetBanking.strokeColor = selectedStroke
                binding.cardNetBanking.strokeWidth = 2
                binding.radioNetBanking.isChecked = true
            }
            PaymentMethod.CASH -> {
                binding.cardCash.strokeColor = selectedStroke
                binding.cardCash.strokeWidth = 2
                binding.radioCash.isChecked = true
            }
            else -> {}
        }
        
        // Update button text for cash
        val data = bookingData ?: return
        binding.btnPay.text = if (selectedPaymentMethod == PaymentMethod.CASH) {
            "Confirm Booking"
        } else {
            "Pay ₹${String.format("%.2f", data.totalPrice)}"
        }
    }

    private fun processPayment() {
        val data = bookingData ?: return
        data.paymentMethod = selectedPaymentMethod
        
        binding.btnPay.isEnabled = false
        binding.btnPay.text = "Processing..."
        
        lifecycleScope.launch {
            // Simulate payment processing
            delay(1500)
            
            // Create booking in Firestore
            val booking = createBookingFromData(data)
            val result = repository.createBooking(booking)
            
            result.onSuccess { savedBooking ->
                navigateToSuccess(savedBooking.id)
            }.onFailure { error ->
                context?.showToast("Booking failed: ${error.message}")
                binding.btnPay.isEnabled = true
                binding.btnPay.text = "Pay ₹${String.format("%.2f", data.totalPrice)}"
            }
        }
    }

    private fun createBookingFromData(data: BookingData): Booking {
        return Booking(
            parkingId = data.parkingId,
            parkingName = data.parkingName,
            parkingAddress = data.parkingAddress,
            vehicleId = data.vehicleId,
            vehicleNumber = data.vehicleNumber,
            vehicleType = data.vehicleType,
            floorNumber = data.floorNumber,
            floorName = data.floorName,
            spotNumber = data.spotNumber,
            entryTime = Timestamp(Date(data.checkInTime)),
            exitTime = Timestamp(Date(data.checkOutTime)),
            durationHours = data.durationHours,
            basePrice = data.basePrice,
            taxAmount = data.taxAmount,
            totalPrice = data.totalPrice,
            paymentMethod = data.paymentMethod,
            paymentStatus = if (data.paymentMethod == PaymentMethod.CASH) {
                PaymentStatus.PENDING
            } else {
                PaymentStatus.COMPLETED
            },
            bookingStatus = BookingStatus.CONFIRMED,
            qrCodeData = "PARKOVER-${System.currentTimeMillis()}"
        )
    }

    private fun navigateToSuccess(bookingId: String) {
        val data = bookingData ?: return
        
        val bundle = Bundle().apply {
            putParcelable("booking_data", data)
            putString("booking_id", bookingId)
        }
        
        findNavController().navigate(R.id.action_payment_to_bookingSuccess, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.parkover.R
import com.example.parkover.data.model.BookingData
import com.example.parkover.databinding.FragmentReviewSummaryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewSummaryFragment : Fragment() {

    private var _binding: FragmentReviewSummaryBinding? = null
    private val binding get() = _binding!!
    
    private var bookingData: BookingData? = null
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewSummaryBinding.inflate(inflater, container, false)
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
        
        // Booking details
        binding.tvParkingName.text = data.parkingName
        binding.tvAddress.text = data.parkingAddress
        binding.tvVehicle.text = "${data.vehicleName} (${data.vehicleNumber})"
        binding.tvSpot.text = "${data.floorName} (${data.spotNumber})"
        binding.tvDate.text = dateFormat.format(Date(data.checkInTime))
        binding.tvDuration.text = "${data.durationHours} hours"
        
        val checkInTime = timeFormat.format(Date(data.checkInTime))
        val checkOutTime = timeFormat.format(Date(data.checkOutTime))
        binding.tvHours.text = "$checkInTime - $checkOutTime"
        
        // Price breakdown
        binding.tvAmount.text = "₹${String.format("%.2f", data.basePrice)}"
        binding.tvTaxes.text = "₹${String.format("%.2f", data.taxAmount)}"
        binding.tvTotal.text = "₹${String.format("%.2f", data.totalPrice)}"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnContinue.setOnClickListener {
            navigateToPayment()
        }
    }

    private fun navigateToPayment() {
        val data = bookingData ?: return
        
        val bundle = Bundle().apply {
            putParcelable("booking_data", data)
        }
        
        findNavController().navigate(R.id.action_reviewSummary_to_payment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

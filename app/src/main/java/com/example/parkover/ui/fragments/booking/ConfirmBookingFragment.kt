package com.example.parkover.ui.fragments.booking

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.parkover.R
import com.example.parkover.data.model.BookingData
import com.example.parkover.databinding.FragmentConfirmBookingBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ConfirmBookingFragment : Fragment() {

    private var _binding: FragmentConfirmBookingBinding? = null
    private val binding get() = _binding!!
    
    private var bookingData: BookingData? = null
    private val calendar = Calendar.getInstance()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bookingData = arguments?.getParcelable("booking_data")
        
        setupUI()
        setupClickListeners()
        setupSlider()
    }

    private fun setupUI() {
        val data = bookingData ?: return
        
        // Set default check-in time to next hour
        calendar.add(Calendar.HOUR_OF_DAY, 1)
        calendar.set(Calendar.MINUTE, 0)
        data.checkInTime = calendar.timeInMillis
        
        // Default duration
        data.durationHours = 4
        updateCheckOutTime()
        updateDurationDisplay()
        updateTimeDisplay()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnCheckInTime.setOnClickListener {
            showTimePicker()
        }
        
        binding.btnContinue.setOnClickListener {
            navigateToReviewSummary()
        }
    }

    private fun setupSlider() {
        binding.sliderDuration.value = 4f
        
        binding.sliderDuration.addOnChangeListener { _, value, _ ->
            bookingData?.let { data ->
                data.durationHours = value.toInt()
                updateCheckOutTime()
                updateDurationDisplay()
            }
        }
    }

    private fun showTimePicker() {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                
                bookingData?.let { data ->
                    data.checkInTime = calendar.timeInMillis
                    updateCheckOutTime()
                    updateTimeDisplay()
                }
            },
            hour,
            minute,
            false
        ).show()
    }

    private fun updateCheckOutTime() {
        val data = bookingData ?: return
        
        val checkOutCalendar = Calendar.getInstance().apply {
            timeInMillis = data.checkInTime
            add(Calendar.HOUR_OF_DAY, data.durationHours)
        }
        data.checkOutTime = checkOutCalendar.timeInMillis
        
        binding.tvCheckOutTime.text = timeFormat.format(checkOutCalendar.time)
    }

    private fun updateDurationDisplay() {
        val data = bookingData ?: return
        data.calculatePricing()
        
        val durationText = "${data.durationHours} hours - ₹${String.format("%.0f", data.basePrice)}"
        binding.tvDurationPrice.text = durationText
    }

    private fun updateTimeDisplay() {
        val data = bookingData ?: return
        binding.tvCheckInTime.text = timeFormat.format(data.checkInTime)
    }

    private fun navigateToReviewSummary() {
        val data = bookingData ?: return
        
        // Update specifications
        data.needsWheelchairAccess = binding.switchWheelchair.isChecked
        data.needsEvCharging = binding.switchEvCharging.isChecked
        
        // Calculate final pricing
        data.calculatePricing()
        
        val bundle = Bundle().apply {
            putParcelable("booking_data", data)
        }
        
        findNavController().navigate(R.id.action_confirmBooking_to_reviewSummary, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

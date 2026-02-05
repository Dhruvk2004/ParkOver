package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.parkover.R
import com.example.parkover.data.model.Booking
import com.example.parkover.data.model.BookingStatus
import com.example.parkover.databinding.FragmentBookingBinding
import com.example.parkover.ui.adapters.BookingAdapter
import com.example.parkover.utils.showToast

class BookingFragment : Fragment() {

    private var _binding: FragmentBookingBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: BookingAdapter
    private var isOngoingSelected = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupTabs()
        loadBookings()
    }

    private fun setupAdapter() {
        adapter = BookingAdapter { booking ->
            context?.showToast("Opening booking: ${booking.parkingName}")
        }
        
        binding.rvBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BookingFragment.adapter
        }
    }

    private fun setupTabs() {
        updateTabUI()
        
        binding.btnOngoing.setOnClickListener {
            if (!isOngoingSelected) {
                isOngoingSelected = true
                updateTabUI()
                loadBookings()
            }
        }
        
        binding.btnCompleted.setOnClickListener {
            if (isOngoingSelected) {
                isOngoingSelected = false
                updateTabUI()
                loadBookings()
            }
        }
    }

    private fun updateTabUI() {
        if (isOngoingSelected) {
            binding.btnOngoing.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            binding.btnOngoing.setTextColor(ContextCompat.getColor(requireContext(), R.color.mainpurple))
            binding.btnOngoing.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.mainpurple)
            binding.btnOngoing.strokeWidth = 2
            
            binding.btnCompleted.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.dark_card)
            binding.btnCompleted.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.btnCompleted.strokeWidth = 0
        } else {
            binding.btnCompleted.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            binding.btnCompleted.setTextColor(ContextCompat.getColor(requireContext(), R.color.mainpurple))
            binding.btnCompleted.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.mainpurple)
            binding.btnCompleted.strokeWidth = 2
            
            binding.btnOngoing.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.dark_card)
            binding.btnOngoing.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.btnOngoing.strokeWidth = 0
        }
    }

    private fun loadBookings() {
        // Mock ongoing bookings
        val ongoingBookings = listOf(
            Booking(
                id = "1",
                parkingName = "Allington Paddock",
                parkingAddress = "7518 Washington Alley",
                durationHours = 2,
                totalPrice = 6.58,
                bookingStatus = BookingStatus.ACTIVE
            ),
            Booking(
                id = "2",
                parkingName = "Appleton Warren",
                parkingAddress = "8499 Red Could Coast",
                durationHours = 2,
                totalPrice = 8.98,
                bookingStatus = BookingStatus.ACTIVE
            )
        )
        
        // Mock completed bookings
        val completedBookings = listOf(
            Booking(
                id = "3",
                parkingName = "Banfield Road",
                parkingAddress = "970 Division Center",
                durationHours = 2,
                totalPrice = 7.34,
                bookingStatus = BookingStatus.COMPLETED
            ),
            Booking(
                id = "4",
                parkingName = "Beach Furlong",
                parkingAddress = "8638 Waubesg Plaza",
                durationHours = 2,
                totalPrice = 5.66,
                bookingStatus = BookingStatus.COMPLETED
            )
        )

        val bookings = if (isOngoingSelected) ongoingBookings else completedBookings
        
        if (bookings.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvBookings.visibility = View.GONE
            binding.tvEmptyTitle.text = if (isOngoingSelected) "No Ongoing Bookings" else "No Completed Bookings"
            binding.tvEmptySubtitle.text = if (isOngoingSelected) 
                "Your active parking bookings\nwill appear here" 
            else 
                "Your past parking bookings\nwill appear here"
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvBookings.visibility = View.VISIBLE
            adapter.submitList(bookings)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

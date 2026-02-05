package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.parkover.R
import com.example.parkover.data.model.BookingData
import com.example.parkover.data.model.FloorData
import com.example.parkover.data.model.ParkingSlot
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.databinding.FragmentSelectFloorBinding
import com.example.parkover.ui.adapters.ParkingSpotAdapter
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class SelectFloorFragment : Fragment() {

    private var _binding: FragmentSelectFloorBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ParkingSpotAdapter
    private val repository = BookingRepository()
    
    private var bookingData: BookingData? = null
    private var floors: List<FloorData> = emptyList()
    private var selectedFloor: FloorData? = null
    private var selectedSpot: ParkingSlot? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectFloorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bookingData = arguments?.getParcelable("booking_data")
        
        setupAdapter()
        setupClickListeners()
        loadFloors()
    }

    private fun setupAdapter() {
        adapter = ParkingSpotAdapter { spot ->
            if (spot.isAvailable) {
                selectedSpot = spot
                adapter.setSelectedSpot(spot.id)
                updateSelectedSpotInfo()
                binding.btnContinue.isEnabled = true
            }
        }
        
        binding.rvParkingSpots.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SelectFloorFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnContinue.setOnClickListener {
            navigateToConfirmBooking()
        }
    }

    private fun loadFloors() {
        val data = bookingData ?: return
        
        lifecycleScope.launch {
            floors = repository.getFloorsWithSpots(data.parkingId, data.vehicleType)
            setupFloorChips()
            
            // Select first floor by default
            if (floors.isNotEmpty()) {
                selectFloor(floors[0])
            }
        }
    }

    private fun setupFloorChips() {
        binding.chipGroupFloors.removeAllViews()
        
        floors.forEachIndexed { index, floor ->
            val chip = Chip(requireContext()).apply {
                text = floor.name
                isCheckable = true
                isChecked = index == 0
                chipBackgroundColor = ContextCompat.getColorStateList(
                    requireContext(),
                    if (index == 0) R.color.mainpurple else R.color.background_white
                )
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (index == 0) R.color.white else R.color.text_primary
                    )
                )
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.mainpurple)
                chipStrokeWidth = 1f
                
                setOnClickListener {
                    selectFloor(floor)
                    updateChipStyles()
                }
            }
            binding.chipGroupFloors.addView(chip)
        }
    }

    private fun updateChipStyles() {
        for (i in 0 until binding.chipGroupFloors.childCount) {
            val chip = binding.chipGroupFloors.getChildAt(i) as? Chip ?: continue
            val isSelected = floors.getOrNull(i) == selectedFloor
            
            chip.chipBackgroundColor = ContextCompat.getColorStateList(
                requireContext(),
                if (isSelected) R.color.mainpurple else R.color.background_white
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.white else R.color.text_primary
                )
            )
        }
    }

    private fun selectFloor(floor: FloorData) {
        selectedFloor = floor
        selectedSpot = null
        binding.btnContinue.isEnabled = false
        binding.tvSelectedSpot.text = "Select a parking spot"
        
        // Group spots into rows for the adapter
        val spotRows = floor.spots.chunked(2)
        adapter.submitList(spotRows)
        adapter.setSelectedSpot(null)
    }

    private fun updateSelectedSpotInfo() {
        val spot = selectedSpot ?: return
        val floor = selectedFloor ?: return
        
        binding.tvSelectedSpot.text = "Selected: ${floor.name} - Spot ${spot.spotNumber}"
        binding.tvSelectedSpot.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.mainpurple)
        )
    }

    private fun navigateToConfirmBooking() {
        val spot = selectedSpot ?: return
        val floor = selectedFloor ?: return
        val data = bookingData ?: return
        
        // Update booking data with selected floor and spot
        data.floorNumber = floor.floorNumber
        data.floorName = floor.name
        data.spotId = spot.id
        data.spotNumber = spot.spotNumber
        
        val bundle = Bundle().apply {
            putParcelable("booking_data", data)
        }
        
        findNavController().navigate(R.id.action_selectFloor_to_confirmBooking, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

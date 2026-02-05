package com.example.parkover.ui.fragments.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.databinding.FragmentSavedBinding
import com.example.parkover.ui.adapters.SavedParkingAdapter
import com.example.parkover.utils.showToast

class SavedFragment : Fragment() {

    private var _binding: FragmentSavedBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SavedParkingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        loadSavedParkings()
    }

    private fun setupAdapter() {
        adapter = SavedParkingAdapter(
            onItemClick = { spot ->
                context?.showToast("Opening ${spot.name}")
            },
            onUnsaveClick = { spot ->
                context?.showToast("Removed from saved")
            }
        )
        
        binding.rvSavedParking.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SavedFragment.adapter
        }
    }

    private fun loadSavedParkings() {
        // Mock data for now - will be replaced with Firestore
        val mockSavedParkings = listOf(
            ParkingSpot(
                id = "1",
                name = "City Center Parking",
                address = "123 Main Street, Downtown",
                latitude = 28.6139,
                longitude = 77.2090,
                pricePerHourFourWheeler = 50.0,
                rating = 4.5,
                availableSpotsFourWheeler = 25,
                totalSpotsFourWheeler = 100
            ),
            ParkingSpot(
                id = "2",
                name = "Mall Parking Complex",
                address = "456 Shopping Avenue",
                latitude = 28.6200,
                longitude = 77.2150,
                pricePerHourFourWheeler = 40.0,
                rating = 4.2,
                availableSpotsFourWheeler = 50,
                totalSpotsFourWheeler = 200
            ),
            ParkingSpot(
                id = "3",
                name = "Metro Station Parking",
                address = "789 Transit Road",
                latitude = 28.6100,
                longitude = 77.2000,
                pricePerHourFourWheeler = 30.0,
                rating = 4.0,
                availableSpotsFourWheeler = 15,
                totalSpotsFourWheeler = 50
            )
        )

        if (mockSavedParkings.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvSavedParking.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvSavedParking.visibility = View.VISIBLE
            adapter.submitList(mockSavedParkings)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

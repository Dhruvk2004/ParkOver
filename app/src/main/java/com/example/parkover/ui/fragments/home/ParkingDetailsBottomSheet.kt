package com.example.parkover.ui.fragments.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.parkover.R
import com.example.parkover.databinding.BottomSheetParkingDetailsBinding
import com.example.parkover.ui.fragments.booking.ParkingDetailsFragment
import com.example.parkover.utils.LocationHelper
import com.example.parkover.utils.showToast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ParkingDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetParkingDetailsBinding? = null
    private val binding get() = _binding!!

    private var parkingName: String = ""
    private var parkingAddress: String = ""
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var routeDistance: String = ""
    private var isSaved: Boolean = false

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_ADDRESS = "address"
        private const val ARG_LAT = "latitude"
        private const val ARG_LNG = "longitude"
        private const val ARG_CURRENT_LAT = "current_lat"
        private const val ARG_CURRENT_LNG = "current_lng"
        private const val ARG_DISTANCE = "distance"

        fun newInstance(
            name: String,
            address: String,
            latitude: Double,
            longitude: Double,
            currentLat: Double,
            currentLng: Double,
            distance: String = ""
        ): ParkingDetailsBottomSheet {
            return ParkingDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_ADDRESS, address)
                    putDouble(ARG_LAT, latitude)
                    putDouble(ARG_LNG, longitude)
                    putDouble(ARG_CURRENT_LAT, currentLat)
                    putDouble(ARG_CURRENT_LNG, currentLng)
                    putString(ARG_DISTANCE, distance)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)

        arguments?.let {
            parkingName = it.getString(ARG_NAME, "")
            parkingAddress = it.getString(ARG_ADDRESS, "")
            latitude = it.getDouble(ARG_LAT, 0.0)
            longitude = it.getDouble(ARG_LNG, 0.0)
            currentLat = it.getDouble(ARG_CURRENT_LAT, 0.0)
            currentLng = it.getDouble(ARG_CURRENT_LNG, 0.0)
            routeDistance = it.getString(ARG_DISTANCE, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetParkingDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        binding.tvParkingName.text = parkingName
        
        // Show distance from Directions API if available, otherwise calculate straight line
        val distanceText = if (routeDistance.isNotEmpty()) {
            routeDistance
        } else if (currentLat != 0.0 && currentLng != 0.0) {
            val distance = LocationHelper.calculateDistance(
                currentLat, currentLng,
                latitude, longitude
            )
            LocationHelper.formatDistance(distance)
        } else {
            ""
        }
        
        if (distanceText.isNotEmpty()) {
            binding.tvParkingAddress.text = "$parkingAddress • $distanceText away"
        } else {
            binding.tvParkingAddress.text = parkingAddress
        }

        // TODO: Load actual parking image from Firestore
        // For now using placeholder

        binding.btnSave.setOnClickListener {
            isSaved = !isSaved
            updateSaveButton()
            val message = if (isSaved) "Saved to favorites" else "Removed from favorites"
            context?.showToast(message)
            // TODO: Save to Firestore
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnDetails.setOnClickListener {
            dismiss()
            // Navigate to full parking details fragment
            val bundle = Bundle().apply {
                putString(ParkingDetailsFragment.ARG_PARKING_ID, "parking_${System.currentTimeMillis()}")
                putString(ParkingDetailsFragment.ARG_PARKING_NAME, parkingName)
                putString(ParkingDetailsFragment.ARG_PARKING_ADDRESS, parkingAddress)
                putDouble(ParkingDetailsFragment.ARG_PRICE_PER_HOUR, 50.0)
                putDouble(ParkingDetailsFragment.ARG_LATITUDE, latitude)
                putDouble(ParkingDetailsFragment.ARG_LONGITUDE, longitude)
                putInt(ParkingDetailsFragment.ARG_AVAILABLE_SPOTS, 25)
                putInt(ParkingDetailsFragment.ARG_TOTAL_SPOTS, 100)
                putFloat(ParkingDetailsFragment.ARG_RATING, 4.5f)
            }
            
            parentFragment?.findNavController()?.navigate(
                R.id.action_home_to_parkingDetails,
                bundle
            )
        }
    }

    private fun updateSaveButton() {
        val iconRes = if (isSaved) R.drawable.ic_saved_filled else R.drawable.ic_saved_outline
        binding.btnSave.setImageResource(iconRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.parkover.ui.fragments.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.parkover.R
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.databinding.BottomSheetParkingDetailsBinding
import com.example.parkover.ui.fragments.booking.ParkingDetailsFragment
import com.example.parkover.utils.LocationHelper
import com.example.parkover.utils.showToast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ParkingDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetParkingDetailsBinding? = null
    private val binding get() = _binding!!

    private var parkingSpot: ParkingSpot? = null
    private var parkingName: String = ""
    private var parkingAddress: String = ""
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private var routeDistance: String = ""
    private var isSaved: Boolean = false

    companion object {
        private const val ARG_PARKING_SPOT = "parking_spot"
        private const val ARG_NAME = "name"
        private const val ARG_ADDRESS = "address"
        private const val ARG_LAT = "latitude"
        private const val ARG_LNG = "longitude"
        private const val ARG_CURRENT_LAT = "current_lat"
        private const val ARG_CURRENT_LNG = "current_lng"
        private const val ARG_DISTANCE = "distance"

        fun newInstance(
            parkingSpot: ParkingSpot? = null,
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
                    parkingSpot?.let { putString(ARG_PARKING_SPOT, it.id) }
                    putString(ARG_NAME, name)
                    putString(ARG_ADDRESS, address)
                    putDouble(ARG_LAT, latitude)
                    putDouble(ARG_LNG, longitude)
                    putDouble(ARG_CURRENT_LAT, currentLat)
                    putDouble(ARG_CURRENT_LNG, currentLng)
                    putString(ARG_DISTANCE, distance)
                }
                this.parkingSpot = parkingSpot
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

        // Load parking image from API if available
        parkingSpot?.images?.firstOrNull()?.let { imageUrl ->
            binding.ivParkingImage.load(imageUrl) {
                crossfade(true)
                placeholder(R.color.nav_background_purple)
                error(R.color.nav_background_purple)
            }
        }
        
        // Check if already saved
        checkIfSaved()

        binding.btnSave.setOnClickListener {
            toggleSaved()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnDetails.setOnClickListener {
            dismiss()
            navigateToParkingDetails()
        }
    }
    
    private fun checkIfSaved() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val spotId = parkingSpot?.id ?: return
        
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("savedParkings")
            .document(spotId)
            .get()
            .addOnSuccessListener { doc ->
                isSaved = doc.exists()
                updateSaveButton()
            }
    }
    
    private fun toggleSaved() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val spot = parkingSpot
        
        if (userId == null) {
            context?.showToast("Please login to save")
            return
        }
        
        isSaved = !isSaved
        updateSaveButton()
        
        val savedRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("savedParkings")
        
        if (isSaved && spot != null) {
            // Save to Firestore
            val savedData = mapOf(
                "parkingId" to spot.id,
                "name" to spot.name,
                "address" to spot.address,
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "pricePerHour" to spot.pricePerHourFourWheeler,
                "rating" to spot.rating,
                "savedAt" to com.google.firebase.Timestamp.now()
            )
            savedRef.document(spot.id).set(savedData)
                .addOnSuccessListener {
                    context?.showToast("Saved to favorites")
                }
                .addOnFailureListener {
                    isSaved = false
                    updateSaveButton()
                    context?.showToast("Failed to save")
                }
        } else if (spot != null) {
            // Remove from Firestore
            savedRef.document(spot.id).delete()
                .addOnSuccessListener {
                    context?.showToast("Removed from favorites")
                }
                .addOnFailureListener {
                    isSaved = true
                    updateSaveButton()
                    context?.showToast("Failed to remove")
                }
        }
    }
    
    private fun navigateToParkingDetails() {
        val spot = parkingSpot
        val bundle = Bundle().apply {
            putString(ParkingDetailsFragment.ARG_PARKING_ID, spot?.id ?: "parking_${System.currentTimeMillis()}")
            putString(ParkingDetailsFragment.ARG_PARKING_NAME, parkingName)
            putString(ParkingDetailsFragment.ARG_PARKING_ADDRESS, parkingAddress)
            putDouble(ParkingDetailsFragment.ARG_PRICE_PER_HOUR, spot?.pricePerHourFourWheeler ?: 50.0)
            putDouble(ParkingDetailsFragment.ARG_LATITUDE, latitude)
            putDouble(ParkingDetailsFragment.ARG_LONGITUDE, longitude)
            putInt(ParkingDetailsFragment.ARG_AVAILABLE_SPOTS, spot?.getTotalAvailableSpots() ?: 25)
            putInt(ParkingDetailsFragment.ARG_TOTAL_SPOTS, spot?.getTotalSpots() ?: 100)
            putFloat(ParkingDetailsFragment.ARG_RATING, spot?.rating?.toFloat() ?: 4.5f)
            // Pass image URL if available
            spot?.images?.firstOrNull()?.let { putString("parking_image", it) }
        }
        
        parentFragment?.findNavController()?.navigate(
            R.id.action_home_to_parkingDetails,
            bundle
        )
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

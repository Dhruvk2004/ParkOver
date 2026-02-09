package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.parkover.R
import com.example.parkover.data.model.AvailabilityStatus
import com.example.parkover.data.model.BookingData
import com.example.parkover.databinding.FragmentParkingDetailsBinding
import com.example.parkover.utils.showToast
import com.example.parkover.viewmodels.ParkingViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ParkingDetailsFragment : Fragment() {

    private var _binding: FragmentParkingDetailsBinding? = null
    private val binding get() = _binding!!
    
    private val parkingViewModel: ParkingViewModel by viewModels({ requireActivity() })
    
    private var bookingData: BookingData? = null
    private var isSaved = false
    private var parkingId: String = ""

    companion object {
        const val ARG_PARKING_ID = "parking_id"
        const val ARG_PARKING_NAME = "parking_name"
        const val ARG_PARKING_ADDRESS = "parking_address"
        const val ARG_PRICE_PER_HOUR = "price_per_hour"
        const val ARG_LATITUDE = "latitude"
        const val ARG_LONGITUDE = "longitude"
        const val ARG_AVAILABLE_SPOTS = "available_spots"
        const val ARG_TOTAL_SPOTS = "total_spots"
        const val ARG_RATING = "rating"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParkingDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        extractArguments()
        setupUI()
        setupClickListeners()
        checkIfSaved()
    }

    private fun extractArguments() {
        arguments?.let { args ->
            parkingId = args.getString(ARG_PARKING_ID, "")
            bookingData = BookingData(
                parkingId = parkingId,
                parkingName = args.getString(ARG_PARKING_NAME, ""),
                parkingAddress = args.getString(ARG_PARKING_ADDRESS, ""),
                pricePerHour = args.getDouble(ARG_PRICE_PER_HOUR, 50.0),
                latitude = args.getDouble(ARG_LATITUDE, 0.0),
                longitude = args.getDouble(ARG_LONGITUDE, 0.0),
                parkingImage = args.getString("parking_image", "")
            )
        }
    }

    private fun setupUI() {
        val data = bookingData ?: return
        
        binding.tvParkingName.text = data.parkingName
        binding.tvParkingAddress.text = data.parkingAddress
        binding.tvPrice.text = "₹${String.format("%.2f", data.pricePerHour)}"
        
        // Get selected parking spot from ViewModel for more details
        val selectedSpot = parkingViewModel.selectedParkingSpot.value
        
        // Description from API or default
        val description = if (selectedSpot != null) {
            buildDescription(selectedSpot)
        } else {
            "This parking facility offers secure and convenient parking spaces for all types of vehicles. " +
                    "Located in a prime area with easy access to major roads and public transportation. " +
                    "The facility is equipped with 24/7 security surveillance, well-lit areas, and professional staff."
        }
        binding.tvDescription.text = description
        
        // Availability info from arguments or ViewModel
        val availableSpots = selectedSpot?.getTotalAvailableSpots() 
            ?: arguments?.getInt(ARG_AVAILABLE_SPOTS, 25) ?: 25
        val totalSpots = selectedSpot?.getTotalSpots() 
            ?: arguments?.getInt(ARG_TOTAL_SPOTS, 100) ?: 100
        val rating = selectedSpot?.rating 
            ?: arguments?.getFloat(ARG_RATING, 4.5f)?.toDouble() ?: 4.5
        
        binding.tvAvailableSpots.text = availableSpots.toString()
        binding.tvTotalSpots.text = totalSpots.toString()
        binding.tvRating.text = String.format("%.1f", rating)
        
        // Update availability color indicator
        val availabilityStatus = selectedSpot?.getAvailabilityStatus() ?: AvailabilityStatus.AVAILABLE
        val statusColor = when (availabilityStatus) {
            AvailabilityStatus.FULL -> R.color.error_red
            AvailabilityStatus.LIMITED -> R.color.warning_orange
            AvailabilityStatus.AVAILABLE -> R.color.success_green
        }
        binding.tvAvailableSpots.setTextColor(resources.getColor(statusColor, null))
        
        // Load parking image
        val imageUrl = data.parkingImage.ifEmpty { selectedSpot?.images?.firstOrNull() }
        if (!imageUrl.isNullOrEmpty()) {
            binding.ivParkingImage.load(imageUrl) {
                crossfade(true)
                placeholder(R.color.nav_background_purple)
                error(R.color.nav_background_purple)
            }
        }
        
        // Show amenities if available
        selectedSpot?.amenities?.let { amenities ->
            if (amenities.isNotEmpty()) {
                binding.tvAmenities?.text = amenities.joinToString(" • ")
            }
        }
        
        // Show operating hours
        selectedSpot?.operatingHours?.let { hours ->
            val hoursText = if (hours.is24Hours) {
                "Open 24 Hours"
            } else {
                "Open: ${hours.openTime} - ${hours.closeTime}"
            }
            binding.tvOperatingHours?.text = hoursText
        }
    }
    
    private fun buildDescription(spot: com.example.parkover.data.model.ParkingSpot): String {
        val amenitiesText = if (spot.amenities.isNotEmpty()) {
            "Amenities include: ${spot.amenities.joinToString(", ")}."
        } else ""
        
        val floorsText = if (spot.floors.isNotEmpty()) {
            "This facility has ${spot.floors.size} floors with a total of ${spot.getTotalSpots()} parking spots."
        } else ""
        
        return "This parking facility offers secure and convenient parking spaces. " +
                "$floorsText $amenitiesText " +
                "The facility is equipped with professional staff and modern amenities for your convenience."
    }
    
    private fun checkIfSaved() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (parkingId.isEmpty()) return
        
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("savedParkings")
            .document(parkingId)
            .get()
            .addOnSuccessListener { doc ->
                isSaved = doc.exists()
                updateSaveButton()
            }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnSave.setOnClickListener {
            toggleSaved()
        }
        
        binding.tvReadMore.setOnClickListener {
            // Toggle description expansion
            val maxLines = if (binding.tvDescription.maxLines == 5) Int.MAX_VALUE else 5
            binding.tvDescription.maxLines = maxLines
            binding.tvReadMore.text = if (maxLines == 5) "Read more..." else "Show less"
        }
        
        binding.btnBookParking.setOnClickListener {
            navigateToSelectVehicle()
        }
    }
    
    private fun toggleSaved() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val data = bookingData
        
        if (userId == null) {
            context?.showToast("Please login to save")
            return
        }
        
        if (data == null || parkingId.isEmpty()) return
        
        isSaved = !isSaved
        updateSaveButton()
        
        val savedRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("savedParkings")
        
        if (isSaved) {
            val selectedSpot = parkingViewModel.selectedParkingSpot.value
            val savedData = mapOf(
                "parkingId" to parkingId,
                "name" to data.parkingName,
                "address" to data.parkingAddress,
                "latitude" to data.latitude,
                "longitude" to data.longitude,
                "pricePerHour" to data.pricePerHour,
                "rating" to (selectedSpot?.rating ?: 4.5),
                "imageUrl" to (selectedSpot?.images?.firstOrNull() ?: ""),
                "savedAt" to com.google.firebase.Timestamp.now()
            )
            savedRef.document(parkingId).set(savedData)
                .addOnSuccessListener {
                    context?.showToast("Saved to favorites")
                }
                .addOnFailureListener {
                    isSaved = false
                    updateSaveButton()
                    context?.showToast("Failed to save")
                }
        } else {
            savedRef.document(parkingId).delete()
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

    private fun updateSaveButton() {
        val iconRes = if (isSaved) R.drawable.ic_saved_filled else R.drawable.ic_saved_outline
        binding.btnSave.setImageResource(iconRes)
    }

    private fun navigateToSelectVehicle() {
        val data = bookingData ?: return
        
        val bundle = Bundle().apply {
            putParcelable("booking_data", data)
        }
        
        findNavController().navigate(R.id.action_parkingDetails_to_selectVehicle, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

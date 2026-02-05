package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.parkover.R
import com.example.parkover.data.model.BookingData
import com.example.parkover.databinding.FragmentParkingDetailsBinding
import com.example.parkover.utils.showToast

class ParkingDetailsFragment : Fragment() {

    private var _binding: FragmentParkingDetailsBinding? = null
    private val binding get() = _binding!!
    
    private var bookingData: BookingData? = null
    private var isSaved = false

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
    }

    private fun extractArguments() {
        arguments?.let { args ->
            bookingData = BookingData(
                parkingId = args.getString(ARG_PARKING_ID, ""),
                parkingName = args.getString(ARG_PARKING_NAME, ""),
                parkingAddress = args.getString(ARG_PARKING_ADDRESS, ""),
                pricePerHour = args.getDouble(ARG_PRICE_PER_HOUR, 50.0),
                latitude = args.getDouble(ARG_LATITUDE, 0.0),
                longitude = args.getDouble(ARG_LONGITUDE, 0.0)
            )
        }
    }

    private fun setupUI() {
        val data = bookingData ?: return
        
        binding.tvParkingName.text = data.parkingName
        binding.tvParkingAddress.text = data.parkingAddress
        binding.tvPrice.text = "₹${String.format("%.2f", data.pricePerHour)}"
        
        // Description - mock for now
        binding.tvDescription.text = "This parking facility offers secure and convenient parking spaces for all types of vehicles. " +
                "Located in a prime area with easy access to major roads and public transportation. " +
                "The facility is equipped with 24/7 security surveillance, well-lit areas, and professional staff."
        
        // Availability info from arguments
        arguments?.let { args ->
            binding.tvAvailableSpots.text = args.getInt(ARG_AVAILABLE_SPOTS, 25).toString()
            binding.tvTotalSpots.text = args.getInt(ARG_TOTAL_SPOTS, 100).toString()
            binding.tvRating.text = String.format("%.1f", args.getFloat(ARG_RATING, 4.5f))
        }
        
        // Load parking image if available
        if (data.parkingImage.isNotEmpty()) {
            binding.ivParkingImage.load(data.parkingImage) {
                crossfade(true)
                placeholder(R.color.nav_background_purple)
            }
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnSave.setOnClickListener {
            isSaved = !isSaved
            updateSaveButton()
            val message = if (isSaved) "Saved to favorites" else "Removed from favorites"
            context?.showToast(message)
            // TODO: Save to Firestore
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

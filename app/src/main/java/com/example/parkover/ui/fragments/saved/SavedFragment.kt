package com.example.parkover.ui.fragments.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.parkover.R
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.databinding.FragmentSavedBinding
import com.example.parkover.ui.adapters.SavedParkingAdapter
import com.example.parkover.ui.fragments.booking.ParkingDetailsFragment
import com.example.parkover.utils.showToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SavedFragment : Fragment() {

    private var _binding: FragmentSavedBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SavedParkingAdapter
    private val firestore = FirebaseFirestore.getInstance()

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
                navigateToParkingDetails(spot)
            },
            onUnsaveClick = { spot ->
                unsaveParking(spot)
            }
        )
        
        binding.rvSavedParking.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SavedFragment.adapter
        }
    }
    
    private fun navigateToParkingDetails(spot: ParkingSpot) {
        val bundle = Bundle().apply {
            putString(ParkingDetailsFragment.ARG_PARKING_ID, spot.id)
            putString(ParkingDetailsFragment.ARG_PARKING_NAME, spot.name)
            putString(ParkingDetailsFragment.ARG_PARKING_ADDRESS, spot.address)
            putDouble(ParkingDetailsFragment.ARG_PRICE_PER_HOUR, spot.pricePerHourFourWheeler)
            putDouble(ParkingDetailsFragment.ARG_LATITUDE, spot.latitude)
            putDouble(ParkingDetailsFragment.ARG_LONGITUDE, spot.longitude)
            putInt(ParkingDetailsFragment.ARG_AVAILABLE_SPOTS, spot.getTotalAvailableSpots())
            putInt(ParkingDetailsFragment.ARG_TOTAL_SPOTS, spot.getTotalSpots())
            putFloat(ParkingDetailsFragment.ARG_RATING, spot.rating.toFloat())
            spot.images.firstOrNull()?.let { putString("parking_image", it) }
        }
        
        findNavController().navigate(R.id.action_saved_to_parkingDetails, bundle)
    }
    
    private fun unsaveParking(spot: ParkingSpot) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        lifecycleScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("savedParkings")
                    .document(spot.id)
                    .delete()
                    .await()
                
                context?.showToast("Removed from saved")
                loadSavedParkings() // Refresh list
            } catch (e: Exception) {
                context?.showToast("Failed to remove")
            }
        }
    }

    private fun loadSavedParkings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        
        if (userId == null) {
            showEmptyState()
            return
        }
        
        binding.progressBar?.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val savedDocs = firestore.collection("users")
                    .document(userId)
                    .collection("savedParkings")
                    .get()
                    .await()
                
                val savedParkings = savedDocs.documents.mapNotNull { doc ->
                    try {
                        ParkingSpot(
                            id = doc.getString("parkingId") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            address = doc.getString("address") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            pricePerHourFourWheeler = doc.getDouble("pricePerHour") ?: 0.0,
                            rating = doc.getDouble("rating") ?: 0.0,
                            images = listOfNotNull(doc.getString("imageUrl"))
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                binding.progressBar?.visibility = View.GONE
                
                if (savedParkings.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.rvSavedParking.visibility = View.VISIBLE
                    adapter.submitList(savedParkings)
                }
            } catch (e: Exception) {
                binding.progressBar?.visibility = View.GONE
                showEmptyState()
                context?.showToast("Failed to load saved parkings")
            }
        }
    }
    
    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.rvSavedParking.visibility = View.GONE
    }
    
    override fun onResume() {
        super.onResume()
        loadSavedParkings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.parkover.ui.fragments.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.parkover.R
import com.example.parkover.data.model.BookingData
import com.example.parkover.data.model.Vehicle
import com.example.parkover.data.model.VehicleType
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.databinding.FragmentSelectVehicleBinding
import com.example.parkover.databinding.DialogAddVehicleBinding
import com.example.parkover.ui.adapters.VehicleAdapter
import com.example.parkover.utils.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SelectVehicleFragment : Fragment() {

    private var _binding: FragmentSelectVehicleBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: VehicleAdapter
    private val repository = BookingRepository()
    private val firestore = FirebaseFirestore.getInstance()
    
    private var bookingData: BookingData? = null
    private var selectedVehicle: Vehicle? = null

    // Vehicle data for dropdown
    private val twoWheelerBrands = listOf("Honda", "TVS", "Bajaj", "Hero", "Royal Enfield", "Yamaha", "Suzuki", "KTM")
    private val fourWheelerBrands = listOf("Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Honda", "Toyota", "Kia", "MG")
    
    private val twoWheelerModels = mapOf(
        "Honda" to listOf("Activa", "Shine", "Unicorn", "SP 125", "Hornet"),
        "TVS" to listOf("Jupiter", "Apache", "Ntorq", "Raider", "XL100"),
        "Bajaj" to listOf("Pulsar", "Platina", "CT", "Dominar", "Avenger"),
        "Hero" to listOf("Splendor", "HF Deluxe", "Passion", "Glamour", "Xtreme"),
        "Royal Enfield" to listOf("Classic 350", "Bullet", "Meteor", "Hunter", "Himalayan"),
        "Yamaha" to listOf("FZ", "R15", "MT-15", "Fascino", "Ray ZR"),
        "Suzuki" to listOf("Access", "Burgman", "Gixxer", "Intruder"),
        "KTM" to listOf("Duke 200", "Duke 390", "RC 200", "Adventure 390")
    )
    
    private val fourWheelerModels = mapOf(
        "Maruti Suzuki" to listOf("Swift", "Baleno", "Brezza", "Ertiga", "Alto", "WagonR", "Dzire"),
        "Hyundai" to listOf("i20", "Creta", "Venue", "Verna", "Aura", "Grand i10"),
        "Tata" to listOf("Nexon", "Punch", "Harrier", "Safari", "Altroz", "Tiago"),
        "Mahindra" to listOf("XUV700", "Scorpio", "Thar", "XUV300", "Bolero"),
        "Honda" to listOf("City", "Amaze", "Elevate", "WR-V"),
        "Toyota" to listOf("Innova", "Fortuner", "Glanza", "Urban Cruiser", "Camry"),
        "Kia" to listOf("Seltos", "Sonet", "Carens", "EV6"),
        "MG" to listOf("Hector", "Astor", "ZS EV", "Gloster")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bookingData = arguments?.getParcelable("booking_data")
        
        setupAdapter()
        setupClickListeners()
        loadVehicles()
    }

    private fun setupAdapter() {
        adapter = VehicleAdapter { vehicle ->
            selectedVehicle = vehicle
            adapter.setSelectedVehicle(vehicle.id)
            binding.btnContinue.isEnabled = true
        }
        
        binding.rvVehicles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SelectVehicleFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnAddVehicle.setOnClickListener {
            showAddVehicleDialog()
        }
        
        binding.btnContinue.setOnClickListener {
            navigateToSelectFloor()
        }
    }

    private fun loadVehicles() {
        lifecycleScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            
            if (userId != null) {
                try {
                    // Try to get user's saved vehicles from Firestore
                    val userVehicles = firestore.collection("vehicles")
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                        .toObjects(Vehicle::class.java)
                    
                    if (userVehicles.isNotEmpty()) {
                        adapter.submitList(userVehicles)
                        binding.emptyState.isVisible = false
                        binding.rvVehicles.isVisible = true
                        return@launch
                    }
                } catch (e: Exception) {
                    // Fall through to default vehicles
                }
            }
            
            // If no saved vehicles, create and save default ones
            val defaultVehicles = createDefaultVehicles(userId)
            adapter.submitList(defaultVehicles)
            binding.emptyState.isVisible = false
            binding.rvVehicles.isVisible = true
        }
    }
    
    private suspend fun createDefaultVehicles(userId: String?): List<Vehicle> {
        val defaultVehicles = listOf(
            Vehicle(
                id = "default_v1",
                userId = userId ?: "",
                type = VehicleType.FOUR_WHEELER,
                brand = "4x4",
                model = "Truck",
                number = "HGE 5295",
                imageResId = R.drawable.car_truck
            ),
            Vehicle(
                id = "default_v2",
                userId = userId ?: "",
                type = VehicleType.FOUR_WHEELER,
                brand = "Toyota",
                model = "Land Cruiser",
                number = "AFD 6397",
                imageResId = R.drawable.car_land_cruiser
            ),
            Vehicle(
                id = "default_v3",
                userId = userId ?: "",
                type = VehicleType.FOUR_WHEELER,
                brand = "KIA",
                model = "SELTOS",
                number = "HUC 2957",
                imageResId = R.drawable.car_suv_orange
            ),
            Vehicle(
                id = "default_v4",
                userId = userId ?: "",
                type = VehicleType.FOUR_WHEELER,
                brand = "Honda",
                model = "City",
                number = "GTK 6294",
                imageResId = R.drawable.car_sedan_white
            )
        )
        
        // Save default vehicles to Firestore if user is logged in
        if (userId != null) {
            try {
                for (vehicle in defaultVehicles) {
                    firestore.collection("vehicles")
                        .document(vehicle.id)
                        .set(vehicle)
                        .await()
                }
            } catch (e: Exception) {
                // Ignore save errors for defaults
            }
        }
        
        return defaultVehicles
    }

    private fun showAddVehicleDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAddVehicleBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        var selectedType: VehicleType? = null
        var selectedBrand: String? = null
        var selectedModel: String? = null

        // Vehicle type selection
        dialogBinding.rgVehicleType.setOnCheckedChangeListener { _, checkedId ->
            selectedType = when (checkedId) {
                R.id.rb_two_wheeler -> VehicleType.TWO_WHEELER
                R.id.rb_four_wheeler -> VehicleType.FOUR_WHEELER
                else -> null
            }
            
            selectedBrand = null
            selectedModel = null
            dialogBinding.dropdownBrand.setText("", false)
            dialogBinding.dropdownModel.setText("", false)
            dialogBinding.cardVehiclePreview.isVisible = false
            
            dialogBinding.tilBrand.isVisible = true
            dialogBinding.tilModel.isVisible = false
            dialogBinding.tilNumber.isVisible = false
            
            val brands = if (selectedType == VehicleType.TWO_WHEELER) twoWheelerBrands else fourWheelerBrands
            val brandAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, brands)
            dialogBinding.dropdownBrand.setAdapter(brandAdapter)
            
            updateSaveButtonState(dialogBinding, selectedType, selectedBrand, selectedModel)
        }

        dialogBinding.dropdownBrand.setOnItemClickListener { _, _, position, _ ->
            val brands = if (selectedType == VehicleType.TWO_WHEELER) twoWheelerBrands else fourWheelerBrands
            selectedBrand = brands[position]
            selectedModel = null
            dialogBinding.dropdownModel.setText("", false)
            
            dialogBinding.tilModel.isVisible = true
            dialogBinding.tilNumber.isVisible = false
            dialogBinding.cardVehiclePreview.isVisible = false
            
            val models = if (selectedType == VehicleType.TWO_WHEELER) {
                twoWheelerModels[selectedBrand] ?: emptyList()
            } else {
                fourWheelerModels[selectedBrand] ?: emptyList()
            }
            val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, models)
            dialogBinding.dropdownModel.setAdapter(modelAdapter)
            
            updateSaveButtonState(dialogBinding, selectedType, selectedBrand, selectedModel)
        }

        dialogBinding.dropdownModel.setOnItemClickListener { _, _, position, _ ->
            val models = if (selectedType == VehicleType.TWO_WHEELER) {
                twoWheelerModels[selectedBrand] ?: emptyList()
            } else {
                fourWheelerModels[selectedBrand] ?: emptyList()
            }
            selectedModel = models[position]
            
            dialogBinding.tilNumber.isVisible = true
            dialogBinding.cardVehiclePreview.isVisible = true
            dialogBinding.tvVehiclePreviewName.text = "$selectedBrand $selectedModel"
            
            val imageRes = if (selectedType == VehicleType.TWO_WHEELER) {
                R.drawable.car_top_view
            } else {
                R.drawable.car_sedan_white
            }
            dialogBinding.ivVehiclePreview.setImageResource(imageRes)
            
            updateSaveButtonState(dialogBinding, selectedType, selectedBrand, selectedModel)
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val number = dialogBinding.etNumber.text.toString().trim().uppercase()
            
            if (number.isEmpty()) {
                dialogBinding.tvError.text = "Please enter vehicle number"
                dialogBinding.tvError.isVisible = true
                return@setOnClickListener
            }
            
            dialogBinding.tvError.isVisible = false
            dialogBinding.progressBar.isVisible = true
            dialogBinding.btnSave.isEnabled = false
            
            saveVehicle(
                type = selectedType!!,
                brand = selectedBrand!!,
                model = selectedModel!!,
                number = number
            ) { success ->
                dialogBinding.progressBar.isVisible = false
                if (success) {
                    context?.showToast("Vehicle saved successfully")
                    dialog.dismiss()
                    loadVehicles()
                } else {
                    dialogBinding.btnSave.isEnabled = true
                    dialogBinding.tvError.text = "Failed to save vehicle"
                    dialogBinding.tvError.isVisible = true
                }
            }
        }

        dialog.show()
    }
    
    private fun updateSaveButtonState(
        dialogBinding: DialogAddVehicleBinding,
        type: VehicleType?,
        brand: String?,
        model: String?
    ) {
        dialogBinding.btnSave.isEnabled = type != null && brand != null && model != null
    }
    
    private fun saveVehicle(
        type: VehicleType,
        brand: String,
        model: String,
        number: String,
        callback: (Boolean) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            callback(false)
            return
        }
        
        lifecycleScope.launch {
            try {
                val vehicleId = firestore.collection("vehicles").document().id
                val vehicle = Vehicle(
                    id = vehicleId,
                    userId = userId,
                    type = type,
                    brand = brand,
                    model = model,
                    number = number,
                    imageResId = if (type == VehicleType.TWO_WHEELER) R.drawable.car_top_view else R.drawable.car_sedan_white
                )
                
                firestore.collection("vehicles")
                    .document(vehicleId)
                    .set(vehicle)
                    .await()
                
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    private fun navigateToSelectFloor() {
        val vehicle = selectedVehicle ?: return
        val data = bookingData ?: return
        
        data.vehicleId = vehicle.id
        data.vehicleName = "${vehicle.brand} ${vehicle.model}"
        data.vehicleNumber = vehicle.number
        data.vehicleType = vehicle.type
        
        val bundle = Bundle().apply {
            putParcelable("booking_data", data)
        }
        
        findNavController().navigate(R.id.action_selectVehicle_to_selectFloor, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

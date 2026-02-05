package com.example.parkover.ui.fragments.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.parkover.R
import com.example.parkover.data.model.Vehicle
import com.example.parkover.data.model.VehicleType
import com.example.parkover.data.repository.BookingRepository
import com.example.parkover.databinding.FragmentSavedVehiclesBinding
import com.example.parkover.databinding.DialogAddVehicleBinding
import com.example.parkover.ui.adapters.VehicleAdapter
import com.example.parkover.utils.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SavedVehiclesFragment : Fragment() {

    private var _binding: FragmentSavedVehiclesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: VehicleAdapter
    private val repository = BookingRepository()
    private val firestore = FirebaseFirestore.getInstance()

    // Vehicle data
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
        _binding = FragmentSavedVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupClickListeners()
        loadVehicles()
    }

    private fun setupAdapter() {
        adapter = VehicleAdapter { vehicle ->
            // Vehicle clicked - could show details or edit
        }
        
        binding.rvVehicles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SavedVehiclesFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnAddVehicle.setOnClickListener {
            showAddVehicleDialog()
        }
    }

    private fun loadVehicles() {
        binding.progressBar.isVisible = true
        
        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    binding.progressBar.isVisible = false
                    binding.emptyState.isVisible = true
                    return@launch
                }
                
                val vehicles = firestore.collection("vehicles")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .toObjects(Vehicle::class.java)
                
                binding.progressBar.isVisible = false
                
                if (vehicles.isEmpty()) {
                    binding.emptyState.isVisible = true
                    binding.rvVehicles.isVisible = false
                } else {
                    binding.emptyState.isVisible = false
                    binding.rvVehicles.isVisible = true
                    adapter.submitList(vehicles)
                }
            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                binding.emptyState.isVisible = true
                context?.showToast("Failed to load vehicles")
            }
        }
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
            
            // Show brand dropdown
            dialogBinding.tilBrand.isVisible = true
            dialogBinding.tilModel.isVisible = false
            dialogBinding.tilNumber.isVisible = false
            
            // Set brand options based on type
            val brands = if (selectedType == VehicleType.TWO_WHEELER) twoWheelerBrands else fourWheelerBrands
            val brandAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, brands)
            dialogBinding.dropdownBrand.setAdapter(brandAdapter)
            
            updateSaveButtonState(dialogBinding, selectedType, selectedBrand, selectedModel)
        }

        // Brand selection
        dialogBinding.dropdownBrand.setOnItemClickListener { _, _, position, _ ->
            val brands = if (selectedType == VehicleType.TWO_WHEELER) twoWheelerBrands else fourWheelerBrands
            selectedBrand = brands[position]
            selectedModel = null
            dialogBinding.dropdownModel.setText("", false)
            
            // Show model dropdown
            dialogBinding.tilModel.isVisible = true
            dialogBinding.tilNumber.isVisible = false
            dialogBinding.cardVehiclePreview.isVisible = false
            
            // Set model options based on brand
            val models = if (selectedType == VehicleType.TWO_WHEELER) {
                twoWheelerModels[selectedBrand] ?: emptyList()
            } else {
                fourWheelerModels[selectedBrand] ?: emptyList()
            }
            val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, models)
            dialogBinding.dropdownModel.setAdapter(modelAdapter)
            
            updateSaveButtonState(dialogBinding, selectedType, selectedBrand, selectedModel)
        }

        // Model selection
        dialogBinding.dropdownModel.setOnItemClickListener { _, _, position, _ ->
            val models = if (selectedType == VehicleType.TWO_WHEELER) {
                twoWheelerModels[selectedBrand] ?: emptyList()
            } else {
                fourWheelerModels[selectedBrand] ?: emptyList()
            }
            selectedModel = models[position]
            
            // Show number input and preview
            dialogBinding.tilNumber.isVisible = true
            dialogBinding.cardVehiclePreview.isVisible = true
            dialogBinding.tvVehiclePreviewName.text = "$selectedBrand $selectedModel"
            
            // Set preview image based on type (placeholder for now)
            val imageRes = if (selectedType == VehicleType.TWO_WHEELER) {
                R.drawable.car_top_view // Will use bike image later
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
            
            if (!isValidVehicleNumber(number)) {
                dialogBinding.tvError.text = "Invalid vehicle number format"
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
    
    private fun isValidVehicleNumber(number: String): Boolean {
        // Basic Indian vehicle number format validation
        val pattern = "^[A-Z]{2}\\s?\\d{1,2}\\s?[A-Z]{1,3}\\s?\\d{1,4}$".toRegex()
        return number.replace(" ", "").matches(pattern) || number.length >= 6
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

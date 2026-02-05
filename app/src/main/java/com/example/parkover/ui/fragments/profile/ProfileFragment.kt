package com.example.parkover.ui.fragments.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.example.parkover.R
import com.example.parkover.databinding.FragmentProfileBinding
import com.example.parkover.databinding.DialogEditProfileBinding
import com.example.parkover.databinding.DialogImagePickerBinding
import com.example.parkover.ui.activities.LoginActivity
import com.example.parkover.utils.showToast
import com.example.parkover.viewmodels.AuthViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()
    
    private var currentPhotoUri: Uri? = null
    private var tempCameraFile: File? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            context?.showToast("Camera permission required")
        }
    }

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            context?.showToast("Storage permission required")
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
        setupMenuClicks()
        setupProfilePhotoClick()
    }

    private fun loadUserData() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            binding.tvName.text = it.displayName ?: "User"
            binding.tvEmail.text = it.email ?: ""
            
            it.photoUrl?.let { photoUrl ->
                binding.ivProfilePhoto.load(photoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_default_profile)
                    error(R.drawable.ic_default_profile)
                    transformations(CircleCropTransformation())
                }
            } ?: run {
                binding.ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
            }
        }
        
        viewModel.loadCurrentUser()
        viewModel.currentUser.observe(viewLifecycleOwner) { userData ->
            userData?.let {
                if (it.name.isNotEmpty()) binding.tvName.text = it.name
                it.profilePhotoUrl?.let { url ->
                    binding.ivProfilePhoto.load(url) {
                        crossfade(true)
                        placeholder(R.drawable.ic_default_profile)
                        error(R.drawable.ic_default_profile)
                        transformations(CircleCropTransformation())
                    }
                }
            }
        }
    }

    private fun setupProfilePhotoClick() {
        binding.btnChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }
        
        binding.ivProfilePhoto.setOnClickListener {
            showImagePickerDialog()
        }
    }

    private fun showImagePickerDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogImagePickerBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.btnCamera.setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndOpen()
        }

        dialogBinding.btnGallery.setOnClickListener {
            dialog.dismiss()
            checkGalleryPermissionAndOpen()
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkGalleryPermissionAndOpen() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            else -> {
                galleryPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PROFILE_${timeStamp}.jpg"
        
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        tempCameraFile = File(storageDir, imageFileName)
        
        currentPhotoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            tempCameraFile!!
        )
        
        cameraLauncher.launch(currentPhotoUri)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun uploadProfileImage(uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        context?.showToast("Uploading image...")
        
        val storageRef = FirebaseStorage.getInstance().reference
        val profileImageRef = storageRef.child("profile_images/${user.uid}.jpg")
        
        profileImageRef.putFile(uri)
            .addOnSuccessListener {
                profileImageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    updateUserProfilePhoto(downloadUri)
                }
            }
            .addOnFailureListener { e ->
                context?.showToast("Failed to upload image: ${e.message}")
            }
    }

    private fun updateUserProfilePhoto(photoUri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(photoUri)
            .build()
        
        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                binding.ivProfilePhoto.load(photoUri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_default_profile)
                    transformations(CircleCropTransformation())
                }
                context?.showToast("Profile photo updated")
            }
            .addOnFailureListener { e ->
                context?.showToast("Failed to update profile: ${e.message}")
            }
    }

    private fun setupMenuClicks() {
        binding.menuEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
        
        binding.menuPayment.setOnClickListener {
            context?.showToast("Coming soon")
        }
        
        binding.menuNotification.setOnClickListener {
            context?.showToast("Coming soon")
        }
        
        binding.menuSavedVehicles.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_savedVehicles)
        }
        
        binding.menuHelp.setOnClickListener {
            context?.showToast("Coming soon")
        }
        
        binding.menuLogout.setOnClickListener {
            viewModel.signOut()
            navigateToLogin()
        }
    }

    private fun showEditProfileDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentName = binding.tvName.text.toString()
        dialogBinding.etName.setText(currentName)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val newName = dialogBinding.etName.text.toString().trim()
            
            when {
                newName.isEmpty() -> {
                    dialogBinding.tvError.text = "Name cannot be empty"
                    dialogBinding.tvError.visibility = View.VISIBLE
                }
                !isValidName(newName) -> {
                    dialogBinding.tvError.text = "Name can only contain letters and spaces"
                    dialogBinding.tvError.visibility = View.VISIBLE
                }
                newName.length < 2 -> {
                    dialogBinding.tvError.text = "Name must be at least 2 characters"
                    dialogBinding.tvError.visibility = View.VISIBLE
                }
                newName.length > 50 -> {
                    dialogBinding.tvError.text = "Name cannot exceed 50 characters"
                    dialogBinding.tvError.visibility = View.VISIBLE
                }
                else -> {
                    dialogBinding.tvError.visibility = View.GONE
                    dialogBinding.progressBar.visibility = View.VISIBLE
                    dialogBinding.btnSave.text = ""
                    dialogBinding.btnSave.isEnabled = false
                    dialogBinding.btnCancel.isEnabled = false
                    
                    updateUserName(newName) { success ->
                        dialogBinding.progressBar.visibility = View.GONE
                        if (success) {
                            binding.tvName.text = newName
                            context?.showToast("Name updated successfully")
                            dialog.dismiss()
                        } else {
                            dialogBinding.btnSave.text = "Save"
                            dialogBinding.btnSave.isEnabled = true
                            dialogBinding.btnCancel.isEnabled = true
                            dialogBinding.tvError.text = "Failed to update name"
                            dialogBinding.tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun isValidName(name: String): Boolean {
        val nameRegex = "^[a-zA-Z\\s]+$".toRegex()
        return name.matches(nameRegex)
    }

    private fun updateUserName(name: String, callback: (Boolean) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            callback(false)
            return
        }
        
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        
        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

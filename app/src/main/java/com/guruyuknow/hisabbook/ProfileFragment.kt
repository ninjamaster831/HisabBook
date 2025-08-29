package com.guruyuknow.hisabbook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.Staff.StaffActivity
import com.guruyuknow.hisabbook.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                // Update the dialog image view if dialog is showing
                currentEditDialog?.findViewById<ImageView>(R.id.dialogProfileImage)?.let { imageView ->
                    Glide.with(this)
                        .load(uri)
                        .transform(CircleCrop())
                        .into(imageView)
                }
            }
        }
    }

    private var currentEditDialog: AlertDialog? = null

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
        setupUI()
        setupClickListeners()
        loadUserData()
    }

    private fun setupUI() {
        binding.apply {
            businessNameText.text = "My Business"
            profileStrengthText.text = "Weak"
            profileStrengthPercentage.text = "0%"
            profileInitials.text = "MB"
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            editButton.setOnClickListener {
                showEditProfileDialog()
            }
            binding.featureCardsGrid.getChildAt(2).setOnClickListener {
                val intent = Intent(requireContext(), CashbookActivity::class.java)
                startActivity(intent)
            }
            binding.featureCardsGrid.getChildAt(1).setOnClickListener {
                val intent = Intent(requireContext(), StaffActivity::class.java)
                startActivity(intent)
            }
            settingsLayout.setOnClickListener {
                // TODO: Navigate to settings
            }

            helpSupportLayout.setOnClickListener {
                // TODO: Navigate to help & support
            }

            aboutUsLayout.setOnClickListener {
                // TODO: Navigate to about us
            }

            logoutButton.setOnClickListener {
                handleLogout()
            }
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                println("=== LOAD USER DATA DEBUG ===")
                val user = SupabaseManager.getCurrentUser()
                println("Loaded user from database: $user")
                user?.let {
                    currentUser = it
                    updateUI(it)
                    println("Current user set to: $currentUser")
                } ?: run {
                    println("No user found in database")
                }
            } catch (e: Exception) {
                println("Load user data error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun updateUI(user: User) {
        binding.apply {
            businessNameText.text = user.fullName ?: user.email ?: "My Business"

            // Load profile image or show initials
            if (!user.avatarUrl.isNullOrEmpty()) {
                profileInitials.visibility = View.GONE
                profileImage.visibility = View.VISIBLE
                Glide.with(this@ProfileFragment)
                    .load(user.avatarUrl)
                    .transform(CircleCrop())
                    .into(profileImage)
            } else {
                profileImage.visibility = View.GONE
                profileInitials.visibility = View.VISIBLE

                val initials = if (!user.fullName.isNullOrEmpty()) {
                    user.fullName.split(" ").take(2).joinToString("") {
                        it.first().toString().uppercase()
                    }
                } else if (!user.email.isNullOrEmpty()) {
                    user.email.first().toString().uppercase()
                } else {
                    "MB"
                }
                profileInitials.text = initials
            }

            updateProfileStrength(user)
        }
    }

    private fun showEditProfileDialog() {
        if (currentUser == null) {
            println("Current user is null, reloading user data...")
            loadUserData()
            Toast.makeText(requireContext(), "Loading user data, please try again", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val profileImageView = dialogView.findViewById<ImageView>(R.id.dialogProfileImage)
        val profileInitialsView = dialogView.findViewById<android.widget.TextView>(R.id.dialogProfileInitials)
        val changePhotoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.changePhotoButton)

        // Pre-fill current data
        nameInput.setText(currentUser?.fullName ?: "")

        // Load current profile image or initials
        if (!currentUser?.avatarUrl.isNullOrEmpty()) {
            profileImageView.visibility = View.VISIBLE
            profileInitialsView.visibility = View.GONE
            Glide.with(this)
                .load(currentUser?.avatarUrl)
                .transform(CircleCrop())
                .into(profileImageView)
        } else {
            profileImageView.visibility = View.GONE
            profileInitialsView.visibility = View.VISIBLE
            val initials = if (!currentUser?.fullName.isNullOrEmpty()) {
                currentUser?.fullName?.split(" ")?.take(2)?.joinToString("") {
                    it.first().toString().uppercase()
                } ?: "MB"
            } else if (!currentUser?.email.isNullOrEmpty()) {
                currentUser?.email?.first()?.toString()?.uppercase() ?: "MB"
            } else {
                "MB"
            }
            profileInitialsView.text = initials
        }

        // Handle photo change
        changePhotoButton.setOnClickListener {
            openImagePicker()
        }

        currentEditDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString().trim()
                saveProfileChanges(newName, selectedImageUri)
            }
            .setNegativeButton("Cancel", null)
            .create()

        currentEditDialog?.show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Profile Image"))
    }

    private fun saveProfileChanges(newName: String, imageUri: Uri?) {
        lifecycleScope.launch {
            try {
                println("=== SAVE PROFILE CHANGES DEBUG ===")
                println("New name: '$newName'")
                println("Image URI: $imageUri")
                println("Current user: $currentUser")

                val updatedUser = currentUser?.copy(fullName = newName)
                println("Updated user: $updatedUser")

                if (updatedUser != null) {
                    val result = SupabaseManager.updateUser(updatedUser, imageUri, requireContext())
                    println("Update result success: ${result.isSuccess}")

                    if (result.isSuccess) {
                        currentUser = result.getOrNull()
                        println("New current user: $currentUser")
                        currentUser?.let { updateUI(it) }
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = result.exceptionOrNull()
                        println("Update failed with error: ${error?.message}")
                        error?.printStackTrace()
                        Toast.makeText(requireContext(), "Failed to update profile: ${error?.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    println("Updated user is null!")
                }

                // Reset selected image
                selectedImageUri = null
                currentEditDialog = null

            } catch (e: Exception) {
                println("Save profile changes error: ${e.message}")
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProfileStrength(user: User) {
        var completionScore = 0
        var totalFields = 4

        if (!user.email.isNullOrEmpty()) completionScore++
        if (!user.fullName.isNullOrEmpty()) completionScore++
        if (!user.avatarUrl.isNullOrEmpty()) completionScore++
        completionScore++ // For having an account

        val percentage = (completionScore * 100) / totalFields
        val strength = when {
            percentage <= 30 -> "Weak"
            percentage <= 70 -> "Medium"
            else -> "Strong"
        }

        binding.apply {
            profileStrengthText.text = strength
            profileStrengthPercentage.text = "$percentage%"

            val color = when (strength) {
                "Weak" -> android.graphics.Color.parseColor("#F44336")
                "Medium" -> android.graphics.Color.parseColor("#FF9800")
                else -> android.graphics.Color.parseColor("#4CAF50")
            }
            profileStrengthText.setTextColor(color)
            profileStrengthPercentage.setTextColor(color)
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                SupabaseManager.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                // Handle logout error
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentEditDialog?.dismiss()
        _binding = null
    }
}
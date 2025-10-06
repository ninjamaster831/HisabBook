package com.guruyuknow.hisabbook

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.Bills.BillsActivity
import com.guruyuknow.hisabbook.Staff.StaffActivity
import com.guruyuknow.hisabbook.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

data class Feature(
    val title: String,
    val icon: Int,
    val color: String,
    val activity: Class<*>
)

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null
    private var currentEditDialog: AlertDialog? = null

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
            profileInitials.text = "MB"
            emailText.text = "business@email.com"
        }
    }

    private fun updateUI(user: User) {
        binding.apply {
            businessNameText.text = user.fullName ?: user.email ?: "My Business"
            emailText.text = user.email ?: ""

            val avatarUrl = resolveAvatarUrl(user.avatarUrl)

            if (!avatarUrl.isNullOrBlank()) {
                profileInitials.visibility = View.GONE
                profileImage.visibility = View.VISIBLE

                Glide.with(this@ProfileFragment)
                    .load(avatarUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(profileImage)
            } else {
                profileImage.visibility = View.GONE
                profileInitials.visibility = View.VISIBLE

                val initials = when {
                    !user.fullName.isNullOrEmpty() ->
                        user.fullName.split(" ").take(2).joinToString("") { it.first().toString().uppercase() }
                    !user.email.isNullOrEmpty() ->
                        user.email.first().toString().uppercase()
                    else -> "MB"
                }
                profileInitials.text = initials
            }
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            // Edit button click - now handles both badge and FAB
            editButton.setOnClickListener {
                showEditProfileDialog()
            }

            editBadge.setOnClickListener {
                showEditProfileDialog()
            }

            // Setup feature cards
            setupFeatureCards()

            // Settings menu items
            settingsLayout.setOnClickListener {
                val intent = Intent(requireContext(), SettingsActivity::class.java)
                startActivity(intent)
            }

            helpSupportLayout.setOnClickListener {
                Toast.makeText(requireContext(), "Help & Support coming soon", Toast.LENGTH_SHORT).show()
            }

            aboutUsLayout.setOnClickListener {
                Toast.makeText(requireContext(), "About Us coming soon", Toast.LENGTH_SHORT).show()
            }

            logoutButton.setOnClickListener {
                handleLogout()
            }
        }
    }

    private fun setupFeatureCards() {
        val features = listOf(
            Feature("Bills", R.drawable.ic_bill, "#EC4899", BillsActivity::class.java),
            Feature("Staff", R.drawable.ic_staff, "#8B5CF6", StaffActivity::class.java),
            Feature("Checkbook", R.drawable.ic_book, "#6366F1", CashbookActivity::class.java),
            Feature("Shop", R.drawable.ic_collection, "#F97316", ShopActivity::class.java),
            Feature("Collections", R.drawable.ic_collection, "#10B981", LoanTrackerActivity::class.java)
        )

        binding.featureCardsGrid.removeAllViews()

        val margin = dp(8)

        features.forEach { feature ->
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_feature_card_modern, binding.featureCardsGrid, false) as MaterialCardView

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(margin, margin, margin, margin)
            }
            card.layoutParams = lp

            // Bind views
            val icon = card.findViewById<ImageView>(R.id.featureIcon)
            val title = card.findViewById<TextView>(R.id.featureTitle)
            val iconBg = card.findViewById<MaterialCardView>(R.id.iconBackground)

            // Colors
            val base = Color.parseColor(feature.color)
            val soft = softPastel(base, 0.88f)

            icon.setImageResource(feature.icon)
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(base))
            iconBg.setCardBackgroundColor(soft)
            title.text = feature.title

            card.setOnClickListener {
                startActivity(Intent(requireContext(), feature.activity))
            }

            binding.featureCardsGrid.addView(card)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun softPastel(color: Int, strength: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val nr = (r + (255 - r) * strength).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * strength).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * strength).toInt().coerceIn(0, 255)
        return Color.rgb(nr, ng, nb)
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

    private fun resolveAvatarUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return if (raw.startsWith("http", ignoreCase = true)) raw
        else SupabaseManager.getPublicFileUrl("avatars", raw)
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
        val profileInitialsView = dialogView.findViewById<TextView>(R.id.dialogProfileInitials)
        val changePhotoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.changePhotoButton)

        // Pre-fill current data
        nameInput.setText(currentUser?.fullName ?: "")

        // Load current profile image or initials
        val avatarUrl = resolveAvatarUrl(currentUser?.avatarUrl)
        if (!avatarUrl.isNullOrBlank()) {
            profileImageView.visibility = View.VISIBLE
            profileInitialsView.visibility = View.GONE
            Glide.with(this)
                .load(avatarUrl)
                .transform(CircleCrop())
                .into(profileImageView)
        } else {
            profileImageView.visibility = View.GONE
            profileInitialsView.visibility = View.VISIBLE
            val initials = when {
                !currentUser?.fullName.isNullOrEmpty() ->
                    currentUser!!.fullName!!.split(" ").take(2).joinToString("") { it.first().toString().uppercase() }
                !currentUser?.email.isNullOrEmpty() ->
                    currentUser!!.email!!.first().toString().uppercase()
                else -> "MB"
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
                        val updated = result.getOrNull()
                        val fixed = updated?.copy(avatarUrl = resolveAvatarUrl(updated.avatarUrl))
                        currentUser = fixed ?: updated
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

                selectedImageUri = null
                currentEditDialog = null

            } catch (e: Exception) {
                println("Save profile changes error: ${e.message}")
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                lifecycleScope.launch {
                    try {
                        SupabaseManager.signOut()
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error logging out", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentEditDialog?.dismiss()
        _binding = null
    }
}
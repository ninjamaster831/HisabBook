package com.guruyuknow.hisabbook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.Bills.BillsActivity
import com.guruyuknow.hisabbook.Staff.StaffActivity
import com.guruyuknow.hisabbook.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

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

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
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
    @Serializable
    data class UserProfile(
        val phone: String? = null,
        val address: String? = null
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupClickListeners()
        loadUserData()
    }

    private fun setupUI() {
        binding.apply {
            businessNameText.text = "My Business"
            emailText.text = "business@email.com"
        }
    }
    private fun formatDisplayName(raw: String?): String {
        if (raw.isNullOrBlank()) return "My Business"

        // Trim whitespace and strip common quote characters
        val cleaned = raw.trim().trim('"', '\'', '“', '”')

        // Convert to title case (each word capitalized)
        return cleaned.split(Regex("\\s+"))
            .joinToString(" ") { word ->
                val lower = word.lowercase(Locale.getDefault())
                lower.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
    }

    private fun updateUI(user: User) {
        binding.apply {
            businessNameText.text = formatDisplayName(user.fullName ?: user.email)

            emailText.text = user.email ?: ""

            val avatarUrl = resolveAvatarUrl(user.avatarUrl)

            if (!avatarUrl.isNullOrBlank()) {
                profileImage.visibility = View.VISIBLE

                Glide.with(this@ProfileFragment)
                    .load(avatarUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(profileImage)
            } else {
                profileImage.visibility = View.GONE

                val initials = when {
                    !user.fullName.isNullOrEmpty() ->
                        user.fullName.split(" ").take(2).joinToString("") { it.first().toString().uppercase() }
                    !user.email.isNullOrEmpty() ->
                        user.email.first().toString().uppercase()
                    else -> "MB"
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            editButton.setOnClickListener { showEditProfileDialog() }
            editBadge.setOnClickListener { showEditProfileDialog() }

            setupFeatureCards()

            settingsLayout.setOnClickListener {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }

            helpSupportLayout.setOnClickListener {
                startActivity(Intent(requireContext(), HelpSupportActivity::class.java))
            }

            aboutUsLayout.setOnClickListener {
                startActivity(Intent(requireContext(), AboutUsActivity::class.java))
            }

            logoutButton.setOnClickListener { handleLogout() }
        }
    }
    private fun loadAndApplyUserProfile(userId: String) {
        lifecycleScope.launch {
            try {
                val profile = SupabaseManager.getUserProfile(userId)
                profile?.let {
                    // update phone if present
                    if (!it.phone.isNullOrBlank()) {
                        binding.phoneText.text = it.phone
                    }
                    // update address if present
                    if (!it.address.isNullOrBlank()) {
                        binding.addressText.text = it.address
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Optional: show toast/log
                Log.e("ProfileFragment", "Failed to load user profile: ${e.message}")
            }
        }
    }


    private fun setupFeatureCards() {
        val features = listOf(
            Feature("Bills", R.drawable.ic_bill, "#EC4899", BillsActivity::class.java),
            Feature("Staff", R.drawable.ic_staff, "#8B5CF6", StaffActivity::class.java),
            Feature("Checkbook", R.drawable.ic_book, "#6366F1", CashbookActivity::class.java),
            Feature("Shop", R.drawable.ic_collection, "#F97316", ShopActivity::class.java),
            Feature("Collections", R.drawable.ic_collection, "#10B981", LoanTrackerActivity::class.java),
        )

        binding.featureCardsGrid.removeAllViews()

        features.forEach { feature ->
            val cardView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_feature_card_modern, binding.featureCardsGrid, false) as LinearLayout

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            cardView.layoutParams = lp

            val icon = cardView.findViewById<ImageView>(R.id.featureIcon)
            val title = cardView.findViewById<TextView>(R.id.featureTitle)
            val iconBg = cardView.findViewById<MaterialCardView>(R.id.iconBackground)

            val base = Color.parseColor(feature.color)
            val soft = softPastel(base, 0.90f)

            icon.setImageResource(feature.icon)
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(base))
            iconBg.setCardBackgroundColor(soft)
            title.text = feature.title

            cardView.setOnClickListener {
                startActivity(Intent(requireContext(), feature.activity))
            }

            binding.featureCardsGrid.addView(cardView)
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
                val user = SupabaseManager.getCurrentUser()
                user?.let {
                    currentUser = it
                    updateUI(it)

                    // NEW: load user_profiles row for additional fields
                    // replace "id" by the actual id field in your User object (commonly `id` or `userId`)
                    val userId = it.id  // adjust if user uses a different property name
                    if (!userId.isNullOrBlank()) {
                        loadAndApplyUserProfile(userId)
                    }
                }
            } catch (e: Exception) {
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
            loadUserData()
            Toast.makeText(requireContext(), "Loading user data, please try again", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val profileImageView = dialogView.findViewById<ImageView>(R.id.dialogProfileImage)
        val profileInitialsView = dialogView.findViewById<TextView>(R.id.dialogProfileInitials)
        val changePhotoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.changePhotoButton)

        nameInput.setText(currentUser?.fullName ?: "")

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
                val updatedUser = currentUser?.copy(fullName = newName)

                if (updatedUser != null) {
                    val result = SupabaseManager.updateUser(updatedUser, imageUri, requireContext())

                    if (result.isSuccess) {
                        val updated = result.getOrNull()
                        val fixed = updated?.copy(avatarUrl = resolveAvatarUrl(updated.avatarUrl))
                        currentUser = fixed ?: updated
                        currentUser?.let { updateUI(it) }
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = result.exceptionOrNull()
                        Toast.makeText(requireContext(), "Failed to update profile: ${error?.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                selectedImageUri = null
                currentEditDialog = null

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Updated handleLogout() and related methods for ProfileFragment

    private fun handleLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { dialog, _ ->
                dialog.dismiss()
                performLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                // Show loading state
                binding.logoutButton.isEnabled = false

                // 1. Sign out from Supabase
                SupabaseManager.signOut()

                // 2. Clear SessionManager (CRITICAL - this is what was missing!)
                SessionManager.clearLoginState(requireContext())

                // 3. Clear any other shared preferences
                requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()

                // 4. Sign out from Google (if using Google Sign-In)
                signOutFromGoogle()

                // 5. Navigate to login screen
                navigateToLogin()

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("ProfileFragment", "Logout error: ${e.message}")

                // Even on error, clear session and navigate
                SessionManager.clearLoginState(requireContext())
                navigateToLogin()
            }
        }
    }

    private fun signOutFromGoogle() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()

            val googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
            googleSignInClient.signOut()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Google sign out error: ${e.message}")
        }
    }

    private fun navigateToLogin() {
        try {
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                // Clear all activities and start fresh
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            startActivity(intent)

            // Finish the current activity
            requireActivity().finish()

            // Optional: Add animation
            requireActivity().overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "Navigation error: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentEditDialog?.dismiss()
        _binding = null
    }
}
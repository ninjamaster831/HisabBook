package com.guruyuknow.hisabbook

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Pad the AppBar by the status bar height; the AppBar has the gradient background
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = topInset)
            insets
        }

        setupToolbar()
        loadCurrentUser()
        setupClickListeners()
        observeSettings()
    }



    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Settings"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
                updateBusinessInfo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            // Observe theme changes
            SettingsManager.themeFlow.collectLatest { theme ->
                binding.themeText.text = theme
            }
        }

        lifecycleScope.launch {
            // Observe language changes
            SettingsManager.languageFlow.collectLatest { language ->
                binding.languageText.text = language
            }
        }

        lifecycleScope.launch {
            // Observe business info changes
            SettingsManager.businessInfoFlow.collectLatest { info ->
                if (info.name.isNotEmpty()) {
                    binding.businessNameText.text = info.name
                }
                if (info.email.isNotEmpty()) {
                    binding.businessEmailText.text = info.email
                }
            }
        }
    }

    private fun updateBusinessInfo() {
        currentUser?.let { user ->
            binding.businessNameText.text = user.fullName ?: "My Business"
            binding.businessEmailText.text = user.email ?: "Not set"
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupClickListeners() = with(binding) {
        businessInfoCard.setOnClickListener { showEditBusinessDialog() }
        notificationsCard.setOnClickListener { showNotificationsDialog() }
        themeCard.setOnClickListener { showThemeDialog() }
        languageCard.setOnClickListener { showLanguageDialog() }
        versionCard.setOnClickListener { showVersionInfo() }
        privacyCard.setOnClickListener { openPrivacyPolicy() }
        termsCard.setOnClickListener { openTermsOfService() }
    }

    private fun showEditBusinessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_business, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.businessNameInput)
        val addressInput = dialogView.findViewById<TextInputEditText>(R.id.businessAddressInput)
        val phoneInput = dialogView.findViewById<TextInputEditText>(R.id.businessPhoneInput)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.businessEmailInput)
        val gstInput = dialogView.findViewById<TextInputEditText>(R.id.gstNumberInput)

        val currentInfo = SettingsManager.getBusinessInfo()
        nameInput.setText(currentUser?.fullName ?: currentInfo.name)
        emailInput.setText(currentUser?.email ?: currentInfo.email)
        addressInput.setText(currentInfo.address)
        phoneInput.setText(currentInfo.phone)
        gstInput.setText(currentInfo.gstNumber)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edit Business Information")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val address = addressInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val gst = gstInput.text.toString().trim()

                saveBusinessInfo(name, address, phone, email, gst)
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Apply clean white rounded background to dialog window
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_white)

        // Optional: Set button colors to match your app theme (#50C9C3)
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setTextColor(android.graphics.Color.parseColor("#50C9C3"))
            negativeButton.setTextColor(android.graphics.Color.parseColor("#7A7A7A"))
        }

        dialog.show()
    }


    private fun saveBusinessInfo(name: String, address: String, phone: String, email: String, gst: String) {
        lifecycleScope.launch {
            try {
                // Update user info in Supabase if name changed
                if (name.isNotEmpty() && name != currentUser?.fullName) {
                    currentUser?.let { user ->
                        val updatedUser = user.copy(fullName = name)
                        SupabaseManager.updateUser(updatedUser, null, this@SettingsActivity)
                        currentUser = updatedUser
                    }
                }

                // Update business info in SettingsManager (local preferences/state)
                SettingsManager.updateBusinessInfo(
                    BusinessInfo(
                        name = name,
                        address = address,
                        phone = phone,
                        email = email,
                        gstNumber = gst
                    )
                )

                // --- NEW: persist address & phone to user_profiles table ---
                try {
                    currentUser?.id?.let { userId ->
                        SupabaseManager.updateUserProfile(userId, address, phone)
                    }
                } catch (dbEx: Exception) {
                    // Log but don't block UI update
                    dbEx.printStackTrace()
                }

                updateBusinessInfo()
                Toast.makeText(this@SettingsActivity, "Business information updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun showNotificationsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_notifications, null)
        val paymentReminders = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.paymentRemindersSwitch)
        val billAlerts = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.billAlertsSwitch)
        val dailyReports = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.dailyReportsSwitch)

        // Initialize switches from SettingsManager (local) — keep current behavior
        paymentReminders.isChecked = SettingsManager.isPaymentRemindersEnabled()
        billAlerts.isChecked = SettingsManager.isBillAlertsEnabled()
        dailyReports.isChecked = SettingsManager.isDailyReportsEnabled()

        // Helper to persist to DB (fire-and-forget but logs result)
        fun persistToDb(payment: Boolean, bill: Boolean, daily: Boolean) {
            lifecycleScope.launch {
                try {
                    val uid = currentUser?.id
                    if (uid.isNullOrBlank()) {
                        // no logged in user; optionally store locally only
                        Log.w("SettingsActivity", "No user id; skipping remote persist")
                        return@launch
                    }

                    val res = SupabaseManager.updateUserNotificationSettings(uid, payment, bill, daily)
                    if (res.isFailure) {
                        // show small feedback but don't block
                        res.exceptionOrNull()?.let { Log.e("SettingsActivity", "Persist notif failed: ${it.message}") }
                        Toast.makeText(this@SettingsActivity, "Failed to save notification settings to server", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("SettingsActivity", "Notification settings persisted")
                    }
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Error persisting notification settings: ${e.message}", e)
                }
            }
        }

        // When user toggles switches, update local SettingsManager and immediately persist
        paymentReminders.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setPaymentReminders(isChecked)
            // persist current combined state
            persistToDb(isChecked, billAlerts.isChecked, dailyReports.isChecked)
        }

        billAlerts.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setBillAlerts(isChecked)
            persistToDb(paymentReminders.isChecked, isChecked, dailyReports.isChecked)
        }

        dailyReports.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setDailyReports(isChecked)
            persistToDb(paymentReminders.isChecked, billAlerts.isChecked, isChecked)
        }

        // Build dialog (white background as before)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Notification Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Redundant persist on Save (ensures final state saved)
                SettingsManager.setPaymentReminders(paymentReminders.isChecked)
                SettingsManager.setBillAlerts(billAlerts.isChecked)
                SettingsManager.setDailyReports(dailyReports.isChecked)

                // Persist final state
                lifecycleScope.launch {
                    try {
                        currentUser?.id?.let { uid ->
                            val res = SupabaseManager.updateUserNotificationSettings(
                                uid,
                                paymentReminders.isChecked,
                                billAlerts.isChecked,
                                dailyReports.isChecked
                            )
                            if (res.isFailure) {
                                Toast.makeText(this@SettingsActivity, "Failed to save notification settings", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@SettingsActivity, "Notification settings updated", Toast.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Toast.makeText(this@SettingsActivity, "Settings saved locally", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "Error saving settings: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        // White background for the dialog window
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.show()
    }



    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System Default")
        val currentTheme = SettingsManager.getTheme()
        val selectedIndex = themes.indexOf(currentTheme).coerceAtLeast(0)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Select Theme")
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val selectedTheme = themes[which]

                // Persist user's choice
                SettingsManager.setTheme(selectedTheme)

                // Apply immediately across the app
                when (selectedTheme) {
                    "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "System Default" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }

                dialog.dismiss()

                // Recreate the activity so the change takes effect right away
                // (AppCompatDelegate.setDefaultNightMode affects the whole app but recreation updates this Activity UI)
                try {
                    recreate()
                } catch (e: Exception) {
                    // fallback: restart activity via intent
                    val intent = intent
                    finish()
                    startActivity(intent)
                }

                Toast.makeText(this, "Theme updated to $selectedTheme", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()

        // Make dialog window white & rounded (use your bg_dialog_white drawable)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_white)

        // Optional: style buttons when dialog is shown (use your app color)
        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            positive?.setTextColor(android.graphics.Color.parseColor("#50C9C3"))
            negative?.setTextColor(android.graphics.Color.parseColor("#7A7A7A"))
        }

        dialog.show()
    }


    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi", "Bengali", "Telugu", "Marathi", "Tamil", "Gujarati")
        val currentLanguage = SettingsManager.getLanguage()
        val selectedIndex = languages.indexOf(currentLanguage).coerceAtLeast(0)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]

                // Save language preference
                SettingsManager.setLanguage(selectedLanguage, this)



                dialog.dismiss()
                Toast.makeText(this, "Language updated to $selectedLanguage", Toast.LENGTH_SHORT).show()

                // Recreate activity to apply changes instantly
                try {
                    recreate()
                } catch (e: Exception) {
                    val intent = intent
                    finish()
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()

        // Set white rounded background
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_white)

        // Optional: set text colors for buttons
        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            positive?.setTextColor(android.graphics.Color.parseColor("#50C9C3"))
            negative?.setTextColor(android.graphics.Color.parseColor("#7A7A7A"))
        }

        dialog.show()
    }



    @RequiresApi(Build.VERSION_CODES.P)
    private fun showVersionInfo() {
        // prefer BuildConfig values (always reflect your current build)
        val versionName = try {
            BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            // fallback to package manager
            packageManager.getPackageInfo(packageName, 0).versionName
        }

        // versionCode as long (for API >= 28 use longVersionCode, else use versionCode)
        val versionCodeLong = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }

        val message = "Version: $versionName\nBuild: $versionCodeLong\n\nDeveloped with ❤️ for small businesses"

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("HisabBook")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Check Updates") { _, _ ->
                checkForUpdates()
            }
            .create()

        // Make the whole dialog white and rounded (use drawable)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_white)

        // Color the neutral/positive buttons with your app color
        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val neutral = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            positive?.setTextColor(android.graphics.Color.parseColor("#50C9C3"))
            neutral?.setTextColor(android.graphics.Color.parseColor("#50C9C3"))
        }

        dialog.show()
    }


    private fun checkForUpdates() {
        Toast.makeText(this, "You're using the latest version!", Toast.LENGTH_SHORT).show()
    }

    private fun openPrivacyPolicy() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://yourwebsite.com/privacy"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Privacy Policy coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTermsOfService() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://yourwebsite.com/terms"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Terms of Service coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}
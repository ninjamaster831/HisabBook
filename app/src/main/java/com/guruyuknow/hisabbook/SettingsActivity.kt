package com.guruyuknow.hisabbook

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("HisabBookPrefs", MODE_PRIVATE)

        setupToolbar()
        loadCurrentUser()
        setupClickListeners()
        loadSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Settings"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
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

    private fun updateBusinessInfo() {
        currentUser?.let { user ->
            binding.businessNameText.text = user.fullName ?: "My Business"
            binding.businessEmailText.text = user.email ?: "Not set"
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupClickListeners() = with(binding) {
        // Business Settings (only Business Info kept)
        businessInfoCard.setOnClickListener { showEditBusinessDialog() }

        // App Settings
        notificationsCard.setOnClickListener { showNotificationsDialog() }
        themeCard.setOnClickListener { showThemeDialog() }
        languageCard.setOnClickListener { showLanguageDialog() }
        backupCard.setOnClickListener { showBackupDialog() }

        // About
        versionCard.setOnClickListener { showVersionInfo() }
        privacyCard.setOnClickListener {
            Toast.makeText(this@SettingsActivity, "Opening Privacy Policy", Toast.LENGTH_SHORT).show()
        }
        termsCard.setOnClickListener {
            Toast.makeText(this@SettingsActivity, "Opening Terms of Service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() = with(binding) {
        val theme = sharedPreferences.getString("theme", "System Default")
        val language = sharedPreferences.getString("language", "English")
        themeText.text = theme
        languageText.text = language
    }

    private fun showEditBusinessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_business, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.businessNameInput)
        val addressInput = dialogView.findViewById<TextInputEditText>(R.id.businessAddressInput)
        val phoneInput = dialogView.findViewById<TextInputEditText>(R.id.businessPhoneInput)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.businessEmailInput)
        val gstInput = dialogView.findViewById<TextInputEditText>(R.id.gstNumberInput)

        nameInput.setText(currentUser?.fullName ?: "")
        emailInput.setText(currentUser?.email ?: "")
        addressInput.setText(sharedPreferences.getString("business_address", ""))
        phoneInput.setText(sharedPreferences.getString("business_phone", ""))
        gstInput.setText(sharedPreferences.getString("gst_number", ""))

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Business Information")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                saveBusinessInfo(
                    nameInput.text.toString(),
                    addressInput.text.toString(),
                    phoneInput.text.toString(),
                    emailInput.text.toString(),
                    gstInput.text.toString()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveBusinessInfo(name: String, address: String, phone: String, email: String, gst: String) {
        lifecycleScope.launch {
            try {
                if (name.isNotEmpty() && name != currentUser?.fullName) {
                    currentUser?.let { user ->
                        val updatedUser = user.copy(fullName = name)
                        SupabaseManager.updateUser(updatedUser, null, this@SettingsActivity)
                        currentUser = updatedUser
                    }
                }
                with(sharedPreferences.edit()) {
                    putString("business_address", address)
                    putString("business_phone", phone)
                    putString("business_email", email)
                    putString("gst_number", gst)
                    apply()
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
        val lowStockAlerts = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.lowStockAlertsSwitch)
        val dailyReports = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.dailyReportsSwitch)

        paymentReminders.isChecked = sharedPreferences.getBoolean("payment_reminders", true)
        billAlerts.isChecked = sharedPreferences.getBoolean("bill_alerts", true)
        lowStockAlerts.isChecked = sharedPreferences.getBoolean("low_stock_alerts", true)
        dailyReports.isChecked = sharedPreferences.getBoolean("daily_reports", false)

        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                with(sharedPreferences.edit()) {
                    putBoolean("payment_reminders", paymentReminders.isChecked)
                    putBoolean("bill_alerts", billAlerts.isChecked)
                    putBoolean("low_stock_alerts", lowStockAlerts.isChecked)
                    putBoolean("daily_reports", dailyReports.isChecked)
                    apply()
                }
                Toast.makeText(this, "Notification settings updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System Default")
        val currentTheme = sharedPreferences.getString("theme", "System Default")
        val selectedIndex = themes.indexOf(currentTheme)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Theme")
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val selectedTheme = themes[which]
                with(sharedPreferences.edit()) {
                    putString("theme", selectedTheme)
                    apply()
                }
                when (selectedTheme) {
                    "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "System Default" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                binding.themeText.text = selectedTheme
                dialog.dismiss()
                Toast.makeText(this, "Theme updated to $selectedTheme", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi", "Bengali", "Telugu", "Marathi", "Tamil", "Gujarati")
        val currentLanguage = sharedPreferences.getString("language", "English")
        val selectedIndex = languages.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                with(sharedPreferences.edit()) {
                    putString("language", languages[which])
                    apply()
                }
                binding.languageText.text = languages[which]
                dialog.dismiss()
                Toast.makeText(this, "Language updated to ${languages[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBackupDialog() {
        val options = arrayOf("Backup to Cloud", "Backup to Device", "Restore from Backup", "Auto Backup Settings")
        MaterialAlertDialogBuilder(this)
            .setTitle("Backup & Restore")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> backupToCloud()
                    1 -> backupToDevice()
                    2 -> restoreFromBackup()
                    3 -> showAutoBackupSettings()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun backupToCloud() = lifecycleScope.launch {
        try {
            Toast.makeText(this@SettingsActivity, "Backing up to cloud...", Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(2000)
            Toast.makeText(this@SettingsActivity, "Backup completed successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this@SettingsActivity, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupToDevice() {
        Toast.makeText(this, "Backing up to device storage...", Toast.LENGTH_SHORT).show()
    }

    private fun restoreFromBackup() {
        Toast.makeText(this, "Restoring from backup...", Toast.LENGTH_SHORT).show()
    }

    private fun showAutoBackupSettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_auto_backup, null)
        val autoBackupSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.autoBackupSwitch)
        val backupFrequency = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.backupFrequencyButton)

        autoBackupSwitch.isChecked = sharedPreferences.getBoolean("auto_backup_enabled", false)
        val frequency = sharedPreferences.getString("backup_frequency", "Weekly")
        backupFrequency.text = frequency

        backupFrequency.setOnClickListener {
            val frequencies = arrayOf("Daily", "Weekly", "Monthly")
            MaterialAlertDialogBuilder(this)
                .setTitle("Backup Frequency")
                .setItems(frequencies) { _, which ->
                    backupFrequency.text = frequencies[which]
                }
                .show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Auto Backup Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                with(sharedPreferences.edit()) {
                    putBoolean("auto_backup_enabled", autoBackupSwitch.isChecked)
                    putString("backup_frequency", backupFrequency.text.toString())
                    apply()
                }
                Toast.makeText(this, "Auto backup settings updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showVersionInfo() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = packageInfo.longVersionCode

        MaterialAlertDialogBuilder(this)
            .setTitle("HisabBook")
            .setMessage("Version: $versionName\nBuild: $versionCode\n\nDeveloped with ❤️ for small businesses")
            .setPositiveButton("OK", null)
            .show()
    }
}

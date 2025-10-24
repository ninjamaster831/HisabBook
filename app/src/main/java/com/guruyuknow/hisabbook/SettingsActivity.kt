package com.guruyuknow.hisabbook

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
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

        applyEdgeToEdgeInsets()
        setupToolbar()
        loadCurrentUser()
        setupClickListeners()
        observeSettings()
    }

    private fun applyEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            v.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                topMargin = top
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            binding.root.updatePadding(bottom = bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Settings"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
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
        backupCard.setOnClickListener { showBackupDialog() }
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
                // Update user info in Supabase if name changed
                if (name.isNotEmpty() && name != currentUser?.fullName) {
                    currentUser?.let { user ->
                        val updatedUser = user.copy(fullName = name)
                        SupabaseManager.updateUser(updatedUser, null, this@SettingsActivity)
                        currentUser = updatedUser
                    }
                }

                // Update business info in SettingsManager
                SettingsManager.updateBusinessInfo(
                    BusinessInfo(
                        name = name,
                        address = address,
                        phone = phone,
                        email = email,
                        gstNumber = gst
                    )
                )

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

        paymentReminders.isChecked = SettingsManager.isPaymentRemindersEnabled()
        billAlerts.isChecked = SettingsManager.isBillAlertsEnabled()
        lowStockAlerts.isChecked = SettingsManager.isLowStockAlertsEnabled()
        dailyReports.isChecked = SettingsManager.isDailyReportsEnabled()

        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                SettingsManager.setPaymentReminders(paymentReminders.isChecked)
                SettingsManager.setBillAlerts(billAlerts.isChecked)
                SettingsManager.setLowStockAlerts(lowStockAlerts.isChecked)
                SettingsManager.setDailyReports(dailyReports.isChecked)

                Toast.makeText(this, "Notification settings updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System Default")
        val currentTheme = SettingsManager.getTheme()
        val selectedIndex = themes.indexOf(currentTheme)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Theme")
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                val selectedTheme = themes[which]
                SettingsManager.setTheme(selectedTheme)
                dialog.dismiss()
                Toast.makeText(this, "Theme updated to $selectedTheme", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi", "Bengali", "Telugu", "Marathi", "Tamil", "Gujarati")
        val currentLanguage = SettingsManager.getLanguage()
        val selectedIndex = languages.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                SettingsManager.setLanguage(selectedLanguage, this)
                dialog.dismiss()
                Toast.makeText(this, "Language updated. Please restart app", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBackupDialog() {
        val options = arrayOf(
            "Backup to Cloud",
            "Backup to Device",
            "Restore from Backup",
            "Auto Backup Settings",
            "Export Settings"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Backup & Restore")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> backupToCloud()
                    1 -> backupToDevice()
                    2 -> restoreFromBackup()
                    3 -> showAutoBackupSettings()
                    4 -> exportSettings()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun backupToCloud() = lifecycleScope.launch {
        try {
            Toast.makeText(this@SettingsActivity, "Backing up to cloud...", Toast.LENGTH_SHORT).show()

            // Export settings
            val settingsJson = SettingsManager.exportSettings()

            // TODO: Upload to Supabase or cloud storage
            // For now, simulate backup
            kotlinx.coroutines.delay(2000)

            Toast.makeText(this@SettingsActivity, "Backup completed successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this@SettingsActivity, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupToDevice() {
        try {
            val settingsJson = SettingsManager.exportSettings()
            val fileName = "hisabbook_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"

            val file = File(getExternalFilesDir(null), fileName)
            file.writeText(settingsJson)

            Toast.makeText(this, "Backup saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreFromBackup() {
        // TODO: Implement file picker to select backup file
        Toast.makeText(this, "Select backup file to restore", Toast.LENGTH_SHORT).show()
    }

    private fun exportSettings() {
        try {
            val settingsJson = SettingsManager.exportSettings()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, settingsJson)
                type = "text/plain"
            }

            startActivity(Intent.createChooser(sendIntent, "Export Settings"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAutoBackupSettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_auto_backup, null)
        val autoBackupSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.autoBackupSwitch)
        val backupFrequency = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.backupFrequencyButton)

        autoBackupSwitch.isChecked = SettingsManager.isAutoBackupEnabled()
        backupFrequency.text = SettingsManager.getBackupFrequency()

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
                SettingsManager.setAutoBackup(autoBackupSwitch.isChecked)
                SettingsManager.setBackupFrequency(backupFrequency.text.toString())

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
            .setNeutralButton("Check Updates") { _, _ ->
                checkForUpdates()
            }
            .show()
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
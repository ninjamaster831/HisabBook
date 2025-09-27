package com.guruyuknow.hisabbook.Staff

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.User
import com.guruyuknow.hisabbook.databinding.ActivityStaffConfigurationBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class StaffConfigurationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffConfigurationBinding
    private var currentUser: User? = null

    private var selectedDate: String = ""
    private var selectedSalaryType: SalaryType = SalaryType.MONTHLY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        setupClickListeners()
        loadCurrentUser()
        populateContactData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Staff and Permissions"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupUI() {
        // Default date → today
        val today = Date()
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)
        binding.tvSalaryStartDate.text =
            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(today)

        // Default salary type
        selectedSalaryType = SalaryType.MONTHLY
        updateSalaryTypeSelection()
    }

    private fun setupClickListeners() {
        binding.layoutSalaryStartDate.setOnClickListener { showDatePicker() }

        // Card clicks
        binding.cardMonthly.setOnClickListener {
            if (selectedSalaryType != SalaryType.MONTHLY) {
                selectedSalaryType = SalaryType.MONTHLY
                updateSalaryTypeSelection()
            }
        }
        binding.cardDaily.setOnClickListener {
            if (selectedSalaryType != SalaryType.DAILY) {
                selectedSalaryType = SalaryType.DAILY
                updateSalaryTypeSelection()
            }
        }

        // Radio clicks (no RadioGroup in your XML)
        binding.radioMonthly.setOnClickListener {
            if (selectedSalaryType != SalaryType.MONTHLY) {
                selectedSalaryType = SalaryType.MONTHLY
                updateSalaryTypeSelection()
            }
        }
        binding.radioDaily.setOnClickListener {
            if (selectedSalaryType != SalaryType.DAILY) {
                selectedSalaryType = SalaryType.DAILY
                updateSalaryTypeSelection()
            }
        }

        binding.btnChange.setOnClickListener { showManualEntryFields() }

        binding.switchAttendanceSalary.setOnCheckedChangeListener { _, _ ->
            // attendance/salary permission toggled
        }
        binding.switchPermissions.setOnCheckedChangeListener { _, _ ->
            // business permission toggled
        }

        binding.btnSave.setOnClickListener { saveStaffConfiguration() }
    }

    private fun updateSalaryTypeSelection() {
        val contactName = getContactName().ifEmpty { "Staff member" }

        // Palette from your drawables/colors
        val primary = safeColor(R.color.hisab_green, android.R.color.holo_green_dark)
        val selectedBg = safeColor(R.color.hisab_green_light, android.R.color.darker_gray)
        val unselectedBg = ContextCompat.getColor(this, android.R.color.white)
        val neutralStroke = ContextCompat.getColor(this, android.R.color.darker_gray)

        when (selectedSalaryType) {
            SalaryType.MONTHLY -> {
                binding.radioMonthly.isChecked = true
                binding.radioDaily.isChecked = false

                binding.cardMonthly.strokeWidth = 3
                binding.cardMonthly.strokeColor = primary
                binding.cardMonthly.setCardBackgroundColor(selectedBg)

                binding.cardDaily.strokeWidth = 1
                binding.cardDaily.strokeColor = neutralStroke
                binding.cardDaily.setCardBackgroundColor(unselectedBg)

                binding.tvSalaryTypeDescription.text = "$contactName gets monthly salary"
                binding.tvDailyDescription.text = "$contactName gets daily salary"

                binding.tilSalaryAmount.hint = "Monthly Salary Amount"
            }

            SalaryType.DAILY -> {
                binding.radioDaily.isChecked = true
                binding.radioMonthly.isChecked = false

                binding.cardDaily.strokeWidth = 3
                binding.cardDaily.strokeColor = primary
                binding.cardDaily.setCardBackgroundColor(selectedBg)

                binding.cardMonthly.strokeWidth = 1
                binding.cardMonthly.strokeColor = neutralStroke
                binding.cardMonthly.setCardBackgroundColor(unselectedBg)

                binding.tvSalaryTypeDescription.text = "$contactName gets monthly salary"
                binding.tvDailyDescription.text = "$contactName gets daily salary"

                binding.tilSalaryAmount.hint = "Daily Salary Amount"
            }
        }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
            } catch (e: Exception) {
                Toast.makeText(
                    this@StaffConfigurationActivity,
                    "Error loading user data",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun populateContactData() {
        val contactName = intent.getStringExtra("contact_name")
        val contactPhone = intent.getStringExtra("contact_phone")
        val contactEmail = intent.getStringExtra("contact_email") // kept if you need it later

        if (!contactName.isNullOrEmpty()) {
            binding.layoutContactInfo.visibility = View.VISIBLE
            binding.layoutManualEntry.visibility = View.GONE

            binding.tvStaffName.text = contactName

            val initials = contactName
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
            binding.tvStaffInitials.text = initials
        } else {
            showManualEntryFields()
        }

        if (!contactPhone.isNullOrEmpty()) {
            binding.tvStaffPhone.text = contactPhone
        }

        updateSalaryTypeSelection()
        updatePermissionDescriptions()
    }

    private fun updatePermissionDescriptions() {
        val contactName = getContactName().ifEmpty { "Staff member" }
        binding.tvPermissionDescription.text =
            "Permissions for $contactName to manage your business on HisabBook"
    }

    private fun showManualEntryFields() {
        binding.layoutManualEntry.visibility = View.VISIBLE
        binding.layoutContactInfo.visibility = View.GONE
        binding.btnChange.visibility = View.GONE

        updateSalaryTypeSelection()
        updatePermissionDescriptions()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        if (selectedDate.isNotEmpty()) {
            try {
                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                df.parse(selectedDate)?.let { calendar.time = it }
            } catch (_: Exception) { /* ignore */ }
        }

        val dlg = DatePickerDialog(
            this,
            R.style.DatePickerTheme, // keep if you have it; else replace with android.R.style.Theme_DeviceDefault_Dialog
            { _, year, month, day ->
                calendar.set(year, month, day)
                selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                val display = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(calendar.time)
                binding.tvSalaryStartDate.text = display
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dlg.show()
    }

    private fun saveStaffConfiguration() {
        val user = currentUser
        if (user == null) {
            Toast.makeText(this, "User data not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val name = getContactName()
        val phone = getContactPhone()
        val salaryAmount = binding.etSalaryAmount.text?.toString()?.trim()?.toDoubleOrNull()

        // Validation
        if (name.isEmpty()) {
            if (binding.layoutManualEntry.visibility == View.VISIBLE) {
                binding.etManualName.error = "Please enter staff name"
                binding.etManualName.requestFocus()
            }
            Toast.makeText(this, "Please enter staff name", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            if (binding.layoutManualEntry.visibility == View.VISIBLE) {
                binding.etManualPhone.error = "Please enter phone number"
                binding.etManualPhone.requestFocus()
            }
            Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (salaryAmount == null || salaryAmount <= 0) {
            binding.etSalaryAmount.error = "Please enter valid salary amount"
            binding.etSalaryAmount.requestFocus()
            Toast.makeText(this, "Please enter valid salary amount", Toast.LENGTH_SHORT).show()
            return
        }

        // Build your Staff object (adjust package/class as per your project)
        val staff = user.id?.let { ownerId ->
            Staff(
                businessOwnerId = ownerId,
                name = name,
                phoneNumber = phone,
                email = intent.getStringExtra("contact_email"),
                salaryType = selectedSalaryType,
                salaryAmount = salaryAmount,
                salaryStartDate = selectedDate,
                hasAttendancePermission = binding.switchAttendanceSalary.isChecked,
                hasSalaryPermission = binding.switchAttendanceSalary.isChecked,
                hasBusinessPermission = binding.switchPermissions.isChecked
            )
        } ?: run {
            Toast.makeText(this, "User id missing", Toast.LENGTH_SHORT).show()
            return
        }

        // Loading UI
        setSaving(true)

        lifecycleScope.launch {
            try {
                val result = SupabaseManager.addStaff(staff)
                if (result.isSuccess) {
                    Toast.makeText(this@StaffConfigurationActivity, "Staff added successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val err = result.exceptionOrNull()
                    Toast.makeText(this@StaffConfigurationActivity, "Error: ${err?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffConfigurationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setSaving(false)
            }
        }
    }

    private fun setSaving(saving: Boolean) {
        binding.btnSave.isEnabled = !saving
        binding.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
        binding.btnSave.text = if (saving) "Saving..." else "SAVE STAFF"
    }

    private fun getContactName(): String {
        val fromIntent = intent.getStringExtra("contact_name")?.trim().orEmpty()
        return if (fromIntent.isNotEmpty()) fromIntent else binding.etManualName.text?.toString()?.trim().orEmpty()
    }

    private fun getContactPhone(): String {
        val fromIntent = intent.getStringExtra("contact_phone")?.trim().orEmpty()
        return if (fromIntent.isNotEmpty()) fromIntent else binding.etManualPhone.text?.toString()?.trim().orEmpty()
    }

    private fun safeColor(primaryId: Int, fallbackId: Int): Int =
        try { ContextCompat.getColor(this, primaryId) }
        catch (_: Exception) { ContextCompat.getColor(this, fallbackId) }
}

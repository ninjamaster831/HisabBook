package com.guruyuknow.hisabbook.Staff

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.User
import com.guruyuknow.hisabbook.databinding.ActivityStaffConfigurationBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.guruyuknow.hisabbook.R

class StaffConfigurationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffConfigurationBinding
    private var currentUser: User? = null
    private var selectedDate: String = ""
    private var selectedSalaryType: SalaryType = SalaryType.MONTHLY

    @RequiresApi(Build.VERSION_CODES.O)
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
    }

    private fun setupUI() {
        // Set default date to today
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val displayDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
        selectedDate = today
        binding.tvSalaryStartDate.text = displayDate

        // Set default salary type with fallback colors
        binding.radioGroupSalaryType.check(binding.radioMonthly.id)
        selectedSalaryType = SalaryType.MONTHLY
        updateSalaryTypeUI(SalaryType.MONTHLY)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupClickListeners() {
        binding.layoutSalaryStartDate.setOnClickListener {
            showDatePicker()
        }

        binding.radioGroupSalaryType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioMonthly.id -> {
                    selectedSalaryType = SalaryType.MONTHLY
                    updateSalaryTypeUI(SalaryType.MONTHLY)
                }
                binding.radioDaily.id -> {
                    selectedSalaryType = SalaryType.DAILY
                    updateSalaryTypeUI(SalaryType.DAILY)
                }
            }
        }

        binding.switchAttendanceSalary.setOnCheckedChangeListener { _, isChecked ->
            // Handle attendance & salary permission toggle
        }

        binding.switchPermissions.setOnCheckedChangeListener { _, isChecked ->
            // Handle business permissions toggle
        }

        binding.btnSave.setOnClickListener {
            saveStaffConfiguration()
        }
    }

    private fun updateSalaryTypeUI(salaryType: SalaryType) {
        val contactName = getContactName()
        val primaryColor = try { ContextCompat.getColor(this, R.color.colorPrimary) } catch (e: Exception) { -0x9e0000 } // Fallback to #6200EE
        val grayColor = try { ContextCompat.getColor(this, android.R.color.darker_gray) } catch (e: Exception) { -0x222222 } // Fallback to #222222
        when (salaryType) {
            SalaryType.MONTHLY -> {
                binding.radioMonthly.isChecked = true
                binding.radioDaily.isChecked = false
                binding.cardMonthly.strokeWidth = 2
                binding.cardMonthly.strokeColor = primaryColor
                binding.cardDaily.strokeWidth = 1
                binding.cardDaily.strokeColor = grayColor
                binding.tvSalaryTypeDescription.text = "$contactName gets monthly salary"
                binding.tvDailyDescription.text = "$contactName gets daily salary"
            }
            SalaryType.DAILY -> {
                binding.radioDaily.isChecked = true
                binding.radioMonthly.isChecked = false
                binding.cardDaily.strokeWidth = 2
                binding.cardDaily.strokeColor = primaryColor
                binding.cardMonthly.strokeWidth = 1
                binding.cardMonthly.strokeColor = grayColor
                binding.tvSalaryTypeDescription.text = "$contactName gets monthly salary"
                binding.tvDailyDescription.text = "$contactName gets daily salary"
            }
        }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
            } catch (e: Exception) {
                Toast.makeText(this@StaffConfigurationActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateContactData() {
        val contactName = intent.getStringExtra("contact_name")
        val contactPhone = intent.getStringExtra("contact_phone")
        val contactEmail = intent.getStringExtra("contact_email")

        contactName?.let {
            binding.tvStaffName.text = it
            binding.tvSalaryTypeDescription.text = "$it gets monthly salary"
            binding.tvDailyDescription.text = "$it gets daily salary"

            // Set initials
            val initials = it.split(" ").take(2).joinToString("") { word ->
                word.first().toString().uppercase()
            }
            binding.tvStaffInitials.text = initials
        }

        contactPhone?.let {
            binding.tvStaffPhone.text = it
        }

        if (contactName == null) {
            showManualEntryFields()
        }
    }

    private fun showManualEntryFields() {
        binding.layoutManualEntry.visibility = View.VISIBLE
        binding.layoutContactInfo.visibility = View.GONE
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                val displayDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(calendar.time)
                binding.tvSalaryStartDate.text = displayDate
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveStaffConfiguration() {
        if (currentUser == null) {
            Toast.makeText(this, "User data not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val name = getContactName()
        val phone = getContactPhone()
        val salaryAmount = binding.etSalaryAmount.text.toString().toDoubleOrNull()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter staff name", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (salaryAmount == null || salaryAmount <= 0) {
            Toast.makeText(this, "Please enter valid salary amount", Toast.LENGTH_SHORT).show()
            return
        }

        val staff = currentUser!!.id?.let {
            Staff(
                businessOwnerId = it,
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
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = staff?.let { SupabaseManager.addStaff(it) }
                if (result != null) {
                    if (result.isSuccess) {
                        Toast.makeText(this@StaffConfigurationActivity, "Staff added successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        val error = result.exceptionOrNull()
                        Toast.makeText(this@StaffConfigurationActivity, "Error: ${error?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffConfigurationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun getContactName(): String {
        return intent.getStringExtra("contact_name")?.takeIf { it.isNotEmpty() }
            ?: binding.etManualName.text.toString().trim()
    }

    private fun getContactPhone(): String {
        return intent.getStringExtra("contact_phone")?.takeIf { it.isNotEmpty() }
            ?: binding.etManualPhone.text.toString().trim()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
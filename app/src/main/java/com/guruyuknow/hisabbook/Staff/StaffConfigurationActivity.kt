package com.guruyuknow.hisabbook.Staff

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.User
import com.guruyuknow.hisabbook.databinding.ActivityStaffConfigurationBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class StaffConfigurationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffConfigurationBinding
    private var currentUser: User? = null
    private var selectedDate: String = ""
    private var selectedSalaryType: SalaryType = SalaryType.MONTHLY
    private var isSaving = false

    private val currencyFormatter by lazy {
        NumberFormat.getInstance(Locale("en", "IN"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityStaffConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        applyWindowInsets()
        setupUI()
        setupClickListeners()
        attachCurrencyFormatter()
        loadCurrentUser()
        populateContactData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Add Staff Member"
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            binding.appBar.setPadding(
                binding.appBar.paddingLeft,
                bars.top,
                binding.appBar.paddingRight,
                binding.appBar.paddingBottom
            )

            val bottomPadding = bars.bottom + resources.getDimensionPixelSize(R.dimen.spacing_large)
            binding.content.updatePadding(bottom = bottomPadding)
            binding.scroll.updatePadding(bottom = bottomPadding)

            binding.btnSave.translationY = -bars.bottom.toFloat()

            insets
        }
    }

    private fun setupUI() {
        val today = Date()
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)
        binding.tvSalaryStartDate.text =
            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(today)

        selectedSalaryType = SalaryType.MONTHLY
        updateSalaryTypeSelection()
    }

    private fun setupClickListeners() {
        binding.layoutSalaryStartDate.setOnClickListener { showDatePicker() }

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

        binding.etSalaryAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnSave.performClick()
                true
            } else false
        }

        binding.btnSave.setOnClickListener {
            if (!isSaving) {
                saveStaffConfiguration()
            }
        }
    }

    private fun attachCurrencyFormatter() {
        var isFormatting = false

        binding.etSalaryAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return

                val input = s?.toString()?.trim() ?: return
                if (input.isEmpty()) return

                // Remove all non-digit characters
                val digitsOnly = input.replace(Regex("[^\\d]"), "")
                if (digitsOnly.isEmpty()) {
                    s?.clear()
                    return
                }

                try {
                    val amount = digitsOnly.toLong()

                    isFormatting = true

                    // Format with Indian number system
                    val formatted = currencyFormatter.format(amount)
                    s?.replace(0, s.length, formatted)

                    isFormatting = false
                } catch (e: Exception) {
                    // Handle invalid input
                    isFormatting = false
                }
            }
        })

        // Input filter to allow only digits
        binding.etSalaryAmount.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                for (i in start until end) {
                    if (!Character.isDigit(source[i]) && source[i] != ',') {
                        return@InputFilter ""
                    }
                }
                null
            }
        )
    }

    private fun updateSalaryTypeSelection() {
        val contactName = getContactName().ifEmpty { "Staff member" }
        val primary = safeColor(R.color.hisab_green)
        val selectedBg = safeColor(R.color.hisab_green_light)
        val unselectedBg = ContextCompat.getColor(this, android.R.color.white)
        val neutralStroke = ContextCompat.getColor(this, android.R.color.darker_gray)

        when (selectedSalaryType) {
            SalaryType.MONTHLY -> {
                binding.radioMonthly.isChecked = true
                binding.radioDaily.isChecked = false

                binding.cardMonthly.strokeWidth = resources.getDimensionPixelSize(R.dimen.spacing_tiny)
                binding.cardMonthly.strokeColor = primary
                binding.cardMonthly.setCardBackgroundColor(selectedBg)

                binding.cardDaily.strokeWidth = 1
                binding.cardDaily.strokeColor = neutralStroke
                binding.cardDaily.setCardBackgroundColor(unselectedBg)

                binding.tvSalaryTypeDescription.text = "$contactName gets a fixed monthly salary"
                binding.tvDailyDescription.text = "Salary calculated based on daily attendance"
                binding.tilSalaryAmount.hint = "Monthly Salary Amount"
            }
            SalaryType.DAILY -> {
                binding.radioDaily.isChecked = true
                binding.radioMonthly.isChecked = false

                binding.cardDaily.strokeWidth = resources.getDimensionPixelSize(R.dimen.spacing_tiny)
                binding.cardDaily.strokeColor = primary
                binding.cardDaily.setCardBackgroundColor(selectedBg)

                binding.cardMonthly.strokeWidth = 1
                binding.cardMonthly.strokeColor = neutralStroke
                binding.cardMonthly.setCardBackgroundColor(unselectedBg)

                binding.tvSalaryTypeDescription.text = "Fixed salary paid every month"
                binding.tvDailyDescription.text = "$contactName gets paid based on daily attendance"
                binding.tilSalaryAmount.hint = "Daily Salary Amount"
            }
        }

        updatePermissionDescriptions()
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
            } catch (e: Exception) {
                Toast.makeText(
                    this@StaffConfigurationActivity,
                    "Error loading user data: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun populateContactData() {
        val contactName = intent.getStringExtra("contact_name")
        val contactPhone = intent.getStringExtra("contact_phone")

        if (!contactName.isNullOrEmpty()) {
            binding.layoutContactInfo.visibility = View.VISIBLE
            binding.layoutManualEntry.visibility = View.GONE

            binding.tvStaffName.text = contactName
            val initials = generateInitials(contactName)
            binding.tvStaffInitials.text = initials
        } else {
            showManualEntryFields()
        }

        if (!contactPhone.isNullOrEmpty()) {
            binding.tvStaffPhone.text = formatPhoneNumber(contactPhone)
        }

        updateSalaryTypeSelection()
        updatePermissionDescriptions()
    }

    private fun updatePermissionDescriptions() {
        val contactName = getContactName().ifEmpty { "Staff member" }
        binding.tvPermissionDescription.text =
            "Grant $contactName permissions to manage your business on HisabBook"
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
            } catch (_: Exception) { }
        }

        val dialog = DatePickerDialog(
            this,
            R.style.DatePickerTheme,
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

        dialog.show()
    }

    private fun parseAmountOrNull(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null

        return try {
            val digitsOnly = raw.replace(Regex("[^\\d]"), "")
            digitsOnly.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun saveStaffConfiguration() {
        if (isSaving) return

        val user = currentUser
        if (user?.id == null) {
            Toast.makeText(this, "Please wait, loading user data...", Toast.LENGTH_SHORT).show()
            return
        }

        val name = getContactName()
        val phone = getContactPhone()
        val salaryAmount = parseAmountOrNull(binding.etSalaryAmount.text?.toString()?.trim())

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

        if (phone.length < 10) {
            if (binding.layoutManualEntry.visibility == View.VISIBLE) {
                binding.etManualPhone.error = "Please enter valid phone number"
                binding.etManualPhone.requestFocus()
            }
            Toast.makeText(this, "Please enter valid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (salaryAmount == null || salaryAmount <= 0) {
            binding.etSalaryAmount.error = "Please enter valid salary amount"
            binding.etSalaryAmount.requestFocus()
            Toast.makeText(this, "Please enter valid salary amount", Toast.LENGTH_SHORT).show()
            return
        }

        val staff = Staff(
            businessOwnerId = user.id,
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

        setSaving(true)

        lifecycleScope.launch {
            try {
                val result = SupabaseManager.addStaff(staff)

                if (result.isSuccess) {
                    Toast.makeText(
                        this@StaffConfigurationActivity,
                        "Staff added successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(
                        this@StaffConfigurationActivity,
                        "Error: $errorMsg",
                        Toast.LENGTH_SHORT
                    ).show()
                    setSaving(false)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@StaffConfigurationActivity,
                    "Error: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
                setSaving(false)
            }
        }
    }

    private fun setSaving(saving: Boolean) {
        isSaving = saving
        binding.btnSave.isEnabled = !saving
        binding.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
        binding.btnSave.text = if (saving) "Saving..." else "SAVE STAFF"

        // Disable input during save
        binding.etSalaryAmount.isEnabled = !saving
        binding.etManualName.isEnabled = !saving
        binding.etManualPhone.isEnabled = !saving
        binding.cardMonthly.isEnabled = !saving
        binding.cardDaily.isEnabled = !saving
        binding.switchAttendanceSalary.isEnabled = !saving
        binding.switchPermissions.isEnabled = !saving
    }

    private fun getContactName(): String {
        val fromIntent = intent.getStringExtra("contact_name")?.trim().orEmpty()
        return if (fromIntent.isNotEmpty()) {
            fromIntent
        } else {
            binding.etManualName.text?.toString()?.trim().orEmpty()
        }
    }

    private fun getContactPhone(): String {
        val fromIntent = intent.getStringExtra("contact_phone")?.trim().orEmpty()
        return if (fromIntent.isNotEmpty()) {
            fromIntent
        } else {
            binding.etManualPhone.text?.toString()?.trim().orEmpty()
        }
    }

    private fun generateInitials(name: String): String {
        return name.trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }

    private fun formatPhoneNumber(phone: String): String {
        return if (phone.length == 10) {
            "${phone.substring(0, 5)} ${phone.substring(5)}"
        } else {
            phone
        }
    }

    private fun safeColor(colorRes: Int): Int {
        return try {
            ContextCompat.getColor(this, colorRes)
        } catch (e: Exception) {
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
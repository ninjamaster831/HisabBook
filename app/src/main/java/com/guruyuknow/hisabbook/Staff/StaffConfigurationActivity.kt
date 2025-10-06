package com.guruyuknow.hisabbook.Staff

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class StaffConfigurationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffConfigurationBinding
    private var currentUser: User? = null

    private var selectedDate: String = ""
    private var selectedSalaryType: SalaryType = SalaryType.MONTHLY

    private val nfIN: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge; we’ll lay out under system bars and add insets ourselves
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Staff and Permissions"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    /** Make status bar/gesture/nav safe for AppBar, Scroll & FAB */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            // Status bar padding for AppBar
            binding.appBar.setPadding(
                binding.appBar.paddingLeft,
                bars.top,
                binding.appBar.paddingRight,
                binding.appBar.paddingBottom
            )

            // Content bottom padding so last card & progress bar aren’t hidden
            val bottom = bars.bottom
            binding.content.updatePadding(bottom = binding.content.paddingBottom + bottom)
            // Also pad the scroll to allow overscroll space
            binding.scroll.updatePadding(bottom = binding.scroll.paddingBottom + bottom)

            // Shift the Extended FAB up
            binding.btnSave.translationY = -bottom.toFloat()

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

        // Cards toggle
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

        // Radios (no RadioGroup in XML)
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

        binding.switchAttendanceSalary.setOnCheckedChangeListener { _, _ -> /* hook */ }
        binding.switchPermissions.setOnCheckedChangeListener { _, _ -> /* hook */ }

        binding.etSalaryAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnSave.performClick()
                true
            } else false
        }

        binding.btnSave.setOnClickListener { saveStaffConfiguration() }
    }

    private fun attachCurrencyFormatter() {
        binding.etSalaryAmount.addTextChangedListener(object : TextWatcher {
            private var selfChange = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (selfChange) return
                val raw = s?.toString().orEmpty()
                if (raw.isEmpty()) return

                // Keep only digits & dot
                val cleaned = raw.replace("[^\\d.]".toRegex(), "")
                if (cleaned.isEmpty()) return

                val value = try {
                    cleaned.toDouble()
                } catch (_: NumberFormatException) {
                    return
                }

                val cursorAtEnd = binding.etSalaryAmount.selectionStart == raw.length
                val formatted = nfIN.format(value).replace("₹", "").trim() // we already show prefix "₹ "
                selfChange = true
                binding.etSalaryAmount.setText(formatted)
                // Restore cursor
                val newPos = if (cursorAtEnd) binding.etSalaryAmount.text?.length ?: 0
                else (formatted.length).coerceAtMost(binding.etSalaryAmount.text?.length ?: 0)
                binding.etSalaryAmount.setSelection(newPos)
                selfChange = false
            }
        })
    }

    private fun updateSalaryTypeSelection() {
        val contactName = getContactName().ifEmpty { "Staff member" }
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

        if (!contactName.isNullOrEmpty()) {
            binding.layoutContactInfo.visibility = View.VISIBLE
            binding.layoutManualEntry.visibility = View.GONE

            binding.tvStaffName.text = contactName
            val initials = contactName.split(" ")
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
        dlg.show()
    }

    private fun parseAmountOrNull(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        // Input shows only numbers and separators (we removed the ₹ prefix already)
        val txt = raw.replace("[,\\s]".toRegex(), "")
        return try { txt.toDouble() } catch (_: NumberFormatException) { null }
    }

    private fun saveStaffConfiguration() {
        val user = currentUser
        if (user == null) {
            Toast.makeText(this, "User data not loaded", Toast.LENGTH_SHORT).show()
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
        if (salaryAmount == null || salaryAmount <= 0) {
            binding.etSalaryAmount.error = "Please enter valid salary amount"
            binding.etSalaryAmount.requestFocus()
            Toast.makeText(this, "Please enter valid salary amount", Toast.LENGTH_SHORT).show()
            return
        }

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

        setSaving(true)
        lifecycleScope.launch {
            try {
                val result = SupabaseManager.addStaff(staff)
                if (result.isSuccess) {
                    Toast.makeText(this@StaffConfigurationActivity, "Staff added successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@StaffConfigurationActivity,
                        "Error: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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

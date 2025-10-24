package com.guruyuknow.hisabbook.Staff

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.databinding.ActivityStaffPermissionsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StaffPermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffPermissionsBinding
    private var currentStaff: Staff? = null
    private lateinit var staffId: String
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityStaffPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        staffId = intent.getStringExtra("staff_id") ?: ""
        if (staffId.isEmpty()) {
            Toast.makeText(this, "Invalid staff ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupClickListeners()
        applyWindowInsets()
        loadStaffData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Staff Permissions"
        }
    }

    private fun setupClickListeners() {
        binding.btnSavePermissions.setOnClickListener {
            if (!isSaving) {
                savePermissions()
            }
        }

        // Add listeners to show descriptions
        binding.switchAttendance.setOnCheckedChangeListener { _, isChecked ->
            updatePermissionDescription()
        }

        binding.switchSalary.setOnCheckedChangeListener { _, isChecked ->
            updatePermissionDescription()
        }

        binding.switchBusiness.setOnCheckedChangeListener { _, isChecked ->
            updatePermissionDescription()
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            // Status bar padding
            binding.appBar?.setPadding(
                binding.appBar.paddingLeft,
                bars.top,
                binding.appBar.paddingRight,
                binding.appBar.paddingBottom
            )

            // Bottom padding for save button
            binding.btnSavePermissions.translationY = -bars.bottom.toFloat()

            // Content padding
            binding.scrollView?.setPadding(
                binding.scrollView.paddingLeft,
                binding.scrollView.paddingTop,
                binding.scrollView.paddingRight,
                bars.bottom + resources.getDimensionPixelSize(com.guruyuknow.hisabbook.R.dimen.spacing_large)
            )

            insets
        }
    }

    private fun loadStaffData() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val staffResult = SupabaseManager.getStaffById(staffId)

                if (staffResult.isSuccess) {
                    currentStaff = staffResult.getOrNull()
                    currentStaff?.let { staff ->
                        updateUI(staff)
                        showLoading(false)
                    } ?: run {
                        showError("Staff data not found")
                    }
                } else {
                    showError("Failed to load staff data: ${staffResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showError("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun updateUI(staff: Staff) {
        binding.apply {
            // Update staff info
            tvStaffName.text = staff.name

            val initials = generateInitials(staff.name)
            tvStaffInitials.text = initials

            // Set current permissions
            switchAttendance.isChecked = staff.hasAttendancePermission
            switchSalary.isChecked = staff.hasSalaryPermission
            switchBusiness.isChecked = staff.hasBusinessPermission

            updatePermissionDescription()
        }
    }

    private fun updatePermissionDescription() {
        val permissionCount = listOf(
            binding.switchAttendance.isChecked,
            binding.switchSalary.isChecked,
            binding.switchBusiness.isChecked
        ).count { it }

        binding.tvPermissionsCount?.text = when (permissionCount) {
            0 -> "No permissions granted"
            1 -> "1 permission granted"
            else -> "$permissionCount permissions granted"
        }
    }

    private fun savePermissions() {
        if (isSaving) return

        val staff = currentStaff
        if (staff == null) {
            Toast.makeText(this, "Staff data not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        setSavingState(true)

        lifecycleScope.launch {
            try {
                val updatedStaff = staff.copy(
                    hasAttendancePermission = binding.switchAttendance.isChecked,
                    hasSalaryPermission = binding.switchSalary.isChecked,
                    hasBusinessPermission = binding.switchBusiness.isChecked,
                    updatedAt = getCurrentTimestamp()
                )

                val result = SupabaseManager.updateStaff(updatedStaff)

                if (result.isSuccess) {
                    Toast.makeText(
                        this@StaffPermissionsActivity,
                        "Permissions updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(
                        this@StaffPermissionsActivity,
                        "Error: $errorMsg",
                        Toast.LENGTH_SHORT
                    ).show()
                    setSavingState(false)
                    isSaving = false
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@StaffPermissionsActivity,
                    "Error: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
                setSavingState(false)
                isSaving = false
            }
        }
    }

    private fun setSavingState(saving: Boolean) {
        binding.apply {
            btnSavePermissions.isEnabled = !saving
            progressBar?.visibility = if (saving) View.VISIBLE else View.GONE
            btnSavePermissions.text = if (saving) "Saving..." else "SAVE PERMISSIONS"

            // Disable switches during save
            switchAttendance.isEnabled = !saving
            switchSalary.isEnabled = !saving
            switchBusiness.isEnabled = !saving
        }
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            loadingOverlay?.visibility = if (show) View.VISIBLE else View.GONE
            contentLayout?.visibility = if (show) View.GONE else View.VISIBLE
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        showLoading(false)
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

    private fun getCurrentTimestamp(): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.now().toString()
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
            }
        } catch (e: Exception) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
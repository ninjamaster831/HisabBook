package com.guruyuknow.hisabbook.Staff

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.databinding.ActivityStaffPermissionsBinding
import kotlinx.coroutines.launch
import java.util.*

class StaffPermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffPermissionsBinding
    private var currentStaff: Staff? = null
    private lateinit var staffId: String

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        staffId = intent.getStringExtra("staff_id") ?: ""
        if (staffId.isEmpty()) {
            finish()
            return
        }

        setupToolbar()
        setupClickListeners()
        loadStaffData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Staff Permissions"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupClickListeners() {
        binding.btnSavePermissions.setOnClickListener {
            savePermissions()
        }
    }

    private fun loadStaffData() {
        lifecycleScope.launch {
            try {
                val staffResult = SupabaseManager.getStaffById(staffId)
                if (staffResult.isSuccess) {
                    currentStaff = staffResult.getOrNull()
                    currentStaff?.let { staff ->
                        binding.tvStaffName.text = staff.name
                        binding.switchAttendance.isChecked = staff.hasAttendancePermission
                        binding.switchSalary.isChecked = staff.hasSalaryPermission
                        binding.switchBusiness.isChecked = staff.hasBusinessPermission

                        val initials = staff.name.split(" ").take(2).joinToString("") {
                            it.first().toString().uppercase()
                        }
                        binding.tvStaffInitials.text = initials
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffPermissionsActivity, "Error loading staff data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun savePermissions() {
        lifecycleScope.launch {
            try {
                currentStaff?.let { staff ->
                    val updatedStaff = staff.copy(
                        hasAttendancePermission = binding.switchAttendance.isChecked,
                        hasSalaryPermission = binding.switchSalary.isChecked,
                        hasBusinessPermission = binding.switchBusiness.isChecked,
                        updatedAt = Date().toInstant().toString()
                    )

                    val result = SupabaseManager.updateStaff(updatedStaff)
                    if (result.isSuccess) {
                        Toast.makeText(this@StaffPermissionsActivity, "Permissions updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@StaffPermissionsActivity, "Error updating permissions", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffPermissionsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

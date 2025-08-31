// Here's the corrected StaffDetailsActivity.kt with all errors fixed:

package com.guruyuknow.hisabbook.Staff

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.databinding.ActivityStaffDetailsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StaffDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffDetailsBinding
    private lateinit var attendanceAdapter: AttendanceAdapter
    private val attendanceList = mutableListOf<Attendance>()
    private var currentStaff: Staff? = null
    private lateinit var staffId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        staffId = intent.getStringExtra("staff_id") ?: ""
        if (staffId.isEmpty()) {
            finish()
            return
        }

        setupUI()
        loadStaffDetails(staffId)
        loadAttendanceHistory(staffId)
        setupClickListeners()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Staff Details"

        attendanceAdapter = AttendanceAdapter(attendanceList)
        binding.recyclerViewAttendance.apply {
            layoutManager = LinearLayoutManager(this@StaffDetailsActivity)
            adapter = attendanceAdapter
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupClickListeners() {
        binding.btnMarkAttendance.setOnClickListener {
            currentStaff?.let { staff ->
                AttendanceDialogFragment.newInstance(staff.id, staff.name)
                    .show(supportFragmentManager, "attendance_dialog")
            }
        }

        binding.btnCalculateSalary.setOnClickListener {
            calculateMonthlySalary()
        }

        // Modified to remove SalaryHistoryActivity reference
        binding.btnViewSalaryHistory.setOnClickListener {
            currentStaff?.let { staff ->
                // Option 1: Show toast for now
                Toast.makeText(this, "Salary history feature coming soon for ${staff.name}", Toast.LENGTH_SHORT).show()

                // Option 2: Or you can create a simple dialog showing salary info
                // showSalaryInfoDialog(staff)
            }
        }
    }

    private fun loadStaffDetails(staffId: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = SupabaseManager.getStaffById(staffId)
                if (result.isSuccess) {
                    currentStaff = result.getOrNull()
                    currentStaff?.let { staff ->
                        displayStaffInfo(staff)
                        calculateAttendanceSummary(staff)
                    }
                } else {
                    Toast.makeText(this@StaffDetailsActivity, "Error loading staff details", Toast.LENGTH_SHORT).show()
                }

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@StaffDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayStaffInfo(staff: Staff) {
        binding.apply {
            tvStaffName.text = staff.name
            tvStaffPhone.text = staff.phoneNumber
            tvSalaryType.text = when (staff.salaryType) {
                SalaryType.MONTHLY -> "Monthly Salary"
                SalaryType.DAILY -> "Daily Salary"
            }
            tvSalaryAmount.text = "₹${staff.salaryAmount.toInt()}"

            // Format salary start date
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(staff.salaryStartDate)
                tvSalaryStartDate.text = "Since ${outputFormat.format(date ?: Date())}"
            } catch (e: Exception) {
                tvSalaryStartDate.text = "Since ${staff.salaryStartDate}"
            }

            // Set initials
            val initials = staff.name.split(" ").take(2).joinToString("") {
                it.first().toString().uppercase()
            }
            tvStaffInitials.text = initials

            // Show permissions
            tvPermissions.text = buildString {
                val permissions = mutableListOf<String>()
                if (staff.hasAttendancePermission) permissions.add("Attendance")
                if (staff.hasSalaryPermission) permissions.add("Salary")
                if (staff.hasBusinessPermission) permissions.add("Business")

                if (permissions.isNotEmpty()) {
                    append("Permissions: ${permissions.joinToString(", ")}")
                } else {
                    append("No special permissions")
                }
            }
        }
    }

    private fun loadAttendanceHistory(staffId: String) {
        lifecycleScope.launch {
            try {
                val result = SupabaseManager.getAttendanceByStaff(staffId, 30)
                if (result.isSuccess) {
                    val attendance = result.getOrNull() ?: emptyList()
                    attendanceList.clear()
                    attendanceList.addAll(attendance)
                    attendanceAdapter.notifyDataSetChanged()

                    if (attendance.isEmpty()) {
                        binding.tvNoAttendance.visibility = View.VISIBLE
                        binding.recyclerViewAttendance.visibility = View.GONE
                    } else {
                        binding.tvNoAttendance.visibility = View.GONE
                        binding.recyclerViewAttendance.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@StaffDetailsActivity, "Error loading attendance", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateAttendanceSummary(staff: Staff) {
        lifecycleScope.launch {
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val result = SupabaseManager.getAttendanceByStaffAndMonth(staff.id, currentMonth)

                if (result.isSuccess) {
                    val monthlyAttendance = result.getOrNull() ?: emptyList()

                    val presentDays = monthlyAttendance.count {
                        it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                    }
                    val absentDays = monthlyAttendance.count { it.status == AttendanceStatus.ABSENT }
                    val halfDays = monthlyAttendance.count { it.status == AttendanceStatus.HALF_DAY }
                    val totalDays = monthlyAttendance.size

                    binding.apply {
                        tvThisMonthPresent.text = "$presentDays days"
                        tvThisMonthAbsent.text = "$absentDays days"
                        tvThisMonthHalfDay.text = "$halfDays days"
                        tvThisMonthTotal.text = "Total: $totalDays days"
                    }

                    // Calculate estimated salary for this month
                    calculateEstimatedSalary(staff, presentDays, halfDays)
                }
            } catch (e: Exception) {
                // Handle error silently or show message
            }
        }
    }

    private fun calculateEstimatedSalary(staff: Staff, presentDays: Int, halfDays: Int) {
        val estimatedSalary = when (staff.salaryType) {
            SalaryType.MONTHLY -> staff.salaryAmount
            SalaryType.DAILY -> {
                val workingDays = presentDays + (halfDays * 0.5)
                staff.salaryAmount * workingDays
            }
        }

        binding.tvEstimatedSalary.text = "Estimated Salary: ₹${estimatedSalary.toInt()}"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateMonthlySalary() {
        lifecycleScope.launch {
            try {
                currentStaff?.let { staff ->
                    binding.btnCalculateSalary.isEnabled = false

                    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                    val result = SupabaseManager.calculateMonthlySalary(staff.id, currentMonth)

                    if (result.isSuccess) {
                        val salary = result.getOrNull()
                        if (salary != null) {
                            val saveResult = SupabaseManager.saveSalary(salary)
                            if (saveResult.isSuccess) {
                                Toast.makeText(this@StaffDetailsActivity,
                                    "Monthly salary calculated: ₹${salary.finalSalary.toInt()}",
                                    Toast.LENGTH_LONG).show()

                                // Refresh attendance summary
                                calculateAttendanceSummary(staff)
                            } else {
                                Toast.makeText(this@StaffDetailsActivity,
                                    "Error saving salary calculation",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@StaffDetailsActivity,
                            "Error calculating salary",
                            Toast.LENGTH_SHORT).show()
                    }

                    binding.btnCalculateSalary.isEnabled = true
                }
            } catch (e: Exception) {
                binding.btnCalculateSalary.isEnabled = true
                Toast.makeText(this@StaffDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Menu functions (now will work with the menu file)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_staff_details, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit_staff -> {
                currentStaff?.let { staff ->
                    val intent = Intent(this, StaffConfigurationActivity::class.java)
                    intent.putExtra("staff_id", staff.id)
                    intent.putExtra("contact_name", staff.name)
                    intent.putExtra("contact_phone", staff.phoneNumber)
                    intent.putExtra("contact_email", staff.email)
                    startActivity(intent)
                }
                true
            }
            R.id.action_permissions -> {
                currentStaff?.let { staff ->
                    val intent = Intent(this, StaffPermissionsActivity::class.java)
                    intent.putExtra("staff_id", staff.id)
                    startActivity(intent)
                }
                true
            }
            R.id.action_delete_staff -> {
                currentStaff?.let { staff ->
                    showDeleteConfirmationDialog(staff)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmationDialog(staff: Staff) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Staff Member")
            .setMessage("Are you sure you want to delete ${staff.name}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteStaff(staff.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteStaff(staffId: String) {
        lifecycleScope.launch {
            try {
                val result = SupabaseManager.deleteStaff(staffId)
                if (result.isSuccess) {
                    Toast.makeText(this@StaffDetailsActivity, "Staff member deleted successfully", Toast.LENGTH_SHORT).show()
                    finish() // Close activity and return to staff list
                } else {
                    Toast.makeText(this@StaffDetailsActivity, "Error deleting staff member", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from other activities
        loadAttendanceHistory(staffId)
        currentStaff?.let { calculateAttendanceSummary(it) }
    }
}
package com.guruyuknow.hisabbook.Staff

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.SupabaseManager.AttendanceSummary
import com.guruyuknow.hisabbook.databinding.ActivityStaffDetailsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StaffDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffDetailsBinding
    private lateinit var attendanceAdapter: AttendanceAdapter
    private val attendanceList = mutableListOf<Attendance>()
    private var currentStaff: Staff? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val staffId = intent.getStringExtra("staff_id")
        if (staffId != null) {
            setupUI()
            loadStaffData(staffId)
        } else {
            finish()  // If no staffId, finish the activity
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        // Initialize the attendance adapter
        attendanceAdapter = AttendanceAdapter(attendanceList)
        binding.recyclerViewAttendance.apply {
            layoutManager = LinearLayoutManager(this@StaffDetailsActivity)
            adapter = attendanceAdapter
        }

        // Handle FAB click for editing staff
        binding.fabEditStaff.setOnClickListener {
            // Handle edit logic here
            Toast.makeText(this, "Edit functionality will be implemented", Toast.LENGTH_SHORT).show()
        }

        // Show loading state
        showLoading(true)
    }

    private fun loadStaffData(staffId: String) {
        lifecycleScope.launch {
            try {
                // Fetch staff details from the database
                val staffResult = SupabaseManager.getStaffById(staffId)
                if (staffResult.isSuccess) {
                    currentStaff = staffResult.getOrNull()
                    currentStaff?.let { staff ->
                        updateStaffUI(staff)
                        loadAttendanceData(staffId)
                    }
                } else {
                    showError("Error loading staff details")
                }
            } catch (e: Exception) {
                showError("Error loading staff data: ${e.message}")
            }
        }
    }

    private fun updateStaffUI(staff: Staff) {
        binding.apply {
            // Update staff header information
            tvStaffNameLarge.text = staff.name
            tvStaffPhoneLarge.text = staff.phoneNumber

            // Set initials
            val initials = staff.name.split(" ").take(2).joinToString("") {
                it.first().toString().uppercase()
            }
            tvStaffInitialsLarge.text = initials

            // Update salary information
            tvSalaryType.text = when (staff.salaryType) {
                SalaryType.MONTHLY -> "Monthly"
                SalaryType.DAILY -> "Daily"
            }
            tvSalaryAmount.text = "₹${staff.salaryAmount.toInt()}"

            // Update toolbar title
            supportActionBar?.title = staff.name
        }
    }

    private fun loadAttendanceData(staffId: String) {
        lifecycleScope.launch {
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                // Load attendance summary
                val summaryResult = SupabaseManager.getAttendanceSummary(staffId, currentMonth)
                if (summaryResult.isSuccess) {
                    val summary = summaryResult.getOrNull()
                    summary?.let { updateAttendanceSummary(it) }
                }

                // Load attendance history
                val historyResult = SupabaseManager.getAttendanceByStaffAndMonth(staffId, currentMonth)
                if (historyResult.isSuccess) {
                    val attendance = historyResult.getOrNull() ?: emptyList()
                    attendanceList.clear()
                    attendanceList.addAll(attendance)
                    attendanceAdapter.notifyDataSetChanged()
                }

                // Calculate and show salary
                calculateAndShowSalary(summaryResult.getOrNull())

                showLoading(false)
            } catch (e: Exception) {
                showError("Error loading attendance data: ${e.message}")
                showLoading(false)
            }
        }
    }

    private fun updateAttendanceSummary(summary: AttendanceSummary) {
        binding.apply {
            tvPresentDays.text = summary.present.toString()
            tvAbsentDays.text = summary.absent.toString()
            tvHalfDays.text = summary.halfDay.toString()

            // Update the count TextViews
            tvPresentCount.text = summary.present.toString()
            tvAbsentCount.text = summary.absent.toString()
            tvHalfDayCount.text = summary.halfDay.toString()
        }
    }

    private fun calculateAndShowSalary(summary: AttendanceSummary?) {
        currentStaff?.let { staff ->
            val totalSalary = if (summary != null) {
                when (staff.salaryType) {
                    SalaryType.DAILY -> {
                        val workingDays = summary.present + (summary.halfDay * 0.5)
                        staff.salaryAmount * workingDays
                    }
                    SalaryType.MONTHLY -> {
                        // For monthly salary, calculate based on working days ratio
                        val totalDaysInMonth = getCurrentMonthTotalDays()
                        val workingDays = summary.present + (summary.halfDay * 0.5)
                        val ratio = workingDays / totalDaysInMonth
                        staff.salaryAmount * ratio
                    }
                }
            } else {
                0.0
            }

            binding.tvTotalSalary.text = "₹${totalSalary.toInt()}"
        }
    }

    private fun getCurrentMonthTotalDays(): Double {
        val calendar = Calendar.getInstance()
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerViewAttendance.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        showLoading(false)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

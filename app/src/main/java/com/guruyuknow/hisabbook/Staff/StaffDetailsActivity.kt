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
import java.text.NumberFormat
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
            finish()
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

        // Enhanced FAB click handling
        binding.fabEditStaff.setOnClickListener {
            currentStaff?.let { staff ->
                // Navigate to edit staff activity
                // val intent = Intent(this, EditStaffActivity::class.java)
                // intent.putExtra("staff_id", staff.id)
                // startActivity(intent)
                Toast.makeText(this, "Edit ${staff.name}", Toast.LENGTH_SHORT).show()
            }
        }

        showLoading(true)
    }

    private fun loadStaffData(staffId: String) {
        lifecycleScope.launch {
            try {
                val staffResult = SupabaseManager.getStaffById(staffId)
                if (staffResult.isSuccess) {
                    currentStaff = staffResult.getOrNull()
                    currentStaff?.let { staff ->
                        updateStaffUI(staff)
                        loadAttendanceData(staffId)
                    }
                } else {
                    showError("Failed to load staff details")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private fun updateStaffUI(staff: Staff) {
        binding.apply {
            // Update staff header information
            tvStaffNameLarge.text = staff.name
            tvStaffPhoneLarge.text = formatPhoneNumber(staff.phoneNumber)

            // Generate and set initials with better logic
            val initials = generateInitials(staff.name)
            tvStaffInitialsLarge.text = initials

            // Update salary information with better formatting
            tvSalaryType.text = when (staff.salaryType) {
                SalaryType.MONTHLY -> "Monthly Salary"
                SalaryType.DAILY -> "Daily Wage"
            }

            val formattedAmount = formatCurrency(staff.salaryAmount)
            tvSalaryAmount.text = formattedAmount

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
                    summary?.let {
                        updateAttendanceSummary(it)
                        calculateAndShowSalary(it)
                    }
                }

                // Load attendance history
                val historyResult = SupabaseManager.getAttendanceByStaffAndMonth(staffId, currentMonth)
                if (historyResult.isSuccess) {
                    val attendance = historyResult.getOrNull() ?: emptyList()
                    attendanceList.clear()
                    attendanceList.addAll(attendance)
                    attendanceAdapter.notifyDataSetChanged()
                }

                showLoading(false)
            } catch (e: Exception) {
                showError("Failed to load attendance data: ${e.message}")
                showLoading(false)
            }
        }
    }

    private fun updateAttendanceSummary(summary: AttendanceSummary) {
        binding.apply {
            // Update main summary cards
            tvPresentDays.text = summary.present.toString()
            tvAbsentDays.text = summary.absent.toString()
            tvHalfDays.text = summary.halfDay.toString()

            // Update the small count TextViews (if they exist in your layout)
            // tvPresentCount?.text = summary.present.toString()
            // tvAbsentCount?.text = summary.absent.toString()
            // tvHalfDayCount?.text = summary.halfDay.toString()
        }
    }

    private fun calculateAndShowSalary(summary: AttendanceSummary) {
        currentStaff?.let { staff ->
            val totalSalary = when (staff.salaryType) {
                SalaryType.DAILY -> {
                    val workingDays = summary.present + (summary.halfDay * 0.5)
                    staff.salaryAmount * workingDays
                }
                SalaryType.MONTHLY -> {
                    val totalDaysInMonth = getCurrentMonthTotalDays()
                    val workingDays = summary.present + (summary.halfDay * 0.5)
                    val ratio = workingDays / totalDaysInMonth
                    staff.salaryAmount * ratio
                }
            }

            binding.tvTotalSalary.text = formatCurrency(totalSalary)
        }
    }

    private fun getCurrentMonthTotalDays(): Double {
        val calendar = Calendar.getInstance()
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()
    }

    private fun generateInitials(name: String): String {
        return name.trim()
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "U" } // Fallback for empty names
    }

    private fun formatPhoneNumber(phone: String): String {
        return if (phone.length == 10) {
            "${phone.substring(0, 5)} ${phone.substring(5)}"
        } else {
            phone
        }
    }

    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return formatter.format(amount).replace("₹", "₹ ")
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerViewAttendance.visibility = if (show) View.GONE else View.VISIBLE

        // Optionally hide/show other content during loading
        if (show) {
            binding.fabEditStaff.hide()
        } else {
            binding.fabEditStaff.show()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        showLoading(false)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to the activity
        intent.getStringExtra("staff_id")?.let { staffId ->
            loadAttendanceData(staffId)
        }
    }

    // Helper function to get current month name
    private fun getCurrentMonthName(): String {
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    }

    // Function to handle month navigation (for future implementation)
    private fun navigateToMonth(direction: Int) {
        // Implementation for navigating to previous/next month
        // This can be implemented when you want to add month navigation
        Toast.makeText(this, "Month navigation coming soon", Toast.LENGTH_SHORT).show()
    }

    // Function to export attendance data (for future implementation)
    private fun exportAttendanceData() {
        currentStaff?.let { staff ->
            Toast.makeText(this, "Exporting ${staff.name}'s attendance data", Toast.LENGTH_SHORT).show()
            // Implementation for exporting data to PDF or Excel
        }
    }
}
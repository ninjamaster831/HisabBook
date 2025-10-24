package com.guruyuknow.hisabbook.Staff

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set status bar color to match toolbar
        window.statusBarColor = android.graphics.Color.parseColor("#50C9C3")

        binding = ActivityStaffDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val staffId = intent.getStringExtra("staff_id")
        if (staffId != null) {
            setupUI()
            applyWindowInsets()
            loadStaffData(staffId)
        } else {
            Toast.makeText(this, "Invalid staff ID", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = ""
        }

        // Initialize the attendance adapter
        attendanceAdapter = AttendanceAdapter(attendanceList)
        binding.recyclerViewAttendance.apply {
            layoutManager = LinearLayoutManager(this@StaffDetailsActivity)
            adapter = attendanceAdapter
            setHasFixedSize(true)

            // Add spacing
            addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: androidx.recyclerview.widget.RecyclerView,
                    state: androidx.recyclerview.widget.RecyclerView.State
                ) {
                    outRect.bottom = resources.getDimensionPixelSize(com.guruyuknow.hisabbook.R.dimen.spacing_small)
                }
            })
        }

        // FAB click handling
        binding.fabEditStaff.setOnClickListener {
            currentStaff?.let { staff ->
                Toast.makeText(this, "Edit ${staff.name}", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to edit staff activity
                // val intent = Intent(this, EditStaffActivity::class.java)
                // intent.putExtra("staff_id", staff.id)
                // startActivity(intent)
            }
        }

        showLoading(true)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            val navigationBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            )

            // Apply top padding to AppBar for status bar
            binding.appBar.setPadding(
                0,
                systemBars.top,
                0,
                0
            )

            // Apply bottom padding to FAB for navigation bar
            val fabParams = binding.fabEditStaff.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            fabParams.bottomMargin = 20.dpToPx() + navigationBars.bottom
            fabParams.rightMargin = 20.dpToPx()
            binding.fabEditStaff.layoutParams = fabParams

            insets
        }
    }

    // Extension function to convert dp to px
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
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
                    } ?: run {
                        showError("Staff data not found")
                    }
                } else {
                    showError("Failed to load staff details: ${staffResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showError("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun updateStaffUI(staff: Staff) {
        binding.apply {
            // Update staff header information
            tvStaffNameLarge.text = staff.name
            tvStaffPhoneLarge.text = formatPhoneNumber(staff.phoneNumber)

            // Generate and set initials
            val initials = generateInitials(staff.name)
            tvStaffInitialsLarge.text = initials

            // Update salary information
            tvSalaryType.text = when (staff.salaryType) {
                SalaryType.MONTHLY -> "Monthly Salary"
                SalaryType.DAILY -> "Daily Wage"
            }

            val formattedAmount = formatCurrency(staff.salaryAmount)
            tvSalaryAmount.text = formattedAmount

            // Update toolbar title
            supportActionBar?.title = staff.name

            // Set content description for accessibility
            tvStaffInitialsLarge.contentDescription = "Profile picture for ${staff.name}"
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
                } else {
                    updateAttendanceSummary(AttendanceSummary(0, 0, 0))
                }

                // Load attendance history
                val historyResult = SupabaseManager.getAttendanceByStaffAndMonth(staffId, currentMonth)
                if (historyResult.isSuccess) {
                    val attendance = historyResult.getOrNull() ?: emptyList()
                    attendanceList.clear()
                    attendanceList.addAll(attendance.sortedByDescending { it.date })
                    attendanceAdapter.notifyDataSetChanged()

                    // Show empty state if no attendance
                    binding.tvNoAttendance.visibility =
                        if (attendance.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    binding.tvNoAttendance.visibility = View.VISIBLE
                }

                showLoading(false)

            } catch (e: Exception) {
                showError("Failed to load attendance data: ${e.localizedMessage}")
                showLoading(false)
            }
        }
    }

    private fun updateAttendanceSummary(summary: AttendanceSummary) {
        binding.apply {
            tvPresentDays.text = summary.present.toString()
            tvAbsentDays.text = summary.absent.toString()
            tvHalfDays.text = summary.halfDay.toString()

            // Animate the update
            animateView(tvPresentDays)
            animateView(tvAbsentDays)
            animateView(tvHalfDays)
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
            animateView(binding.tvTotalSalary)

            // Update month label
            binding.tvCurrentMonth.text = getCurrentMonthName()
        }
    }

    private fun getCurrentMonthTotalDays(): Double {
        val calendar = Calendar.getInstance()
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()
    }

    private fun getCurrentMonthName(): String {
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
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
        val cleaned = phone.replace(Regex("[^\\d]"), "")
        return when {
            cleaned.length == 10 -> "${cleaned.substring(0, 5)} ${cleaned.substring(5)}"
            cleaned.length > 10 -> "${cleaned.substring(0, cleaned.length - 10)} ${cleaned.substring(cleaned.length - 10, cleaned.length - 5)} ${cleaned.substring(cleaned.length - 5)}"
            else -> phone
        }
    }

    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return formatter.format(amount)
    }

    private fun animateView(view: View) {
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentScrollView.visibility = if (show) View.GONE else View.VISIBLE

        if (show) {
            binding.fabEditStaff.hide()
        } else {
            binding.fabEditStaff.show()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        showLoading(false)

        // Optionally show error state UI
        binding.errorLayout.visibility = View.VISIBLE
        binding.errorMessage.text = message
        binding.btnRetry.setOnClickListener {
            intent.getStringExtra("staff_id")?.let { staffId ->
                binding.errorLayout.visibility = View.GONE
                loadStaffData(staffId)
            }
        }
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
}
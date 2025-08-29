package com.guruyuknow.hisabbook.Staff


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.User
import com.guruyuknow.hisabbook.databinding.ActivityStaffBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StaffActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffBinding
    private lateinit var staffAdapter: StaffAdapter
    private val staffList = mutableListOf<Staff>()
    private var currentUser: User? = null

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openContactPicker()
        } else {
            Toast.makeText(this, "Contact permission is required to add staff", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Staff"
    }

    private fun setupRecyclerView() {
        staffAdapter = StaffAdapter(
            staffList = staffList,
            onStaffClick = { staff -> openStaffDetails(staff) },
            onAttendanceClick = { staff -> markAttendance(staff) },
            onPermissionClick = { staff -> openStaffPermissions(staff) }
        )

        binding.recyclerViewStaff.apply {
            layoutManager = LinearLayoutManager(this@StaffActivity)
            adapter = staffAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddStaff.setOnClickListener {
            checkContactPermissionAndProceed()
        }

        binding.btnAddStaff.setOnClickListener {
            checkContactPermissionAndProceed()
        }

        binding.chipAll.setOnClickListener { filterStaff("all") }
        binding.chipSalaryAdded.setOnClickListener { filterStaff("salary_added") }
        binding.chipPermissionGiven.setOnClickListener { filterStaff("permission_given") }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
                currentUser?.let { user ->
                    user.id?.let { loadStaffData(it) }
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStaffData(businessOwnerId: String) {
        lifecycleScope.launch {
            try {
                val result = SupabaseManager.getStaffByBusinessOwner(businessOwnerId)
                if (result.isSuccess) {
                    val staff = result.getOrNull() ?: emptyList()
                    staffList.clear()
                    staffList.addAll(staff)
                    staffAdapter.notifyDataSetChanged()

                    updateUI(staff)
                    calculateTotalDue(staff)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(this@StaffActivity, "Error loading staff: $error", Toast.LENGTH_SHORT).show()
                    println("Staff loading error: $error")  // Add logging
                    result.exceptionOrNull()?.printStackTrace()  // Print stack trace
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                println("Staff loading exception: ${e.message}")  // Add logging
                e.printStackTrace()  // Print stack trace
            }
        }
    }

    private fun updateUI(staff: List<Staff>) {
        if (staff.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutContent.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.layoutContent.visibility = View.VISIBLE

            // Update attendance summary for today
            updateTodayAttendanceSummary()
        }
    }

    private fun updateTodayAttendanceSummary() {
        lifecycleScope.launch {
            try {
                currentUser?.let { user ->
                    val result = user.id?.let { SupabaseManager.getTodayAttendance(it) }
                    if (result != null) {
                        if (result.isSuccess) {
                            val todayAttendance = result?.getOrNull() ?: emptyList()
                            val presentCount = todayAttendance.count { it.status == AttendanceStatus.PRESENT }
                            val absentCount = todayAttendance.count { it.status == AttendanceStatus.ABSENT }
                            val halfDayCount = todayAttendance.count { it.status == AttendanceStatus.HALF_DAY }

                            binding.tvPresentCount.text = presentCount.toString()
                            binding.tvAbsentCount.text = absentCount.toString()
                            binding.tvHalfDayCount.text = halfDayCount.toString()
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    private fun calculateTotalDue(staff: List<Staff>) {
        lifecycleScope.launch {
            try {
                var totalDue = 0.0
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                staff.forEach { staffMember ->
                    when (staffMember.salaryType) {
                        SalaryType.MONTHLY -> totalDue += staffMember.salaryAmount
                        SalaryType.DAILY -> {
                            val attendanceResult = SupabaseManager.getAttendanceByStaffAndMonth(
                                staffMember.id, currentMonth
                            )
                            if (attendanceResult.isSuccess) {
                                val attendance = attendanceResult.getOrNull() ?: emptyList()
                                val workingDays = attendance.count {
                                    it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                                } + (attendance.count { it.status == AttendanceStatus.HALF_DAY } * 0.5)
                                totalDue += staffMember.salaryAmount * workingDays
                            }
                        }
                    }
                }

                binding.tvTotalDue.text = "₹${totalDue.toInt()}"
                binding.tvStaffCount.text = "for ${staff.size} staff"
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun checkContactPermissionAndProceed() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                openContactPicker()
            }
            else -> {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun openContactPicker() {
        val intent = Intent(this, ContactPickerActivity::class.java)
        startActivity(intent)
    }

    private fun openStaffDetails(staff: Staff) {
        val intent = Intent(this, StaffDetailsActivity::class.java)
        intent.putExtra("staff_id", staff.id)
        startActivity(intent)
    }

    private fun markAttendance(staff: Staff) {
        // Show attendance marking dialog
        AttendanceDialogFragment.newInstance(staff.id, staff.name)
            .show(supportFragmentManager, "attendance_dialog")
    }

    private fun openStaffPermissions(staff: Staff) {
        val intent = Intent(this, StaffPermissionsActivity::class.java)
        intent.putExtra("staff_id", staff.id)
        startActivity(intent)
    }

    private fun filterStaff(filter: String) {
        // Implement filtering logic based on the filter type
        when (filter) {
            "all" -> {
                binding.chipAll.isChecked = true
                binding.chipSalaryAdded.isChecked = false
                binding.chipPermissionGiven.isChecked = false
            }
            "salary_added" -> {
                binding.chipAll.isChecked = false
                binding.chipSalaryAdded.isChecked = true
                binding.chipPermissionGiven.isChecked = false
            }
            "permission_given" -> {
                binding.chipAll.isChecked = false
                binding.chipSalaryAdded.isChecked = false
                binding.chipPermissionGiven.isChecked = true
            }
        }
        // Apply filter to adapter
        staffAdapter.filter(filter)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from other activities
        currentUser?.let { user ->
            user.id?.let { loadStaffData(it) }
        }
    }
}
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
    private var currentFilter = "all"

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openContactPicker()
        } else {
            Toast.makeText(this, "Contact permission is required to add staff from contacts", Toast.LENGTH_LONG).show()
            // Allow manual entry without contacts
            openManualStaffEntry()
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

        binding.chipAll.setOnClickListener {
            if (!binding.chipAll.isChecked) {
                filterStaff("all")
            }
        }

        binding.chipSalaryAdded.setOnClickListener {
            if (!binding.chipSalaryAdded.isChecked) {
                filterStaff("salary_added")
            }
        }

        binding.chipPermissionGiven.setOnClickListener {
            if (!binding.chipPermissionGiven.isChecked) {
                filterStaff("permission_given")
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
                currentUser?.let { user ->
                    user.id?.let { loadStaffData(it) }
                } ?: run {
                    Toast.makeText(this@StaffActivity, "Please log in again", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffActivity, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshData() {
        currentUser?.id?.let { loadStaffData(it) }
    }

    private fun loadStaffData(businessOwnerId: String) {
        lifecycleScope.launch {
            try {
                binding.swipeRefreshLayout.isRefreshing = true
                binding.progressBar.visibility = View.VISIBLE

                val result = SupabaseManager.getStaffByBusinessOwner(businessOwnerId)
                if (result.isSuccess) {
                    val staff = result.getOrNull() ?: emptyList()
                    staffList.clear()
                    staffList.addAll(staff)
                    staffAdapter.updateList(staff)

                    updateUI(staff)
                    calculateTotalDue(staff)

                    // Apply current filter after loading data
                    filterStaff(currentFilter)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(this@StaffActivity, "Error loading staff: $error", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun updateUI(staff: List<Staff>) {
        if (staff.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutContent.visibility = View.GONE
            binding.swipeRefreshLayout.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.layoutContent.visibility = View.VISIBLE
            binding.swipeRefreshLayout.visibility = View.VISIBLE

            // Update attendance summary for today
            updateTodayAttendanceSummary()
        }
    }

    private fun updateTodayAttendanceSummary() {
        lifecycleScope.launch {
            try {
                currentUser?.let { user ->
                    user.id?.let { userId ->
                        val result = SupabaseManager.getTodayAttendance(userId)
                        if (result.isSuccess) {
                            val todayAttendance = result.getOrNull() ?: emptyList()
                            val presentCount = todayAttendance.count {
                                it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                            }
                            val absentCount = todayAttendance.count { it.status == AttendanceStatus.ABSENT }
                            val halfDayCount = todayAttendance.count { it.status == AttendanceStatus.HALF_DAY }

                            binding.tvPresentCount.text = presentCount.toString()
                            binding.tvAbsentCount.text = absentCount.toString()
                            binding.tvHalfDayCount.text = halfDayCount.toString()
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error silently or show subtle message
            }
        }
    }

    private fun calculateTotalDue(staff: List<Staff>) {
        lifecycleScope.launch {
            try {
                var totalDue = 0.0
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                for (staffMember in staff) {
                    when (staffMember.salaryType) {
                        SalaryType.MONTHLY -> {
                            totalDue += staffMember.salaryAmount
                        }
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
                // Handle error silently
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
        startActivityForResult(intent, REQUEST_CODE_ADD_STAFF)
    }

    private fun openManualStaffEntry() {
        val intent = Intent(this, StaffConfigurationActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_ADD_STAFF)
    }

    private fun openStaffDetails(staff: Staff) {
        val intent = Intent(this, StaffDetailsActivity::class.java)
        intent.putExtra("staff_id", staff.id)
        startActivity(intent)
    }

    private fun markAttendance(staff: Staff) {
        // Show attendance marking dialog
        val dialog = AttendanceDialogFragment.newInstance(staff.id, staff.name)
        dialog.show(supportFragmentManager, "attendance_dialog")
    }

    private fun openStaffPermissions(staff: Staff) {
        val intent = Intent(this, StaffPermissionsActivity::class.java)
        intent.putExtra("staff_id", staff.id)
        startActivityForResult(intent, REQUEST_CODE_EDIT_STAFF)
    }

    private fun filterStaff(filter: String) {
        currentFilter = filter

        // Update chip states
        binding.chipAll.isChecked = (filter == "all")
        binding.chipSalaryAdded.isChecked = (filter == "salary_added")
        binding.chipPermissionGiven.isChecked = (filter == "permission_given")

        // Apply filter to adapter
        staffAdapter.filter(filter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && (requestCode == REQUEST_CODE_ADD_STAFF || requestCode == REQUEST_CODE_EDIT_STAFF)) {
            // Refresh data when returning from staff-related activities
            refreshData()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from other activities
        refreshData()
    }

    companion object {
        private const val REQUEST_CODE_ADD_STAFF = 100
        private const val REQUEST_CODE_EDIT_STAFF = 101
    }
}
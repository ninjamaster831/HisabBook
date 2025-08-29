package com.guruyuknow.hisabbook.Staff

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.databinding.ActivityStaffDetailsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            loadStaffDetails(staffId)
            loadAttendanceHistory(staffId)
        } else {
            finish()
        }
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

    private fun loadStaffDetails(staffId: String) {
        lifecycleScope.launch {
            try {
                // Load staff details from database
                // Implementation depends on your database setup
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@StaffDetailsActivity, "Error loading staff details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAttendanceHistory(staffId: String) {
        lifecycleScope.launch {
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val result = SupabaseManager.getAttendanceByStaffAndMonth(staffId, currentMonth)
                if (result.isSuccess) {
                    val attendance = result.getOrNull() ?: emptyList()
                    attendanceList.clear()
                    attendanceList.addAll(attendance)
                    attendanceAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffDetailsActivity, "Error loading attendance", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
package com.guruyuknow.hisabbook.Staff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import com.guruyuknow.hisabbook.databinding.ActivityStaffBinding
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class StaffActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffBinding
    private lateinit var staffAdapter: StaffAdapter
    private val staffList = mutableListOf<Staff>()
    private var businessOwnerId: String? = null

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openContactPicker()
        else Toast.makeText(this, "Contact permission is required to add staff", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge: we’ll consume system bars ourselves.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityStaffBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        setupDialogResultListener()
        applyWindowInsets() // <- important: fixes FAB cut & adds safe paddings

        resolveBusinessOwnerIdAndLoad()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Staff"

        // Keep title single line
        binding.toolbar.post {
            for (i in 0 until binding.toolbar.childCount) {
                (binding.toolbar.getChildAt(i) as? androidx.appcompat.widget.AppCompatTextView)?.apply {
                    isSingleLine = true
                    maxLines = 1
                    ellipsize = null
                }
            }
        }
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
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        val add = View.OnClickListener { checkContactPermissionAndProceed() }
        binding.fabAddStaff.setOnClickListener(add)
        binding.btnAddStaff.setOnClickListener(add)

        binding.chipAll.setOnClickListener { filterStaff("all") }
        binding.chipSalaryAdded.setOnClickListener { filterStaff("salary_added") }
        binding.chipPermissionGiven.setOnClickListener { filterStaff("permission_given") }
    }

    private fun setupDialogResultListener() {
        supportFragmentManager.setFragmentResultListener(
            AttendanceDialogFragment.RESULT_KEY,
            this
        ) { _, bundle ->
            if (bundle.getBoolean(AttendanceDialogFragment.RESULT_OK, false)) {
                businessOwnerId?.let { id ->
                    loadStaffData(id)
                }
            }
        }
    }

    /** Handles status bar & nav bar insets so content/FAB don’t get cut */
    private fun applyWindowInsets() {
        // Root: apply only left/right for nice edge-to-edge look.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            // AppBar padding for status bar (top)
            binding.appBar.setPadding(
                binding.appBar.paddingLeft,
                bars.top,
                binding.appBar.paddingRight,
                binding.appBar.paddingBottom
            )

            // Content lists get a safe bottom padding (so last rows aren’t hidden)
            val contentBottomPad = bars.bottom.coerceAtLeast(binding.recyclerViewStaff.paddingBottom)
            binding.recyclerViewStaff.updatePadding(bottom = contentBottomPad)
            binding.layoutContent.updatePadding(bottom = contentBottomPad)
            binding.layoutEmpty.updatePadding(bottom = contentBottomPad)

            // FloatingActionButton avoids 3-button nav bar overlap
            binding.fabAddStaff.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = (bottomMargin + bars.bottom)
            }

            // Loading overlay respects insets visually
            binding.loadingOverlay.updatePadding(bottom = bars.bottom, top = bars.top)

            insets
        }
    }

    private fun resolveBusinessOwnerIdAndLoad() {
        val authUser = SupabaseManager.client.auth.currentUserOrNull()
        businessOwnerId = authUser?.id
        if (businessOwnerId == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadStaffData(businessOwnerId!!)
    }

    private fun showLoading(show: Boolean) {
        // Dim content + block touches when loading
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.layoutContent.alpha = if (show) 0.5f else 1f
        binding.layoutContent.isEnabled = !show
    }

    private fun loadStaffData(businessOwnerId: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = SupabaseManager.getStaffByBusinessOwner(businessOwnerId)
                if (result.isSuccess) {
                    val staff = result.getOrNull().orEmpty()
                    staffList.clear()
                    staffList.addAll(staff)
                    staffAdapter.notifyDataSetChanged()

                    updateEmptyState(staff.isEmpty())
                    // Parallel: today summary + total due
                    updateTodayAttendanceSummary(businessOwnerId)
                    calculateTotalDue(staff)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(this@StaffActivity, "Error loading staff: $error", Toast.LENGTH_SHORT).show()
                    updateEmptyState(true)
                }
            } catch (e: Exception) {
                Toast.makeText(this@StaffActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                updateEmptyState(true)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.layoutContent.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            binding.tvPresentCount.text = "0"
            binding.tvAbsentCount.text = "0"
            binding.tvHalfDayCount.text = "0"
            binding.tvTotalDue.text = getCurrency(0.0)
            binding.tvStaffCount.text = getString(R.string.for_n_staff, 0)
        }
    }

    /** Present = PRESENT or LATE; Absent = ABSENT; Half Day = HALF_DAY */
    private fun updateTodayAttendanceSummary(ownerId: String) {
        lifecycleScope.launch {
            try {
                val todayResult = SupabaseManager.getTodayAttendance(ownerId)
                if (todayResult.isSuccess) {
                    val today = todayResult.getOrNull().orEmpty()
                    val present = today.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                    val absent = today.count { it.status == AttendanceStatus.ABSENT }
                    val half   = today.count { it.status == AttendanceStatus.HALF_DAY }

                    binding.tvPresentCount.text = present.toString()
                    binding.tvAbsentCount.text  = absent.toString()
                    binding.tvHalfDayCount.text = half.toString()
                } else {
                    binding.tvPresentCount.text = "0"
                    binding.tvAbsentCount.text = "0"
                    binding.tvHalfDayCount.text = "0"
                }
            } catch (_: Exception) {
                binding.tvPresentCount.text = "0"
                binding.tvAbsentCount.text = "0"
                binding.tvHalfDayCount.text = "0"
            }
        }
    }

    /** Total due for current month:
     *  - MONTHLY: salaryAmount
     *  - DAILY: salaryAmount * (Present+Late + 0.5*HalfDay)
     */
    private fun calculateTotalDue(staff: List<Staff>) {
        lifecycleScope.launch {
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val dailyJobs = staff.map { s ->
                    async {
                        when (s.salaryType) {
                            SalaryType.MONTHLY -> s.salaryAmount
                            SalaryType.DAILY -> {
                                val res = SupabaseManager.getAttendanceByStaffAndMonth(s.id, currentMonth)
                                if (res.isSuccess) {
                                    val attendance = res.getOrNull().orEmpty()
                                    val fullDays = attendance.count {
                                        it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                                    }
                                    val halfDays = attendance.count { it.status == AttendanceStatus.HALF_DAY }
                                    s.salaryAmount * (fullDays + (halfDays * 0.5))
                                } else 0.0
                            }
                        }
                    }
                }

                val totalDue = dailyJobs.awaitAll().sum()
                binding.tvTotalDue.text = getCurrency(totalDue)
                binding.tvStaffCount.text = getString(R.string.for_n_staff, staff.size)

            } catch (_: Exception) {
                binding.tvTotalDue.text = getCurrency(0.0)
                binding.tvStaffCount.text = getString(R.string.for_n_staff, staff.size)
            }
        }
    }

    private fun getCurrency(amount: Double): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return nf.format(amount)
    }

    private fun checkContactPermissionAndProceed() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED -> openContactPicker()
            else -> contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openContactPicker() {
        startActivity(Intent(this, ContactPickerActivity::class.java))
    }

    private fun openStaffDetails(staff: Staff) {
        val intent = Intent(this, StaffDetailsActivity::class.java)
        intent.putExtra("staff_id", staff.id)
        startActivity(intent)
    }

    // Prevent multiple dialogs: use tag guard
    private fun markAttendance(staff: Staff) {
        val tag = "attendance_dialog"
        if (supportFragmentManager.findFragmentByTag(tag) == null) {
            AttendanceDialogFragment.newInstance(staff.id, staff.name)
                .show(supportFragmentManager, tag)
        }
    }

    private fun openStaffPermissions(staff: Staff) {
        val intent = Intent(this, StaffPermissionsActivity::class.java)
        intent.putExtra("staff_id", staff.id)
        startActivity(intent)
    }

    private fun filterStaff(filter: String) {
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
        staffAdapter.filter(filter)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        val id = SupabaseManager.client.auth.currentUserOrNull()?.id
        if (id != null) {
            if (id != businessOwnerId) businessOwnerId = id
            loadStaffData(id)
        }
    }
}

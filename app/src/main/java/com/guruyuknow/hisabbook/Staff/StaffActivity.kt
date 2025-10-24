package com.guruyuknow.hisabbook.Staff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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

    // State management
    private sealed class UiState {
        object Loading : UiState()
        data class Success(val staff: List<Staff>) : UiState()
        data class Error(val message: String, val canRetry: Boolean = true) : UiState()
        object Empty : UiState()
    }

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openContactPicker()
        } else {
            Toast.makeText(
                this,
                "Contact permission is required to add staff from contacts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityStaffBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        setupDialogResultListener()
        setupSwipeRefresh()
        applyWindowInsets()

        resolveBusinessOwnerIdAndLoad()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Manage Staff"
        }

        binding.toolbar.post {
            for (i in 0 until binding.toolbar.childCount) {
                (binding.toolbar.getChildAt(i) as? androidx.appcompat.widget.AppCompatTextView)?.apply {
                    isSingleLine = true
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
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

            // Add item spacing
            addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: androidx.recyclerview.widget.RecyclerView,
                    state: androidx.recyclerview.widget.RecyclerView.State
                ) {
                    outRect.bottom = resources.getDimensionPixelSize(R.dimen.spacing_small)
                }
            })
        }
    }

    private fun setupClickListeners() {
        val addClickListener = View.OnClickListener { checkContactPermissionAndProceed() }
        binding.fabAddStaff.setOnClickListener(addClickListener)
        binding.btnAddStaff.setOnClickListener(addClickListener)

        // Filter chips
        binding.chipAll.setOnClickListener {
            filterStaff("all")
            updateChipSelection(binding.chipAll.id)
        }
        binding.chipSalaryAdded.setOnClickListener {
            filterStaff("salary_added")
            updateChipSelection(binding.chipSalaryAdded.id)
        }
        binding.chipPermissionGiven.setOnClickListener {
            filterStaff("permission_given")
            updateChipSelection(binding.chipPermissionGiven.id)
        }
    }

    private fun updateChipSelection(selectedId: Int) {
        binding.chipAll.isChecked = (selectedId == binding.chipAll.id)
        binding.chipSalaryAdded.isChecked = (selectedId == binding.chipSalaryAdded.id)
        binding.chipPermissionGiven.isChecked = (selectedId == binding.chipPermissionGiven.id)
    }

    private fun setupDialogResultListener() {
        supportFragmentManager.setFragmentResultListener(
            AttendanceDialogFragment.RESULT_KEY,
            this
        ) { _, bundle ->
            if (bundle.getBoolean(AttendanceDialogFragment.RESULT_OK, false)) {
                businessOwnerId?.let { id ->
                    loadStaffData(id)
                    Toast.makeText(this, "Attendance marked successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh?.apply {
            setColorSchemeResources(
                R.color.hisab_green,
                R.color.colorPrimary
            )

            setOnRefreshListener {
                businessOwnerId?.let { id ->
                    loadStaffData(id)
                }
                postDelayed({ isRefreshing = false }, 1000)
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            // AppBar padding for status bar
            binding.appBar.setPadding(
                binding.appBar.paddingLeft,
                bars.top,
                binding.appBar.paddingRight,
                binding.appBar.paddingBottom
            )

            // Content bottom padding
            val contentBottomPad = bars.bottom.coerceAtLeast(16)
            binding.recyclerViewStaff.updatePadding(bottom = contentBottomPad)
            binding.layoutContent.updatePadding(bottom = contentBottomPad)
            binding.layoutEmpty.updatePadding(bottom = contentBottomPad)

            // FAB margin
            binding.fabAddStaff.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                bottomMargin = (16 + bars.bottom)
            }

            // Loading overlay
            binding.loadingOverlay.updatePadding(bottom = bars.bottom, top = bars.top)

            insets
        }
    }

    private fun resolveBusinessOwnerIdAndLoad() {
        val authUser = SupabaseManager.client.auth.currentUserOrNull()
        businessOwnerId = authUser?.id

        if (businessOwnerId == null) {
            Toast.makeText(this, "Authentication required. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadStaffData(businessOwnerId!!)
    }
    private fun loadStaffData(businessOwnerId: String) {
        updateUiState(UiState.Loading)

        lifecycleScope.launch {
            try {
                val result = SupabaseManager.getStaffByBusinessOwner(businessOwnerId)

                if (result.isSuccess) {
                    val staff = result.getOrNull().orEmpty()

                    if (staff.isEmpty()) {
                        updateUiState(UiState.Empty)
                    } else {
                        staffList.clear()
                        staffList.addAll(staff)
                        staffAdapter.notifyDataSetChanged()
                        updateUiState(UiState.Success(staff))

                        // Load additional data in parallel
                        updateTodayAttendanceSummary(businessOwnerId)
                        calculateTotalDue(staff)
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = when {
                        error?.message?.contains("network", ignoreCase = true) == true ->
                            "No internet connection. Please check and try again."
                        error?.message?.contains("timeout", ignoreCase = true) == true ->
                            "Request timed out. Please try again."
                        else -> "Failed to load staff: ${error?.message ?: "Unknown error"}"
                    }
                    updateUiState(UiState.Error(errorMessage))
                }
            } catch (e: Exception) {
                Log.e("StaffActivity", "Error loading staff data", e)
                updateUiState(UiState.Error("Unexpected error: ${e.localizedMessage}"))
            }
        }
    }

    private fun updateUiState(state: UiState) {
        when (state) {
            is UiState.Loading -> {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutContent.visibility = View.GONE
                binding.swipeRefresh?.isEnabled = false
            }
            is UiState.Success -> {
                binding.loadingOverlay.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutContent.visibility = View.VISIBLE
                binding.swipeRefresh?.isEnabled = true

                // Animate content in
                binding.layoutContent.alpha = 0f
                binding.layoutContent.animate().alpha(1f).setDuration(300).start()
            }
            is UiState.Error -> {
                binding.loadingOverlay.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.layoutContent.visibility = View.GONE
                binding.swipeRefresh?.isEnabled = true

                binding.tvEmptyTitle.text = "Unable to Load Staff"
                binding.tvEmptyMessage.text = state.message
                binding.btnAddStaff.text = if (state.canRetry) "Retry" else "Add Staff"

                if (state.canRetry) {
                    binding.btnAddStaff.setOnClickListener {
                        businessOwnerId?.let { loadStaffData(it) }
                    }
                }
            }
            is UiState.Empty -> {
                binding.loadingOverlay.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.layoutContent.visibility = View.GONE
                binding.swipeRefresh?.isEnabled = true

                binding.tvEmptyTitle.text = "No Staff Members Yet"
                binding.tvEmptyMessage.text = "Add your first staff member to start tracking attendance and managing salaries"
                binding.btnAddStaff.text = "Add Staff"
                binding.btnAddStaff.setOnClickListener { checkContactPermissionAndProceed() }

                // Reset summary
                resetSummaryViews()
            }
        }
    }

    private fun resetSummaryViews() {
        binding.tvPresentCount.text = "0"
        binding.tvAbsentCount.text = "0"
        binding.tvHalfDayCount.text = "0"
        binding.tvTotalDue.text = formatCurrency(0.0)
        binding.tvStaffCount.text = "0 staff"
    }

    private fun updateTodayAttendanceSummary(ownerId: String) {
        lifecycleScope.launch {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val result = SupabaseManager.getTodayAttendance(ownerId)

                if (result.isSuccess) {
                    val attendance = result.getOrNull().orEmpty()

                    val present = attendance.count {
                        it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                    }
                    val absent = attendance.count { it.status == AttendanceStatus.ABSENT }
                    val halfDay = attendance.count { it.status == AttendanceStatus.HALF_DAY }

                    binding.tvPresentCount.text = present.toString()
                    binding.tvAbsentCount.text = absent.toString()
                    binding.tvHalfDayCount.text = halfDay.toString()

                    // Animate counter updates
                    animateCounter(binding.tvPresentCount)
                    animateCounter(binding.tvAbsentCount)
                    animateCounter(binding.tvHalfDayCount)
                } else {
                    binding.tvPresentCount.text = "0"
                    binding.tvAbsentCount.text = "0"
                    binding.tvHalfDayCount.text = "0"
                }
            } catch (e: Exception) {
                Log.e("StaffActivity", "Error loading attendance summary", e)
                binding.tvPresentCount.text = "0"
                binding.tvAbsentCount.text = "0"
                binding.tvHalfDayCount.text = "0"
            }
        }
    }

    private fun animateCounter(view: android.widget.TextView) {
        view.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
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

    private fun calculateTotalDue(staff: List<Staff>) {
        lifecycleScope.launch {
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                val salaryJobs = staff.map { s ->
                    async {
                        when (s.salaryType) {
                            SalaryType.MONTHLY -> s.salaryAmount
                            SalaryType.DAILY -> {
                                val result = SupabaseManager.getAttendanceByStaffAndMonth(s.id, currentMonth)
                                if (result.isSuccess) {
                                    val attendance = result.getOrNull().orEmpty()
                                    val fullDays = attendance.count {
                                        it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                                    }
                                    val halfDays = attendance.count { it.status == AttendanceStatus.HALF_DAY }
                                    s.salaryAmount * (fullDays + (halfDays * 0.5))
                                } else {
                                    0.0
                                }
                            }
                        }
                    }
                }

                val totalDue = salaryJobs.awaitAll().sum()
                binding.tvTotalDue.text = formatCurrency(totalDue)
                binding.tvStaffCount.text = "${staff.size} staff"

                // Animate
                animateCounter(binding.tvTotalDue)

            } catch (e: Exception) {
                Log.e("StaffActivity", "Error calculating total due", e)
                binding.tvTotalDue.text = formatCurrency(0.0)
                binding.tvStaffCount.text = "${staff.size} staff"
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return formatter.format(amount)
    }

    private fun checkContactPermissionAndProceed() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED -> {
                openContactPicker()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Contact Permission Needed")
                    .setMessage("This app needs access to your contacts to help you quickly add staff members.")
                    .setPositiveButton("Grant") { _, _ ->
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun openContactPicker() {
        startActivity(Intent(this, ContactPickerActivity::class.java))
    }

    private fun openStaffDetails(staff: Staff) {
        val intent = Intent(this, StaffDetailsActivity::class.java).apply {
            putExtra("staff_id", staff.id)
        }
        startActivity(intent)
    }

    private fun markAttendance(staff: Staff) {
        val tag = "attendance_dialog_${staff.id}"

        // Prevent duplicate dialogs
        if (supportFragmentManager.findFragmentByTag(tag) != null) {
            return
        }

        AttendanceDialogFragment.newInstance(staff.id, staff.name)
            .show(supportFragmentManager, tag)
    }

    private fun openStaffPermissions(staff: Staff) {
        val intent = Intent(this, StaffPermissionsActivity::class.java).apply {
            putExtra("staff_id", staff.id)
        }
        startActivity(intent)
    }

    private fun filterStaff(filter: String) {
        staffAdapter.filter(filter)

        // Update filter result text
        val count = staffAdapter.itemCount
        val filterName = when (filter) {
            "all" -> "All Staff"
            "salary_added" -> "With Salary"
            "permission_given" -> "With Permissions"
            else -> "All Staff"
        }

        // You can add a TextView to show filter results if needed
        Log.d("StaffActivity", "Filter applied: $filterName, Results: $count")
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        businessOwnerId = SupabaseManager.client.auth.currentUserOrNull()?.id
        businessOwnerId?.let { loadStaffData(it) }
    }
}
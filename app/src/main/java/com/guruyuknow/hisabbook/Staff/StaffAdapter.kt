package com.guruyuknow.hisabbook.Staff

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ItemStaffBinding
import java.text.NumberFormat
import java.util.*

class StaffAdapter(
    private val staffList: MutableList<Staff>,
    private val onStaffClick: (Staff) -> Unit,
    private val onAttendanceClick: (Staff) -> Unit,
    private val onPermissionClick: (Staff) -> Unit
) : RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {

    private var filteredList = staffList.toMutableList()
    private var lastPosition = -1

    inner class StaffViewHolder(private val binding: ItemStaffBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentListener: AdapterView.OnItemSelectedListener? = null

        fun bind(staff: Staff, position: Int) {
            Log.d("StaffAdapter", "Binding staff: ${staff.name}")

            // Clear previous listener
            binding.dropdownAttendance.onItemSelectedListener = null

            binding.apply {
                tvStaffName.text = staff.name
                tvSalaryType.text = when (staff.salaryType) {
                    SalaryType.MONTHLY -> "Monthly Salary"
                    SalaryType.DAILY -> "Daily Salary"
                }
                tvSalaryAmount.text = formatCurrency(staff.salaryAmount)

                // Set initials with better logic
                val initials = generateInitials(staff.name)
                tvStaffInitials.text = initials

                // Setup attendance dropdown
                setupAttendanceDropdown(staff)

                // Handle clicks with ripple feedback
                root.setOnClickListener {
                    Log.d("StaffAdapter", "Staff item clicked: ${staff.name}")
                    onStaffClick(staff)
                }

                btnAddPermission.setOnClickListener {
                    Log.d("StaffAdapter", "Permission button clicked for: ${staff.name}")
                    onPermissionClick(staff)
                }

                // Content descriptions for accessibility
                root.contentDescription = "Staff member ${staff.name}, " +
                        "Salary: ${formatCurrency(staff.salaryAmount)} per ${staff.salaryType.getDisplayName()}"
                btnAddPermission.contentDescription = "Manage permissions for ${staff.name}"
                dropdownAttendance.contentDescription = "Mark attendance for ${staff.name}"
            }
        }

        private fun setupAttendanceDropdown(staff: Staff) {
            val attendanceOptions = arrayOf("Mark Attendance", "Present", "Absent", "Half Day", "Late")
            val adapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_item,
                attendanceOptions
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            binding.dropdownAttendance.adapter = adapter
            binding.dropdownAttendance.setSelection(0)

            val listener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position > 0) {
                        Log.d("StaffAdapter", "Attendance option selected: ${attendanceOptions[position]} for ${staff.name}")
                        onAttendanceClick(staff)

                        // Reset to default after a short delay
                        binding.root.postDelayed({
                            try {
                                binding.dropdownAttendance.setSelection(0)
                            } catch (e: Exception) {
                                Log.e("StaffAdapter", "Error resetting dropdown", e)
                            }
                        }, 150)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Do nothing
                }
            }

            currentListener = listener
            binding.dropdownAttendance.onItemSelectedListener = listener
        }

        fun cleanup() {
            binding.dropdownAttendance.onItemSelectedListener = null
            currentListener = null
            binding.root.clearAnimation()
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

        private fun formatCurrency(amount: Double): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            return formatter.format(amount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val binding = ItemStaffBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StaffViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        holder.bind(filteredList[position], position)

        // Animate items on first load
        if (position > lastPosition) {
            animateItem(holder.itemView, position)
            lastPosition = position
        }
    }

    override fun getItemCount(): Int = filteredList.size

    override fun onViewRecycled(holder: StaffViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    override fun onViewDetachedFromWindow(holder: StaffViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.clearAnimation()
    }

    fun filter(filterType: String) {
        Log.d("StaffAdapter", "Applying filter: $filterType")

        val oldSize = filteredList.size
        filteredList.clear()

        when (filterType) {
            "all" -> filteredList.addAll(staffList)
            "salary_added" -> filteredList.addAll(staffList.filter { it.salaryAmount > 0 })
            "permission_given" -> filteredList.addAll(staffList.filter {
                it.hasAttendancePermission || it.hasSalaryPermission || it.hasBusinessPermission
            })
        }

        Log.d("StaffAdapter", "Filtered list size: ${filteredList.size}")

        // Smart update instead of full refresh
        if (oldSize == filteredList.size) {
            notifyItemRangeChanged(0, filteredList.size)
        } else {
            notifyDataSetChanged()
        }

        // Reset animation position for new filter
        lastPosition = -1
    }

    fun updateList(newList: List<Staff>) {
        Log.d("StaffAdapter", "Updating staff list with ${newList.size} items")
        staffList.clear()
        staffList.addAll(newList)
        filteredList.clear()
        filteredList.addAll(newList)
        lastPosition = -1
        notifyDataSetChanged()
    }

    private fun animateItem(view: View, position: Int) {
        view.alpha = 0f
        view.translationY = 50f

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay((position * 50L).coerceAtMost(300))
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
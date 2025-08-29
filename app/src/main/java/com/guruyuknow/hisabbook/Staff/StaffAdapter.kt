package com.guruyuknow.hisabbook.Staff

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.databinding.ItemStaffBinding

class StaffAdapter(
    private val staffList: MutableList<Staff>,
    private val onStaffClick: (Staff) -> Unit,
    private val onAttendanceClick: (Staff) -> Unit,
    private val onPermissionClick: (Staff) -> Unit
) : RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {

    private var filteredList = staffList.toMutableList()

    inner class StaffViewHolder(private val binding: ItemStaffBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(staff: Staff) {
            Log.d("StaffAdapter", "Binding staff: $staff")
            binding.apply {
                tvStaffName.text = staff.name
                tvSalaryType.text = when (staff.salaryType) {
                    SalaryType.MONTHLY -> "Monthly Salary"
                    SalaryType.DAILY -> "Daily Salary"
                }
                tvSalaryAmount.text = "₹${staff.salaryAmount.toInt()}"

                // Set initials
                val initials = staff.name.split(" ").take(2).joinToString("") {
                    it.first().toString().uppercase()
                }
                tvStaffInitials.text = initials

                // Handle clicks
                root.setOnClickListener {
                    Log.d("StaffAdapter", "Staff item clicked: ${staff.name}")
                    onStaffClick(staff)
                }
                btnAddPermission.setOnClickListener {
                    Log.d("StaffAdapter", "Permission button clicked for: ${staff.name}")
                    onPermissionClick(staff)
                }
                dropdownAttendance.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                        Log.d("StaffAdapter", "Attendance selected for: ${staff.name}, position: $position")
                        onAttendanceClick(staff)
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                        Log.d("StaffAdapter", "No attendance selected for: ${staff.name}")
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val binding = ItemStaffBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StaffViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount(): Int = filteredList.size

    fun filter(filterType: String) {
        Log.d("StaffAdapter", "Applying filter: $filterType")
        filteredList.clear()
        when (filterType) {
            "all" -> filteredList.addAll(staffList)
            "salary_added" -> filteredList.addAll(staffList.filter { it.salaryAmount > 0 })
            "permission_given" -> filteredList.addAll(staffList.filter {
                it.hasAttendancePermission || it.hasSalaryPermission || it.hasBusinessPermission
            })
        }
        Log.d("StaffAdapter", "Filtered list size: ${filteredList.size}")
        notifyDataSetChanged()
    }

    fun updateList(newList: List<Staff>) {
        Log.d("StaffAdapter", "Updating staff list with ${newList.size} items")
        staffList.clear()
        staffList.addAll(newList)
        filteredList.clear()
        filteredList.addAll(newList)
        notifyDataSetChanged()
    }
}
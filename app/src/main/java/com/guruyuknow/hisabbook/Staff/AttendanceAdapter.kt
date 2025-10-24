package com.guruyuknow.hisabbook.Staff

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ItemAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAdapter(
    private val attendanceList: List<Attendance>
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(private val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(attendance: Attendance) {
            Log.d("AttendanceAdapter", "Binding attendance: ${attendance.date} - ${attendance.status}")

            binding.apply {
                // Format date
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = inputFormat.parse(attendance.date)

                    if (date != null) {
                        // Format day (dd MMM)
                        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                        tvDate.text = outputFormat.format(date)

                        // Get day of week (Mon, Tue, etc.)
                        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                        tvDay.text = dayFormat.format(date)
                    } else {
                        tvDate.text = attendance.date
                        tvDay.text = ""
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceAdapter", "Error parsing date: ${attendance.date}", e)
                    tvDate.text = attendance.date
                    tvDay.text = ""
                }

                // Set status with appropriate styling
                when (attendance.status) {
                    AttendanceStatus.PRESENT -> {
                        tvStatus.text = "Present"
                        tvStatus.setTextColor(getColor(R.color.green))
                        ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        ivStatusIcon.setColorFilter(getColor(R.color.green))
                    }
                    AttendanceStatus.ABSENT -> {
                        tvStatus.text = "Absent"
                        tvStatus.setTextColor(getColor(R.color.red))
                        ivStatusIcon.setImageResource(R.drawable.ic_cancel)
                        ivStatusIcon.setColorFilter(getColor(R.color.red))
                    }
                    AttendanceStatus.HALF_DAY -> {
                        tvStatus.text = "Half Day"
                        tvStatus.setTextColor(getColor(R.color.orange))
                        ivStatusIcon.setImageResource(R.drawable.ic_access_time)
                        ivStatusIcon.setColorFilter(getColor(R.color.orange))
                    }
                    AttendanceStatus.LATE -> {
                        tvStatus.text = "Late"
                        tvStatus.setTextColor(getColor(R.color.orange))
                        ivStatusIcon.setImageResource(R.drawable.ic_schedule)
                        ivStatusIcon.setColorFilter(getColor(R.color.orange))
                    }
                }

                // Set check-in time if available
                if (!attendance.checkInTime.isNullOrEmpty()) {
                    try {
                        // Format time if it's in HH:mm:ss format
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val time = timeFormat.parse(attendance.checkInTime)

                        tvCheckInTime.text = if (time != null) {
                            "Check-in: ${displayFormat.format(time)}"
                        } else {
                            "Check-in: ${attendance.checkInTime}"
                        }
                        tvCheckInTime.visibility = android.view.View.VISIBLE
                    } catch (e: Exception) {
                        tvCheckInTime.text = "Check-in: ${attendance.checkInTime}"
                        tvCheckInTime.visibility = android.view.View.VISIBLE
                    }
                } else {
                    tvCheckInTime.visibility = android.view.View.GONE
                }

                // Set notes if available
                if (!attendance.notes.isNullOrEmpty()) {
                    tvNotes.text = attendance.notes
                    tvNotes.visibility = android.view.View.VISIBLE
                } else {
                    tvNotes.visibility = android.view.View.GONE
                }

                // Content description for accessibility
                root.contentDescription = buildContentDescription(attendance)
            }
        }

        private fun getColor(colorRes: Int): Int {
            return ContextCompat.getColor(binding.root.context, colorRes)
        }

        private fun buildContentDescription(attendance: Attendance): String {
            val statusText = when (attendance.status) {
                AttendanceStatus.PRESENT -> "Present"
                AttendanceStatus.ABSENT -> "Absent"
                AttendanceStatus.HALF_DAY -> "Half Day"
                AttendanceStatus.LATE -> "Late"
            }

            return "Attendance on ${attendance.date}: $statusText" +
                    if (attendance.checkInTime != null) " at ${attendance.checkInTime}" else ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendanceList[position])
    }

    override fun getItemCount(): Int = attendanceList.size
}

// DiffUtil callback for efficient updates if you need to update the list
class AttendanceDiffCallback(
    private val oldList: List<Attendance>,
    private val newList: List<Attendance>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
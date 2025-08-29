package com.guruyuknow.hisabbook.Staff

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.databinding.ItemAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAdapter(
    private val attendanceList: List<Attendance>
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(private val binding: ItemAttendanceBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(attendance: Attendance) {
            Log.d("AttendanceAdapter", "Binding attendance: $attendance")
            binding.apply {
                // Format date
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                    val date = inputFormat.parse(attendance.date)
                    tvDate.text = outputFormat.format(date ?: Date())

                    // Get day of week
                    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                    tvDay.text = dayFormat.format(date ?: Date())
                } catch (e: Exception) {
                    Log.e("AttendanceAdapter", "Error parsing date: ${e.message}", e)
                    tvDate.text = attendance.date
                    tvDay.text = ""
                }

                // Set status
                when (attendance.status) {
                    AttendanceStatus.PRESENT -> {
                        tvStatus.text = "Present"
                        tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.green))
                        ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.green))
                    }
                    AttendanceStatus.ABSENT -> {
                        tvStatus.text = "Absent"
                        tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.red))
                        ivStatusIcon.setImageResource(R.drawable.ic_cancel)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.red))
                    }
                    AttendanceStatus.HALF_DAY -> {
                        tvStatus.text = "Half Day"
                        tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.orange))
                        ivStatusIcon.setImageResource(R.drawable.ic_access_time)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.orange))
                    }
                    AttendanceStatus.LATE -> {
                        tvStatus.text = "Late"
                        tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.orange))
                        ivStatusIcon.setImageResource(R.drawable.ic_schedule)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.orange))
                    }
                }

                // Set check-in time
                if (attendance.checkInTime != null) {
                    tvCheckInTime.text = "Check-in: ${attendance.checkInTime}"
                } else {
                    tvCheckInTime.text = ""
                }

                // Set notes if available
                if (!attendance.notes.isNullOrEmpty()) {
                    tvNotes.text = attendance.notes
                    tvNotes.visibility = android.view.View.VISIBLE
                } else {
                    tvNotes.visibility = android.view.View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendanceList[position])
    }

    override fun getItemCount(): Int = attendanceList.size
}
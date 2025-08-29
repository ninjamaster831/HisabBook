package com.guruyuknow.hisabbook.Staff

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.guruyuknow.hisabbook.SupabaseManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceDialogFragment : DialogFragment() {

    private lateinit var staffId: String
    private lateinit var staffName: String

    companion object {
        fun newInstance(staffId: String, staffName: String): AttendanceDialogFragment {
            val fragment = AttendanceDialogFragment()
            val args = Bundle().apply {
                putString("staff_id", staffId)
                putString("staff_name", staffName)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            staffId = it.getString("staff_id", "")
            staffName = it.getString("staff_name", "")
        }
        Log.d("AttendanceDialog", "Dialog created for staffId: $staffId, staffName: $staffName")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = arrayOf("Present", "Absent", "Half Day", "Late")
        var selectedOption = 0

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mark Attendance for $staffName")
            .setSingleChoiceItems(options, selectedOption) { _, which ->
                selectedOption = which
                Log.d("AttendanceDialog", "Selected attendance option: ${options[which]}")
            }
            .setPositiveButton("Save") { _, _ ->
                val status = when (selectedOption) {
                    0 -> AttendanceStatus.PRESENT
                    1 -> AttendanceStatus.ABSENT
                    2 -> AttendanceStatus.HALF_DAY
                    3 -> AttendanceStatus.LATE
                    else -> AttendanceStatus.PRESENT
                }
                Log.d("AttendanceDialog", "Saving attendance with status: $status")
                markAttendance(status)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d("AttendanceDialog", "Attendance dialog cancelled")
            }
            .create()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun markAttendance(status: AttendanceStatus) {
        lifecycleScope.launch {
            try {
                Log.d("AttendanceDialog", "Starting markAttendance for staffId: $staffId")
                val currentUser = SupabaseManager.getCurrentUser()
                Log.d("AttendanceDialog", "Current user: $currentUser")
                if (currentUser != null) {
                    if (staffId.isEmpty()) {
                        Log.e("AttendanceDialog", "Invalid staffId: $staffId")
                        Toast.makeText(requireContext(), "Error: Invalid staff ID", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                    val attendance = currentUser.id?.let {
                        Attendance(
                            staffId = staffId,
                            businessOwnerId = it,
                            date = today,
                            status = status,
                            checkInTime = if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) currentTime else null
                        )
                    }
                    Log.d("AttendanceDialog", "Attendance object created: $attendance")

                    if (attendance != null) {
                        val result = SupabaseManager.markAttendance(attendance)
                        if (result.isSuccess) {
                            Log.d("AttendanceDialog", "Attendance marked successfully: ${result.getOrNull()}")
                            Toast.makeText(requireContext(), "Attendance marked successfully", Toast.LENGTH_SHORT).show()
                            // Refresh parent activity
                            requireActivity().recreate()
                        } else {
                            val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                            Log.e("AttendanceDialog", "Error marking attendance: $errorMessage", result.exceptionOrNull())
                            Toast.makeText(requireContext(), "Error marking attendance: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("AttendanceDialog", "Attendance object is null, likely due to missing user ID")
                        Toast.makeText(requireContext(), "Error: Unable to create attendance record", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("AttendanceDialog", "No current user found")
                    Toast.makeText(requireContext(), "Error: No user logged in", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("AttendanceDialog", "Exception marking attendance: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
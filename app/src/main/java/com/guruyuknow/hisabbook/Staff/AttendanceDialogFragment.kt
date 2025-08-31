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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            .setMessage("Today: ${SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())}")
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
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d("AttendanceDialog", "Attendance dialog cancelled")
                dialog.dismiss()
            }
            .create()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun markAttendance(status: AttendanceStatus) {
        // Use fragment's lifecycleScope (not viewLifecycleOwner) because this is called from onCreateDialog()
        lifecycleScope.launch {
            // Capture context and simple UI state BEFORE any suspension
            val dialogContext = context ?: run {
                Log.e("AttendanceDialog", "Fragment not attached - aborting markAttendance")
                return@launch
            }

            try {
                Log.d("AttendanceDialog", "Starting markAttendance for staffId: $staffId")

                // Fetch current user on IO dispatcher
                val currentUser = withContext(Dispatchers.IO) {
                    try {
                        SupabaseManager.getCurrentUser()
                    } catch (ex: Exception) {
                        Log.e("AttendanceDialog", "Error getting current user (IO): ${ex.message}", ex)
                        null
                    }
                }

                // If fragment got detached while awaiting, bail out
                if (!isAdded) {
                    Log.w("AttendanceDialog", "Fragment detached after getCurrentUser - aborting")
                    return@launch
                }

                Log.d("AttendanceDialog", "Current user: $currentUser")

                if (currentUser?.id == null) {
                    Log.e("AttendanceDialog", "No current user found or user ID is null")
                    Toast.makeText(dialogContext, "Error: No user logged in", Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (staffId.isEmpty()) {
                    Log.e("AttendanceDialog", "Invalid staffId: $staffId")
                    Toast.makeText(dialogContext, "Error: Invalid staff ID", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                val attendance = Attendance(
                    staffId = staffId,
                    businessOwnerId = currentUser.id,
                    date = today,
                    status = status,
                    checkInTime = if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) currentTime else null,
                    notes = when (status) {
                        AttendanceStatus.LATE -> "Marked as late"
                        AttendanceStatus.HALF_DAY -> "Half day attendance"
                        AttendanceStatus.ABSENT -> "Marked absent"
                        else -> null
                    }
                )

                Log.d("AttendanceDialog", "Attendance object created: $attendance")

                // perform markAttendance on IO
                val result = withContext(Dispatchers.IO) {
                    try {
                        SupabaseManager.markAttendance(attendance)
                    } catch (ex: Exception) {
                        Log.e("AttendanceDialog", "markAttendance IO error: ${ex.message}", ex)
                        Result.failure<Attendance>(ex)
                    }
                }

                // check again fragment attachment before UI updates
                if (!isAdded) {
                    Log.w("AttendanceDialog", "Fragment detached after markAttendance - aborting UI updates")
                    return@launch
                }

                if (result.isSuccess) {
                    val message = when (status) {
                        AttendanceStatus.PRESENT -> "Marked $staffName as Present"
                        AttendanceStatus.ABSENT -> "Marked $staffName as Absent"
                        AttendanceStatus.HALF_DAY -> "Marked $staffName as Half Day"
                        AttendanceStatus.LATE -> "Marked $staffName as Late"
                    }

                    Toast.makeText(dialogContext, message, Toast.LENGTH_SHORT).show()

                    // Safe notify parent activity
                    (activity as? StaffActivity)?.let { activitySafe ->
                        if (isAdded) activitySafe.refreshData()
                    }

                    if (isAdded) dismiss()
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e("AttendanceDialog", "Error marking attendance: $errorMessage", result.exceptionOrNull())
                    Toast.makeText(dialogContext, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled by lifecycle — swallow quietly
                Log.w("AttendanceDialog", "markAttendance cancelled")
            } catch (e: Exception) {
                Log.e("AttendanceDialog", "Exception marking attendance: ${e.message}", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


}
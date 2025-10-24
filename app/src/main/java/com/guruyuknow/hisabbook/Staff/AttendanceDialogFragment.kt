package com.guruyuknow.hisabbook.Staff

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceDialogFragment : DialogFragment() {

    private lateinit var staffId: String
    private lateinit var staffName: String
    private var isSaving = false

    companion object {
        const val RESULT_KEY = "attendance_result"
        const val RESULT_OK = "ok"

        fun newInstance(staffId: String, staffName: String) = AttendanceDialogFragment().apply {
            arguments = Bundle().apply {
                putString("staff_id", staffId)
                putString("staff_name", staffName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            staffId = it.getString("staff_id", "")
            staffName = it.getString("staff_name", "")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = arrayOf("Present", "Absent", "Half Day", "Late")
        var selected = 0

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mark Attendance for $staffName")
            .setSingleChoiceItems(options, selected) { _, which -> selected = which }
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val alertDialog = dialog as AlertDialog
            val btnSave = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancel = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Create progress indicator
            val progressBar = ProgressBar(requireContext()).apply {
                visibility = View.GONE
                setPadding(32, 24, 32, 24)
            }

            // Add progress bar to dialog
            try {
                val parentLayout = alertDialog.findViewById<View>(android.R.id.content)?.parent as? android.view.ViewGroup
                parentLayout?.addView(progressBar)
            } catch (e: Exception) {
                // Fallback: just show toast
            }

            btnSave.setOnClickListener {
                if (isSaving) return@setOnClickListener

                val status = when (selected) {
                    0 -> AttendanceStatus.PRESENT
                    1 -> AttendanceStatus.ABSENT
                    2 -> AttendanceStatus.HALF_DAY
                    3 -> AttendanceStatus.LATE
                    else -> AttendanceStatus.PRESENT
                }

                saveAttendance(status, btnSave, btnCancel, progressBar)
            }
        }

        return dialog
    }

    private fun saveAttendance(
        status: AttendanceStatus,
        btnSave: Button,
        btnCancel: Button,
        progressBar: ProgressBar
    ) {
        isSaving = true
        btnSave.isEnabled = false
        btnCancel.isEnabled = false
        progressBar.visibility = View.VISIBLE
        isCancelable = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val authUser = SupabaseManager.client.auth.currentUserOrNull()
                        ?: throw IllegalStateException("No user logged in")

                    if (staffId.isEmpty()) {
                        throw IllegalArgumentException("Invalid staff ID")
                    }

                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                    val attendance = Attendance(
                        staffId = staffId,
                        businessOwnerId = authUser.id,
                        date = today,
                        status = status,
                        checkInTime = if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) now else null
                    )

                    SupabaseManager.markAttendance(attendance)
                }

                if (result.isSuccess) {
                    showToast("Attendance marked successfully")
                    parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                        putBoolean(RESULT_OK, true)
                    })
                    dismissAllowingStateLoss()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    showToast("Error: $errorMsg")
                    resetDialogState(btnSave, btnCancel, progressBar)
                }
            } catch (e: IllegalStateException) {
                showToast("Please login again")
                resetDialogState(btnSave, btnCancel, progressBar)
            } catch (e: IllegalArgumentException) {
                showToast("Invalid staff data")
                resetDialogState(btnSave, btnCancel, progressBar)
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "No internet connection"
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Request timed out"
                    else -> e.message ?: "Operation failed"
                }
                showToast("Error: $errorMsg")
                resetDialogState(btnSave, btnCancel, progressBar)
            }
        }
    }

    private fun resetDialogState(
        btnSave: Button,
        btnCancel: Button,
        progressBar: ProgressBar
    ) {
        isSaving = false
        btnSave.isEnabled = true
        btnCancel.isEnabled = true
        progressBar.visibility = View.GONE
        isCancelable = true
    }

    private fun showToast(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
}
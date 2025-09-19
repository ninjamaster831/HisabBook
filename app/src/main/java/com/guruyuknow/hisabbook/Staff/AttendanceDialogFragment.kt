package com.guruyuknow.hisabbook.Staff

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = arrayOf("Present", "Absent", "Half Day", "Late")
        var selected = 0

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mark Attendance for $staffName")
            .setSingleChoiceItems(options, selected) { _, which -> selected = which }
            .setPositiveButton("Save", null) // we’ll override to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .create()

        // Optional: avoid keyboard pushing dialog oddly
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        dialog.setOnShowListener {
            val btnSave = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Add a tiny inline "Saving..." text view (reuses dialog’s message slot)
            val savingView = TextView(requireContext()).apply {
                text = "Saving…"
                setPadding(32, 24, 32, 0)
                isVisible = false
            }
            dialog.setView(savingView)

            btnSave.setOnClickListener {
                // keep dialog open; disable buttons during save
                btnSave.isEnabled = false
                btnCancel.isEnabled = false
                savingView.isVisible = true
                isCancelable = false
                dialog.setCanceledOnTouchOutside(false)

                val status = when (selected) {
                    0 -> AttendanceStatus.PRESENT
                    1 -> AttendanceStatus.ABSENT
                    2 -> AttendanceStatus.HALF_DAY
                    3 -> AttendanceStatus.LATE
                    else -> AttendanceStatus.PRESENT
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val ui = { block: () -> Unit -> lifecycleScope.launch(Dispatchers.Main) { block() } }

                    try {
                        val authUser = SupabaseManager.client.auth.currentUserOrNull()
                        if (authUser == null) {
                            ui {
                                toast("Error: No user logged in")
                                resetButtons(btnSave, btnCancel, savingView)
                            }
                            return@launch
                        }
                        if (staffId.isEmpty()) {
                            ui {
                                toast("Error: Invalid staff ID")
                                resetButtons(btnSave, btnCancel, savingView)
                            }
                            return@launch
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

                        val result = SupabaseManager.markAttendance(attendance)
                        if (result.isSuccess) {
                            ui {
                                toast("Attendance marked")
                                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                                    putBoolean(RESULT_OK, true)
                                })
                                // now it’s safe to dismiss; job already completed
                                dismissAllowingStateLoss()
                            }
                        } else {
                            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                            ui {
                                toast("Error: $msg")
                                resetButtons(btnSave, btnCancel, savingView)
                            }
                        }
                    } catch (e: Exception) {
                        ui {
                            toast("Error: ${e.message ?: "Operation failed"}")
                            resetButtons(btnSave, btnCancel, savingView)
                        }
                    }
                }
            }
        }
        return dialog
    }

    private fun resetButtons(
        btnSave: android.widget.Button,
        btnCancel: android.widget.Button,
        savingView: TextView
    ) {
        btnSave.isEnabled = true
        btnCancel.isEnabled = true
        savingView.isVisible = false
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(true)
    }

    private fun toast(msg: String) {
        // avoid requireContext() after detach — we’re always on Main here
        context?.let { android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_SHORT).show() }
    }
}

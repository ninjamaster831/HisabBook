package com.guruyuknow.hisabbook

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.databinding.BottomSheetCreateEventBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreateEventBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateEventBinding? = null
    private val binding get() = _binding!!

    private var selectedStartDate: Long? = null
    private var selectedEndDate: Long? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDatePickers()

        binding.btnCreateEvent.setOnClickListener { createEvent() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun setupDatePickers() {
        val calendar = Calendar.getInstance()

        // Start date
        binding.etStartDate.setOnClickListener {
            val startCal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    startCal.set(year, month, day, 0, 0, 0)
                    startCal.set(Calendar.MILLISECOND, 0)
                    selectedStartDate = startCal.timeInMillis
                    binding.etStartDate.setText(dateFormat.format(Date(selectedStartDate!!)))

                    // If end date is set before start date, clear it
                    if (selectedEndDate != null && selectedEndDate!! < selectedStartDate!!) {
                        selectedEndDate = null
                        binding.etEndDate.text = null
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // (Optional) prevent selecting a past date
                // datePicker.minDate = System.currentTimeMillis()
            }.show()
        }

        // End date
        binding.etEndDate.setOnClickListener {
            val endCal = Calendar.getInstance()
            val dialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    endCal.set(year, month, day, 0, 0, 0)
                    endCal.set(Calendar.MILLISECOND, 0)
                    selectedEndDate = endCal.timeInMillis
                    binding.etEndDate.setText(dateFormat.format(Date(selectedEndDate!!)))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            // If a start date is chosen, enforce end >= start
            selectedStartDate?.let { start ->
                dialog.datePicker.minDate = start
            }

            dialog.show()
        }
    }

    private fun createEvent() {
        val name = binding.etEventName.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val location = binding.etLocation.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (name.isEmpty()) { binding.etEventName.error = "Event name is required"; hasError = true }
        if (selectedStartDate == null) {
            (activity?.applicationContext ?: context?.applicationContext)?.let {
                android.widget.Toast.makeText(it, "Please select start date", android.widget.Toast.LENGTH_SHORT).show()
            }
            hasError = true
        }
        if (selectedEndDate != null && selectedStartDate != null && selectedEndDate!! < selectedStartDate!!) {
            (activity?.applicationContext ?: context?.applicationContext)?.let {
                android.widget.Toast.makeText(it, "End date cannot be before start date", android.widget.Toast.LENGTH_SHORT).show()
            }
            hasError = true
        }
        if (hasError) return

        // loading on
        binding.btnCreateEvent.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val appCtx = activity?.applicationContext ?: context?.applicationContext
            fun toast(msg: String) { appCtx?.let { android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_SHORT).show() } }

            try {
                // Cashbook-style user fetch
                val user = SupabaseManager.getCurrentUser()
                if (user == null) { toast("Please login first"); return@launch }
                val userId = user.id ?: run { toast("User id not found"); return@launch }

                val event = Event(
                    name = name,
                    description = description.ifEmpty { null },
                    location = location.ifEmpty { null },
                    startDate = selectedStartDate!!,
                    endDate = selectedEndDate,
                    createdBy = userId
                )

                val eventId = SupabaseManager.createEvent(event)

                // Send result to host and dismiss
                setFragmentResult(
                    "event_created",
                    androidx.core.os.bundleOf("event_id" to eventId)
                )
                toast("Event created successfully!")
                dismiss()

            } catch (e: Exception) {
                toast("Failed to create event: ${e.message}")
            } finally {
                if (_binding != null && isAdded) {
                    binding.btnCreateEvent.isEnabled = true
                    binding.btnCancel.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }




    private fun toggleLoading(loading: Boolean) {
        binding.btnCreateEvent.isEnabled = !loading
        binding.btnCancel.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

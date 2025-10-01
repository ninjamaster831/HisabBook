package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.databinding.BottomSheetAddExpenseBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class AddExpenseBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddExpenseBinding? = null
    private val binding get() = _binding!!
    private lateinit var eventId: String
    private var submitJob: Job? = null

    companion object {
        private const val ARG_EVENT_ID = "event_id"

        fun newInstance(eventId: String) = AddExpenseBottomSheet().apply {
            arguments = Bundle().apply { putString(ARG_EVENT_ID, eventId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = arguments?.getString(ARG_EVENT_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategoryDropdown()
        setupFieldHelpers()

        binding.btnAddExpense.setOnClickListener { addExpense() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun setupFieldHelpers() {
        // Clear errors once user starts typing
        binding.etExpenseTitle.doAfterTextChanged { binding.etExpenseTitle.error = null }
        binding.etAmount.doAfterTextChanged { binding.etAmount.error = null }
    }

    private fun setupCategoryDropdown() {
        val categories = listOf(
            "Food & Dining",
            "Transportation",
            "Accommodation",
            "Activities",
            "Shopping",
            "Entertainment",
            "Health & Medical",
            "Other"
        )

        // Note: with TextInputLayout ExposedDropdownMenu you should set adapter on the AutoCompleteTextView
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories)
        binding.spinnerCategory.setAdapter(adapter)

        // Show dropdown on tap/focus for better UX
        binding.spinnerCategory.setOnClickListener { binding.spinnerCategory.showDropDown() }
        binding.spinnerCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.spinnerCategory.showDropDown()
        }

        // Default selection
        if (binding.spinnerCategory.text.isNullOrBlank()) {
            binding.spinnerCategory.setText(categories.first(), false)
        }
    }

    private fun addExpense() {
        val title = binding.etExpenseTitle.text?.toString()?.trim().orEmpty()
        val amountStr = binding.etAmount.text?.toString()?.trim().orEmpty()
        val category = binding.spinnerCategory.text?.toString()?.trim().orEmpty()
        val notes = binding.etNotes.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (title.isEmpty()) {
            binding.etExpenseTitle.error = "Title is required"
            hasError = true
        }
        if (amountStr.isEmpty()) {
            binding.etAmount.error = "Amount is required"
            hasError = true
        }
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            binding.etAmount.error = "Enter a valid amount"
            hasError = true
        }
        if (hasError) return

        // show loading
        binding.btnAddExpense.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Cashbook-style: fetch current user from Supabase
                val user = SupabaseManager.getCurrentUser()
                if (user == null) {
                    Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val userId = user.id ?: run {
                    Toast.makeText(context, "User id not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val expense = EventExpense(
                    eventId = eventId,
                    title = title,
                    amount = amount!!,
                    category = if (category.isEmpty()) "Other" else category,
                    notes = notes.ifEmpty { null },
                    paidBy = userId,
                    date = System.currentTimeMillis()
                )

                SupabaseManager.addEventExpense(expense)
                Toast.makeText(context, "Expense added successfully!", Toast.LENGTH_SHORT).show()
                if (isAdded) dismiss()

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to add expense: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null && isAdded) {
                    binding.btnAddExpense.isEnabled = true
                    binding.btnCancel.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }


    private fun toggleLoading(loading: Boolean) {
        binding.btnAddExpense.isEnabled = !loading
        binding.btnCancel.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.guruyuknow.hisabbook.databinding.BottomSheetEventDetailBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class EventDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEventDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var eventId: String
    private lateinit var expenseAdapter: EventExpenseAdapter

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    companion object {
        private const val ARG_EVENT_ID = "event_id"

        fun newInstance(eventId: String) = EventDetailBottomSheet().apply {
            arguments = Bundle().apply { putString(ARG_EVENT_ID, eventId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = arguments?.getString(ARG_EVENT_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (eventId.isBlank()) {
            Toast.makeText(context, "Invalid event. Please try again.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        setupRecyclerView()
        loadEventDetails()

        binding.fabAddExpense.setOnClickListener {
            AddExpenseBottomSheet.newInstance(eventId)
                .show(parentFragmentManager, "add_expense")
        }

        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        expenseAdapter = EventExpenseAdapter(
            onDeleteClick = { expense -> deleteExpense(expense) }
        )
        binding.rvExpenses.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = expenseAdapter
        }
    }

    private fun loadEventDetails() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE

        // Use viewLifecycleOwner.lifecycleScope for Fragment view safety
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val event = SupabaseManager.getEventById(eventId)
                val expenses = SupabaseManager.getEventExpenses(eventId)
                displayEventDetails(event, expenses)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load event: ${e.message}", Toast.LENGTH_SHORT).show()
                if (isAdded) dismiss()
            } finally {
                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun displayEventDetails(event: Event, expenses: List<EventExpense>) {
        binding.tvEventName.text = event.name

        binding.tvEventDate.text = buildString {
            append(dateFormat.format(Date(event.startDate)))
            event.endDate?.let { append(" - ${dateFormat.format(Date(it))}") }
        }

        // Location: show/hide the whole row, and set the text on the actual TextView
        if (event.location.isNullOrBlank()) {
            binding.rowLocation.visibility = View.GONE
        } else {
            binding.rowLocation.visibility = View.VISIBLE
            binding.tvLocation.text = event.location
        }

        // Description
        if (event.description.isNullOrBlank()) {
            binding.tvDescription.visibility = View.GONE
        } else {
            binding.tvDescription.visibility = View.VISIBLE
            binding.tvDescription.text = event.description
        }

        val totalAmount = expenses.sumOf { it.amount }
        binding.tvTotalAmount.text = currencyFormat.format(totalAmount)
        binding.tvExpenseCount.text = "${expenses.size} ${if (expenses.size == 1) "expense" else "expenses"}"

        expenseAdapter.submitList(expenses)

        if (expenses.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvExpenses.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvExpenses.visibility = View.VISIBLE
        }
    }

    private fun deleteExpense(expense: EventExpense) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SupabaseManager.deleteEventExpense(expense.id)
                Toast.makeText(context, "Expense deleted", Toast.LENGTH_SHORT).show()
                loadEventDetails()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when coming back from AddExpense sheet
        loadEventDetails()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

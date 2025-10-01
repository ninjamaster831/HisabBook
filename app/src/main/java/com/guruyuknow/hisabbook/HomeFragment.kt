package com.guruyuknow.hisabbook

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.guruyuknow.hisabbook.databinding.FragmentHomeBinding
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var recentTransactionsAdapter: RecentTransactionsAdapter
    private val homeDatabase = HomeDatabase()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private lateinit var eventsAdapter: EventsAdapter

    // For add-entry dialog (mirrors Cashbook)
    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDayFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    @Serializable
    private data class CashbookEntryInsert(
        @SerialName("user_id") val userId: String,
        val amount: Double,
        val type: String, // "IN" or "OUT"
        @SerialName("payment_method") val paymentMethod: String, // "CASH"/"ONLINE"
        val description: String? = null,
        val category: String? = null,
        val date: String // "yyyy-MM-dd"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadDashboardData()
        loadUserEvents()

        parentFragmentManager.setFragmentResultListener("event_created", viewLifecycleOwner) { _, bundle ->
            val eventId = bundle.getString("event_id") ?: return@setFragmentResultListener
            loadUserEvents()
            EventDetailBottomSheet.newInstance(eventId)
                .show(parentFragmentManager, "event_detail")
        }
    }

    private fun loadUserEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = SupabaseManager.getCurrentUser()
                if (user?.id == null) {
                    showErrorMessage("Please login to see your events")
                    if (::eventsAdapter.isInitialized) eventsAdapter.submitList(emptyList())
                    return@launch
                }
                val events = SupabaseManager.getUserEvents(user.id!!)
                if (::eventsAdapter.isInitialized) eventsAdapter.submitList(events)
            } catch (e: Exception) {
                showErrorMessage("Failed to load events: ${e.message}")
            }
        }
    }

    private fun setupUI() {
        setupGreeting()

        recentTransactionsAdapter = RecentTransactionsAdapter { transaction: Transaction ->
            navigateToTransactionDetails(transaction)
        }
        binding.recentTransactionsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentTransactionsAdapter
        }

        eventsAdapter = EventsAdapter { event ->
            EventDetailBottomSheet.newInstance(event.id)
                .show(parentFragmentManager, "event_detail")
        }
        binding.eventsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventsAdapter
        }

        setupClickListeners()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        _binding?.greetingText?.text = greeting
    }

    private fun setupClickListeners() {
        _binding?.apply {
            quickAddExpenseBtn.setOnClickListener { showAddCashbookEntryDialogFromHome("OUT", "Expense") }
            quickAddSaleBtn.setOnClickListener    { showAddCashbookEntryDialogFromHome("IN",  "Sale") }
            quickAddPurchaseBtn.setOnClickListener{ showAddCashbookEntryDialogFromHome("OUT", "Purchase") }

            cashFlowCard.setOnClickListener { navigateToCashbook() }
            salesCard.setOnClickListener { navigateToSales() }
            purchasesCard.setOnClickListener { navigateToPurchases() }
            staffCard.setOnClickListener { navigateToStaffManagement() }
            loansCard.setOnClickListener { navigateToLoans() }
            viewAllTransactionsBtn.setOnClickListener { navigateToAllTransactions() }
        }
    }

    // ---------- DIALOG (exact Cashbook feel) ----------
    private fun showAddCashbookEntryDialogFromHome(
        type: String,                 // "IN" or "OUT"
        prefillCategory: String? = null
    ) {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_entry, null)

        val amountInput      = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val categoryInput    = dialogView.findViewById<TextInputEditText>(R.id.categoryInput)
        val cashRadio        = dialogView.findViewById<MaterialButton>(R.id.cashRadio)
        val onlineRadio      = dialogView.findViewById<MaterialButton>(R.id.onlineRadio)
        val dateButton       = dialogView.findViewById<MaterialButton>(R.id.dateButton)

        prefillCategory?.let { categoryInput.setText(it) }

        var selectedPaymentMethod = "CASH"
        var selectedDate = isoDateFormatter.format(Date())

        dateButton.text = displayDayFormatter.format(Date())

        cashRadio.setOnClickListener {
            selectedPaymentMethod = "CASH"
            updatePaymentMethodButtonsFromHome(cashRadio, onlineRadio, true)
        }
        onlineRadio.setOnClickListener {
            selectedPaymentMethod = "ONLINE"
            updatePaymentMethodButtonsFromHome(cashRadio, onlineRadio, false)
        }

        dateButton.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                ctx,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDate = isoDateFormatter.format(cal.time)
                    dateButton.text = displayDayFormatter.format(cal.time)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(if (type == "IN") "Add Income" else "Add Expense")
            .setView(dialogView)
            .setPositiveButton("Save", null) // override for validation
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = amountInput.text?.toString()?.toDoubleOrNull()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                val category = categoryInput.text?.toString()?.trim().orEmpty()

                if (amount == null || amount <= 0) {
                    Toast.makeText(ctx, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val user = SupabaseManager.getCurrentUser()
                        val userId = user?.id
                        if (userId.isNullOrEmpty()) {
                            Toast.makeText(ctx, "Please login again", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val body = CashbookEntryInsert(
                            userId = userId,
                            amount = amount,
                            type = type,
                            paymentMethod = selectedPaymentMethod,
                            description = description.ifEmpty { null },
                            category = category.ifEmpty { null },
                            date = selectedDate
                        )

                        // insert using serializable DTO
                        SupabaseManager.client
                            .from("cashbook_entries")
                            .insert(body) { select() }

                        Toast.makeText(ctx, "Entry added successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadDashboardData() // refresh cards

                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Error adding entry: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun updatePaymentMethodButtonsFromHome(
        cashRadio: MaterialButton,
        onlineRadio: MaterialButton,
        isCashSelected: Boolean
    ) {
        if (isCashSelected) {
            cashRadio.setBackgroundColor(requireContext().getColor(R.color.colorPrimary))
            cashRadio.setTextColor(requireContext().getColor(android.R.color.white))
            onlineRadio.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            onlineRadio.setTextColor(requireContext().getColor(R.color.colorPrimary))
        } else {
            onlineRadio.setBackgroundColor(requireContext().getColor(R.color.colorPrimary))
            onlineRadio.setTextColor(requireContext().getColor(android.R.color.white))
            cashRadio.setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            cashRadio.setTextColor(requireContext().getColor(R.color.colorPrimary))
        }
    }

    private fun loadDashboardData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: return@launch
            try {
                b.progressBar.visibility = View.VISIBLE
                val currentUser = try {
                    SupabaseManager.getCurrentUser()
                } catch (_: CancellationException) {
                    return@launch
                }
                if (currentUser?.id == null) {
                    showErrorMessage("User not authenticated")
                    return@launch
                }
                val result = homeDatabase.loadDashboardData(currentUser.id)
                if (result.isSuccess) {
                    result.getOrNull()?.let { data -> updateUI(data) }
                        ?: showErrorMessage("No dashboard data available")
                } else {
                    showErrorMessage("Failed to load dashboard data: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showErrorMessage("Failed to load dashboard data: ${e.message}")
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun updateUI(data: DashboardData) {
        val b = _binding ?: return

        b.todayIncomeAmount.text = numberFormat.format(data.todayIncome)
        b.todayExpenseAmount.text = numberFormat.format(data.todayExpenses)
        val todayBalance = data.todayIncome - data.todayExpenses
        b.todayBalanceAmount.text = numberFormat.format(todayBalance)
        b.todayBalanceAmount.setTextColor(
            if (todayBalance >= 0)
                resources.getColor(R.color.success_green, null)
            else
                resources.getColor(R.color.error_red, null)
        )

        b.monthlyIncomeAmount.text = numberFormat.format(data.monthlyIncome)
        b.monthlyExpenseAmount.text = numberFormat.format(data.monthlyExpenses)
        val monthlyProfit = data.monthlyIncome - data.monthlyExpenses
        b.monthlyProfitAmount.text = numberFormat.format(monthlyProfit)
        b.monthlyProfitAmount.setTextColor(
            if (monthlyProfit >= 0)
                resources.getColor(R.color.success_green, null)
            else
                resources.getColor(R.color.error_red, null)
        )

        b.todaySalesAmount.text = numberFormat.format(data.todaySales)
        b.monthlySalesAmount.text = numberFormat.format(data.monthlySales)

        b.monthlyPurchasesAmount.text = numberFormat.format(data.monthlyPurchases)

        b.totalStaffCount.text = "${data.totalStaff} Active"
        b.monthlyStaffExpenseAmount.text = numberFormat.format(data.monthlyStaffExpenses)

        b.loansGivenAmount.text = numberFormat.format(data.totalLoansGiven)
        b.loansReceivedAmount.text = numberFormat.format(data.totalLoansReceived)
        b.pendingCollectionsAmount.text = numberFormat.format(data.pendingCollections)

        recentTransactionsAdapter.submitList(data.recentTransactions as List<Transaction?>?)

        val empty = data.recentTransactions.isEmpty()
        b.emptyStateLayout.visibility = if (empty) View.VISIBLE else View.GONE
        b.recentTransactionsRecycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ------- (stubs, keep as-is or wire later) -------
    private fun navigateToCashbook() {}
    private fun navigateToSales() {}
    private fun navigateToPurchases() {}
    private fun navigateToStaffManagement() {}
    private fun navigateToLoans() {}
    private fun navigateToAllTransactions() {}
    private fun navigateToTransactionDetails(transaction: Transaction) {}

    private fun showErrorMessage(message: String) {
        _binding?.root?.let { root ->
            Toast.makeText(root.context, message, Toast.LENGTH_LONG).show()
        } ?: run {
            if (isAdded) Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboardData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Keep this here so the adapter + DB agree on types
    data class DashboardData(
        val todayIncome: Double,
        val todayExpenses: Double,
        val monthlyIncome: Double,
        val monthlyExpenses: Double,
        val todaySales: Double,
        val monthlySales: Double,
        val monthlyPurchases: Double,
        val totalStaff: Int,
        val monthlyStaffExpenses: Double,
        val totalLoansGiven: Double,
        val totalLoansReceived: Double,
        val pendingCollections: Double,
        val recentTransactions: List<Transaction>
    )
}

package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.databinding.FragmentHomeBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
// import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!   // Don't use this inside coroutines; use a local snapshot

    private lateinit var recentTransactionsAdapter: RecentTransactionsAdapter
    private val homeDatabase = HomeDatabase()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

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
    }

    private fun setupUI() {
        // Setup greeting
        setupGreeting()

        // Setup RecyclerView for recent transactions
        recentTransactionsAdapter = RecentTransactionsAdapter { transaction: Transaction ->
            // Handle transaction item click
            navigateToTransactionDetails(transaction)
        }

        binding.recentTransactionsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentTransactionsAdapter
        }

        // Setup click listeners
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
            // Quick action buttons
            quickAddExpenseBtn.setOnClickListener { navigateToAddExpense() }
            quickAddSaleBtn.setOnClickListener { navigateToAddSale() }
            quickAddPurchaseBtn.setOnClickListener { navigateToAddPurchase() }

            // Card clicks for detailed views
            cashFlowCard.setOnClickListener { navigateToCashbook() }
            salesCard.setOnClickListener { navigateToSales() }
            purchasesCard.setOnClickListener { navigateToPurchases() }
            staffCard.setOnClickListener { navigateToStaffManagement() }
            loansCard.setOnClickListener { navigateToLoans() }
            viewAllTransactionsBtn.setOnClickListener { navigateToAllTransactions() }
        }
    }

    private fun loadDashboardData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // snapshot the binding; if view is gone, stop immediately
            val b = _binding ?: return@launch

            try {
                b.progressBar.visibility = View.VISIBLE

                val currentUser = try {
                    SupabaseManager.getCurrentUser()
                } catch (_: CancellationException) {
                    // Lifecycle moved on; quietly stop
                    return@launch
                }

                if (currentUser?.id == null) {
                    showErrorMessage("User not authenticated")
                    return@launch
                }

                val result = homeDatabase.loadDashboardData(currentUser.id)

                if (result.isSuccess) {
                    result.getOrNull()?.let { data ->
                        updateUI(data)
                    } ?: showErrorMessage("No dashboard data available")
                } else {
                    showErrorMessage("Failed to load dashboard data: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showErrorMessage("Failed to load dashboard data: ${e.message}")
            } finally {
                // re-check because the view may have been destroyed in the meantime
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun updateUI(data: DashboardData) {
        val b = _binding ?: return

        // Today's cash flow
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

        // Monthly overview
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

        // Sales (from purchases table as per your data)
        b.todaySalesAmount.text = numberFormat.format(data.todaySales)
        b.monthlySalesAmount.text = numberFormat.format(data.monthlySales)

        // Purchases
        b.monthlyPurchasesAmount.text = numberFormat.format(data.monthlyPurchases)

        // Staff
        b.totalStaffCount.text = "${data.totalStaff} Active"
        b.monthlyStaffExpenseAmount.text = numberFormat.format(data.monthlyStaffExpenses)

        // Loans
        b.loansGivenAmount.text = numberFormat.format(data.totalLoansGiven)
        b.loansReceivedAmount.text = numberFormat.format(data.totalLoansReceived)
        b.pendingCollectionsAmount.text = numberFormat.format(data.pendingCollections)

        // Recent transactions
        recentTransactionsAdapter.submitList(data.recentTransactions as List<Transaction?>?)

        // Empty state toggle
        val empty = data.recentTransactions.isEmpty()
        b.emptyStateLayout.visibility = if (empty) View.VISIBLE else View.GONE
        b.recentTransactionsRecycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ==================== NAVIGATION METHODS ====================

    private fun navigateToAddExpense() {
        // TODO: Implement navigation
    }

    private fun navigateToAddSale() {
        // TODO: Implement navigation
    }

    private fun navigateToAddPurchase() {
        // TODO: Implement navigation
    }

    private fun navigateToCashbook() {
        // TODO: Implement navigation
    }

    private fun navigateToSales() {
        // TODO: Implement navigation
    }

    private fun navigateToPurchases() {
        // TODO: Implement navigation
    }

    private fun navigateToStaffManagement() {
        // TODO: Implement navigation
    }

    private fun navigateToLoans() {
        // TODO: Implement navigation
    }

    private fun navigateToAllTransactions() {
        // TODO: Implement navigation
    }

    private fun navigateToTransactionDetails(transaction: Transaction) {
        // TODO: Implement navigation, pass `transaction`
    }

    private fun showErrorMessage(message: String) {
        _binding?.root?.let { root ->
            // Snackbar example:
            // Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
            Toast.makeText(root.context, message, Toast.LENGTH_LONG).show()
        } ?: run {
            if (isAdded) {
                Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Optional: comment this out if you don't want auto-refresh on return.
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

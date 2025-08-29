package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CashbookReportActivity : AppCompatActivity() {

    // UI Components
    private lateinit var backButton: ImageView
    private lateinit var filterButton: ImageView
    private lateinit var totalIncomeAmount: TextView
    private lateinit var totalExpenseAmount: TextView
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout

    // Chips
    private lateinit var chipAll: Chip
    private lateinit var chipToday: Chip
    private lateinit var chipWeek: Chip
    private lateinit var chipMonth: Chip
    private lateinit var chipIncome: Chip
    private lateinit var chipExpense: Chip

    // Data
    private val allEntries = mutableListOf<CashbookEntry>()
    private val filteredEntries = mutableListOf<CashbookEntry>()
    private lateinit var reportAdapter: CashbookReportAdapter
    private var currentUser: User? = null

    // Date formatters
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_cashbook_report)

        initViews()
        setupRecyclerView()
        setupClickListeners()
        setupChipListeners()
        loadCurrentUser()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        filterButton = findViewById(R.id.filterButton)
        totalIncomeAmount = findViewById(R.id.totalIncomeAmount)
        totalExpenseAmount = findViewById(R.id.totalExpenseAmount)
        filterChipGroup = findViewById(R.id.filterChipGroup)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        // Initialize chips
        chipAll = findViewById(R.id.chipAll)
        chipToday = findViewById(R.id.chipToday)
        chipWeek = findViewById(R.id.chipWeek)
        chipMonth = findViewById(R.id.chipMonth)
        chipIncome = findViewById(R.id.chipIncome)
        chipExpense = findViewById(R.id.chipExpense)
    }

    private fun setupRecyclerView() {
        reportAdapter = CashbookReportAdapter(filteredEntries) { entry ->
            // Handle entry click - could show details or edit
            Toast.makeText(this, "Entry: ${entry.description ?: "No description"}", Toast.LENGTH_SHORT).show()
        }
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = reportAdapter
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        filterButton.setOnClickListener {
            // Could implement advanced filtering options
            Toast.makeText(this, "Advanced filters coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupChipListeners() {
        filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipAll -> applyFilter(FilterType.ALL)
                    R.id.chipToday -> applyFilter(FilterType.TODAY)
                    R.id.chipWeek -> applyFilter(FilterType.THIS_WEEK)
                    R.id.chipMonth -> applyFilter(FilterType.THIS_MONTH)
                    R.id.chipIncome -> applyFilter(FilterType.INCOME_ONLY)
                    R.id.chipExpense -> applyFilter(FilterType.EXPENSE_ONLY)
                }
            }
        }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
                if (currentUser != null) {
                    loadAllTransactions()
                } else {
                    Toast.makeText(this@CashbookReportActivity, "Please login first", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CashbookReportActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun loadAllTransactions() {
        try {
            val userId = currentUser?.id ?: return

            val result = SupabaseManager.client
                .from("cashbook_entries")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<CashbookEntry>()

            allEntries.clear()
            allEntries.addAll(result)

            runOnUiThread {
                applyFilter(FilterType.ALL) // Apply default filter
                updateSummary()
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@CashbookReportActivity, "Error loading transactions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilter(filterType: FilterType) {
        filteredEntries.clear()

        val calendar = Calendar.getInstance()
        val today = dateFormatter.format(calendar.time)

        // Get start of week (Monday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val weekStart = dateFormatter.format(calendar.time)

        // Get start of month
        calendar.time = Date()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = dateFormatter.format(calendar.time)

        when (filterType) {
            FilterType.ALL -> {
                filteredEntries.addAll(allEntries)
            }
            FilterType.TODAY -> {
                filteredEntries.addAll(allEntries.filter { it.date == today })
            }
            FilterType.THIS_WEEK -> {
                filteredEntries.addAll(allEntries.filter { it.date >= weekStart })
            }
            FilterType.THIS_MONTH -> {
                filteredEntries.addAll(allEntries.filter { it.date >= monthStart })
            }
            FilterType.INCOME_ONLY -> {
                filteredEntries.addAll(allEntries.filter { it.type == "IN" })
            }
            FilterType.EXPENSE_ONLY -> {
                filteredEntries.addAll(allEntries.filter { it.type == "OUT" })
            }
        }

        reportAdapter.notifyDataSetChanged()
        updateEmptyState()
        updateSummary()
    }

    private fun updateSummary() {
        var totalIncome = 0.0
        var totalExpense = 0.0

        filteredEntries.forEach { entry ->
            if (entry.type == "IN") {
                totalIncome += entry.amount
            } else {
                totalExpense += entry.amount
            }
        }

        totalIncomeAmount.text = "₹ ${String.format("%.0f", totalIncome)}"
        totalExpenseAmount.text = "₹ ${String.format("%.0f", totalExpense)}"
    }

    private fun updateEmptyState() {
        if (filteredEntries.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            transactionsRecyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            transactionsRecyclerView.visibility = View.VISIBLE
        }
    }

    private enum class FilterType {
        ALL, TODAY, THIS_WEEK, THIS_MONTH, INCOME_ONLY, EXPENSE_ONLY
    }
}
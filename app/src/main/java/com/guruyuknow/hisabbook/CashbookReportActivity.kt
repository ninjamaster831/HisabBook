package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CashbookReportActivity : AppCompatActivity() {

    // UI Components
    private lateinit var backButton: ImageView
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

    // Date / Currency
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // Insets
    private var baseRecyclerBottomPadding = 0
    private var lastSystemBarBottomInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_cashbook_report)

        initViews()
        setupRecyclerView()
        setupClickListeners()
        setupChipListeners()
        applyWindowInsets()
        loadCurrentUser()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        totalIncomeAmount = findViewById(R.id.totalIncomeAmount)
        totalExpenseAmount = findViewById(R.id.totalExpenseAmount)
        filterChipGroup = findViewById(R.id.filterChipGroup)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        // Chips
        chipAll = findViewById(R.id.chipAll)
        chipToday = findViewById(R.id.chipToday)
        chipWeek = findViewById(R.id.chipWeek)
        chipMonth = findViewById(R.id.chipMonth)
        chipIncome = findViewById(R.id.chipIncome)
        chipExpense = findViewById(R.id.chipExpense)

        baseRecyclerBottomPadding = transactionsRecyclerView.paddingBottom
    }

    private fun setupRecyclerView() {
        reportAdapter = CashbookReportAdapter(filteredEntries) { entry ->
            // Handle entry click - could show details or edit
            Toast.makeText(
                this,
                "Entry: ${entry.description ?: "(no description)"}",
                Toast.LENGTH_SHORT
            ).show()
        }
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = reportAdapter
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

    }

    private fun setupChipListeners() {
        // Ensure single selection is respected and we react to the active chip id
        filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds.first()) {
                R.id.chipAll -> applyFilter(FilterType.ALL)
                R.id.chipToday -> applyFilter(FilterType.TODAY)
                R.id.chipWeek -> applyFilter(FilterType.THIS_WEEK)
                R.id.chipMonth -> applyFilter(FilterType.THIS_MONTH)
                R.id.chipIncome -> applyFilter(FilterType.INCOME_ONLY)
                R.id.chipExpense -> applyFilter(FilterType.EXPENSE_ONLY)
            }
        }

        // Ensure a default is selected (XML has checked="true", but we enforce)
        chipAll.isChecked = true
    }

    private fun applyWindowInsets() {
        val statusBarSpace = findViewById<View>(R.id.statusBarSpace)
        val content = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            lastSystemBarBottomInset = bars.bottom

            // Status bar spacer (uses at least 24dp)
            statusBarSpace?.let { spacer ->
                val min24 = resources.getDimensionPixelSize(R.dimen.spacing_24dp)
                spacer.layoutParams = spacer.layoutParams.apply {
                    height = maxOf(min24, bars.top)
                }
                spacer.requestLayout()
            }

            // Lift the list above the nav bar/gesture area
            transactionsRecyclerView.updatePadding(
                bottom = baseRecyclerBottomPadding + bars.bottom
            )

            insets
        }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                currentUser = SupabaseManager.getCurrentUser()
                if (currentUser != null) {
                    loadAllTransactions()
                } else {
                    Toast.makeText(
                        this@CashbookReportActivity,
                        "Please login first",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this@CashbookReportActivity,
                    "Error loading user data",
                    Toast.LENGTH_SHORT
                ).show()
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
                    filter { eq("user_id", userId) }
                    order("date", Order.DESCENDING)
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<CashbookEntry>()

            allEntries.clear()
            allEntries.addAll(result)

            runOnUiThread {
                // Default filter
                chipAll.isChecked = true
                applyFilter(FilterType.ALL)
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(
                    this@CashbookReportActivity,
                    "Error loading transactions: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyFilter(filterType: FilterType) {
        filteredEntries.clear()

        val todayCal = Calendar.getInstance()
        val todayIso = iso.format(todayCal.time)

        // Start of week (Monday)
        val weekCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            // Normalize to today’s date zone at 00:00
        }
        val weekStartIso = iso.format(weekCal.time)

        // Start of month
        val monthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val monthStartIso = iso.format(monthCal.time)

        when (filterType) {
            FilterType.ALL -> filteredEntries.addAll(allEntries)

            FilterType.TODAY -> filteredEntries.addAll(
                allEntries.filter { it.date == todayIso }
            )

            FilterType.THIS_WEEK -> filteredEntries.addAll(
                allEntries.filter { it.date >= weekStartIso && it.date <= todayIso }
            )

            FilterType.THIS_MONTH -> filteredEntries.addAll(
                allEntries.filter { it.date >= monthStartIso && it.date <= todayIso }
            )

            FilterType.INCOME_ONLY -> filteredEntries.addAll(
                allEntries.filter { it.type == "IN" }
            )

            FilterType.EXPENSE_ONLY -> filteredEntries.addAll(
                allEntries.filter { it.type == "OUT" }
            )
        }

        // Keep newest on top (just in case)
        filteredEntries.sortWith(
            compareByDescending<CashbookEntry> { it.date }
                .thenByDescending { it.createdAt ?: "" }
        )

        reportAdapter.notifyDataSetChanged()
        updateEmptyState()
        updateSummary()
    }

    private fun updateSummary() {
        var income = 0.0
        var expense = 0.0

        filteredEntries.forEach { e ->
            if (e.type == "IN") income += e.amount else expense += e.amount
        }

        totalIncomeAmount.text = currency.format(income)
        totalExpenseAmount.text = currency.format(expense)
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

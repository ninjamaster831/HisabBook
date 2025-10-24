package com.guruyuknow.hisabbook
import android.animation.ObjectAnimator

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class StatisticsFragment : Fragment() {

    // Existing views
    private lateinit var tvTotalEarnings: TextView
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvNetBalance: TextView
    private lateinit var pieChart: PieChart
    private lateinit var lineChart: LineChart
    private lateinit var barChart: BarChart
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var categoryAdapter: TopCategoryAdapter
    private lateinit var staffBarChart: BarChart
    private lateinit var staffChartCard: View

    // AI-powered views
    private lateinit var aiInsightsCard: CardView
    private lateinit var businessHealthCard: CardView
    private lateinit var tvBusinessHealthScore: TextView
    private lateinit var progressBusinessHealth: ProgressBar
    private lateinit var recyclerViewInsights: RecyclerView
    private lateinit var insightsAdapter: AIInsightsAdapter
    private lateinit var recyclerViewAnomalies: RecyclerView
    private lateinit var anomaliesAdapter: AnomaliesAdapter
    private lateinit var cashFlowPredictionChart: LineChart
    private lateinit var fabVoiceAssistant: FloatingActionButton
    private lateinit var chipGroupFilters: ChipGroup
    private lateinit var tvPredictedBalance: TextView
    private lateinit var recyclerViewChallenges: RecyclerView
    private lateinit var challengesAdapter: SavingsChallengesAdapter

    // Data
    private var totalEarnings = 0.0
    private var totalExpenses = 0.0
    private var netBalance = 0.0
    private val monthlyData = mutableListOf<MonthlyData>()
    private val topCategories = mutableListOf<CategoryData>()
    private val staffMonthlyData = mutableListOf<StaffMonthlyData>()

    // AI Data
    private val aiInsights = mutableListOf<AIInsight>()
    private val detectedAnomalies = mutableListOf<FinancialAnomaly>()
    private val savingsChallenges = mutableListOf<SavingsChallenge>()
    private var businessHealthMetrics: BusinessHealthMetrics? = null
    private val cashFlowPredictions = mutableListOf<CashFlowPrediction>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        initAIViews(view)
        setupRecyclerViews()
        setupVoiceAssistant()

        // ✅ Tie async/UI work to VIEW lifecycle to avoid running after view is destroyed
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Load + render while the view is STARTED (auto-cancels on STOPPED)
                loadDataFromDatabase()
            }
        }
    }

    private fun initViews(view: View) {
        tvTotalEarnings = view.findViewById(R.id.tvTotalEarnings)
        tvTotalExpenses = view.findViewById(R.id.tvTotalExpenses)
        tvNetBalance = view.findViewById(R.id.tvNetBalance)
        pieChart = view.findViewById(R.id.pieChart)
        lineChart = view.findViewById(R.id.lineChart)
        barChart = view.findViewById(R.id.barChart)
        recyclerViewCategories = view.findViewById(R.id.recyclerViewCategories)
        staffBarChart = view.findViewById(R.id.staffBarChart)
        staffChartCard = view.findViewById(R.id.staffChartCard)

        categoryAdapter = TopCategoryAdapter(topCategories)
        recyclerViewCategories.layoutManager = LinearLayoutManager(context)
        recyclerViewCategories.adapter = categoryAdapter
    }

    private fun initAIViews(view: View) {
        aiInsightsCard = view.findViewById(R.id.aiInsightsCard)
        businessHealthCard = view.findViewById(R.id.businessHealthCard)
        tvBusinessHealthScore = view.findViewById(R.id.tvBusinessHealthScore)
        progressBusinessHealth = view.findViewById(R.id.progressBusinessHealth)
        recyclerViewInsights = view.findViewById(R.id.recyclerViewInsights)
        recyclerViewAnomalies = view.findViewById(R.id.recyclerViewAnomalies)
        cashFlowPredictionChart = view.findViewById(R.id.cashFlowPredictionChart)
        fabVoiceAssistant = view.findViewById(R.id.fabVoiceAssistant)
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters)
        tvPredictedBalance = view.findViewById(R.id.tvPredictedBalance)
        recyclerViewChallenges = view.findViewById(R.id.recyclerViewChallenges)
    }

    private fun setupRecyclerViews() {
        // AI Insights
        insightsAdapter = AIInsightsAdapter(aiInsights) { insight ->
            // Handle insight click - show detailed explanation
            showInsightDetails(insight)
        }
        recyclerViewInsights.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewInsights.adapter = insightsAdapter

        // Anomalies
        anomaliesAdapter = AnomaliesAdapter(detectedAnomalies) { anomaly ->
            handleAnomalyAction(anomaly)
        }
        recyclerViewAnomalies.layoutManager = LinearLayoutManager(context)
        recyclerViewAnomalies.adapter = anomaliesAdapter

        // Savings Challenges
        challengesAdapter = SavingsChallengesAdapter(savingsChallenges) { challenge ->
            handleChallengeAction(challenge)
        }
        recyclerViewChallenges.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewChallenges.adapter = challengesAdapter
    }

    private fun setupVoiceAssistant() {
        fabVoiceAssistant.setOnClickListener {
            startVoiceRecognition()
        }
    }

    private fun loadDataFromDatabase() {
        // ✅ Use viewLifecycleOwner scope (cancels when view is destroyed)
        viewLifecycleOwner.lifecycleScope.launch {
            val user = SupabaseManager.getCurrentUser() ?: return@launch

            // Reset data
            totalEarnings = 0.0
            totalExpenses = 0.0
            netBalance = 0.0
            monthlyData.clear()
            topCategories.clear()
            staffMonthlyData.clear()
            aiInsights.clear()
            detectedAnomalies.clear()
            savingsChallenges.clear()
            cashFlowPredictions.clear()
            businessHealthMetrics = null

            // Load existing data (keeping your original logic)
            loadExistingData(user.id!!)

            // Generate AI insights
            generateAIInsights()

            // ✅ Only touch UI if the view is still there
            val root = view ?: return@launch
            if (!isAdded) return@launch

            setupUI()
            setupAIFeatures(root)
        }
    }


    private suspend fun loadExistingData(userId: String) {
        // Your existing data loading logic
        StatsDatabase.getCashbookEntries(userId).onSuccess { entries ->
            totalEarnings = entries.filter { it.type == "IN" }.sumOf { it.amount }
            totalExpenses = entries.filter { it.type == "OUT" }.sumOf { it.amount }
            netBalance = totalEarnings - totalExpenses

            // Categories
            val groupedByCategory = entries.groupBy { it.category ?: "Others" }
            groupedByCategory.forEach { (cat, list) ->
                topCategories.add(CategoryData(cat, list.sumOf { it.amount }, getCategoryIcon(cat)))
            }

            // Monthly trends
            val monthFormatter = SimpleDateFormat("MMM", Locale.getDefault())
            entries.groupBy { it.date.substring(0, 7) }.forEach { (yearMonth, list) ->
                val month = try {
                    val parsed = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(yearMonth)
                    monthFormatter.format(parsed ?: Date())
                } catch (e: Exception) {
                    yearMonth
                }
                val earnings = list.filter { it.type == "IN" }.sumOf { it.amount }
                val expenses = list.filter { it.type == "OUT" }.sumOf { it.amount }
                monthlyData.add(MonthlyData(month, earnings, expenses))
            }
            monthlyData.sortBy { it.month }

            // Detect anomalies in transactions
            detectTransactionAnomalies(entries)
        }

        // Load other data (loans, purchases, staff)
        StatsDatabase.getLoans(userId).onSuccess { loans ->
            // Process loans for AI insights
            if (loans.isNotEmpty()) {
                val totalLoans = loans.sumOf { it.amount }
                aiInsights.add(
                    AIInsight(
                        title = "Loan Management",
                        description = "You have ${loans.size} active loans totaling ${formatCurrency(totalLoans)}",
                        type = AIInsightType.DEBT_MANAGEMENT,
                        priority = if (totalLoans > totalEarnings * 0.3) Priority.HIGH else Priority.MEDIUM,
                        actionable = true,
                        action = "Consider loan consolidation to reduce interest burden"
                    )
                )
            }
        }

        StatsDatabase.getStaff(userId).onSuccess { staff ->
            if (staff.isNotEmpty()) {
                processStaffData(staff)
            }
        }
    }

    private fun generateAIInsights() {
        // Business Health Score
        businessHealthMetrics = AIEngine.calculateBusinessHealth(
            totalEarnings, totalExpenses, monthlyData, topCategories, staffMonthlyData
        )

        // Cash Flow Predictions
        cashFlowPredictions.addAll(
            AIEngine.predictCashFlow(monthlyData, staffMonthlyData)
        )

        // Category Insights
        generateCategoryInsights()

        // Spending Pattern Analysis
        analyzePendingPatterns()

        // Generate Savings Challenges
        generateSavingsChallenges()

        // Vendor Performance (if applicable)
        analyzeVendorPerformance()
    }

    private fun generateCategoryInsights() {
        topCategories.forEach { category ->
            val benchmark = AIEngine.getCategoryBenchmark(category.name, totalExpenses)
            val variance = ((category.amount - benchmark) / benchmark) * 100

            if (abs(variance) > 20) { // More than 20% variance
                aiInsights.add(
                    AIInsight(
                        title = "${category.name} Analysis",
                        description = if (variance > 0)
                            "You're spending ${String.format("%.1f", variance)}% more on ${category.name} than similar businesses"
                        else
                            "Great! You're spending ${String.format("%.1f", abs(variance))}% less on ${category.name}",
                        type = if (variance > 0) AIInsightType.COST_OPTIMIZATION else AIInsightType.POSITIVE_TREND,
                        priority = if (abs(variance) > 50) Priority.HIGH else Priority.MEDIUM,
                        actionable = variance > 0,
                        action = if (variance > 0) "Review ${category.name} expenses for optimization opportunities" else null
                    )
                )
            }
        }
    }

    private fun analyzePendingPatterns() {
        if (monthlyData.size >= 3) {
            val trend = AIEngine.calculateTrend(monthlyData.takeLast(3).map { it.expenses })

            if (trend > 0.15) { // 15% increase trend
                aiInsights.add(
                    AIInsight(
                        title = "Rising Expenses Alert",
                        description = "Your expenses have increased by ${String.format("%.1f", trend * 100)}% over the last 3 months",
                        type = AIInsightType.EXPENSE_ALERT,
                        priority = Priority.HIGH,
                        actionable = true,
                        action = "Review recent expense categories and identify cost-cutting opportunities"
                    )
                )
            } else if (trend < -0.10) { // 10% decrease trend
                aiInsights.add(
                    AIInsight(
                        title = "Expense Control Success",
                        description = "Excellent! You've reduced expenses by ${String.format("%.1f", abs(trend) * 100)}% over the last 3 months",
                        type = AIInsightType.POSITIVE_TREND,
                        priority = Priority.LOW,
                        actionable = false,
                        action = null
                    )
                )
            }
        }
    }

    private fun detectTransactionAnomalies(entries: List<CashbookEntry>) {
        // Detect duplicate transactions
        val duplicates = entries.groupBy { "${it.amount}_${it.date}_${it.description}" }
            .filter { it.value.size > 1 }

        duplicates.forEach { (_, transactions) ->
            detectedAnomalies.add(
                FinancialAnomaly(
                    id = UUID.randomUUID().toString(),
                    type = AnomalyType.DUPLICATE_TRANSACTION,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Potential duplicate: ${transactions.size} transactions of ₹${transactions.first().amount}",
                    suggestedAction = "Review and remove duplicates if necessary",
                    detectedAt = System.currentTimeMillis(),
                    affectedAmount = transactions.first().amount,
                    transactionIds = transactions.mapNotNull { it.id }
                )
            )
        }


        // Detect unusual amounts (outliers)
        val amounts = entries.map { it.amount }
        val avgAmount = amounts.average()
        val unusualThreshold = avgAmount * 5 // 5x average

        entries.filter { it.amount > unusualThreshold }.forEach { transaction ->
            detectedAnomalies.add(
                FinancialAnomaly(
                    id = UUID.randomUUID().toString(),
                    type = AnomalyType.UNUSUAL_EXPENSE,
                    severity = if (transaction.amount > avgAmount * 10) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM,
                    description = "Unusually large transaction: ₹${formatCurrency(transaction.amount)}",
                    suggestedAction = "Verify this transaction is legitimate",
                    detectedAt = System.currentTimeMillis(),
                    affectedAmount = transaction.amount,
                    transactionIds = listOfNotNull(transaction.id)   // <-- FIXED
                )
            )
        }

    }

    private fun generateSavingsChallenges() {
        // Weekly expense reduction challenge
        if (monthlyData.isNotEmpty()) {
            val avgWeeklyExpense = monthlyData.last().expenses / 4
            savingsChallenges.add(
                SavingsChallenge(
                    id = "weekly_reduction",
                    title = "Weekly Expense Challenge",
                    description = "Reduce this week's expenses by 10%",
                    targetAmount = avgWeeklyExpense * 0.1,
                    currentProgress = 0.0,
                    daysRemaining = 7,
                    reward = "₹50 savings bonus",
                    difficulty = ChallengeDifficulty.MEDIUM,
                    category = "General"
                )
            )
        }

        // Category-specific challenge
        val highestCategory = topCategories.maxByOrNull { it.amount }
        if (highestCategory != null) {
            savingsChallenges.add(
                SavingsChallenge(
                    id = "category_optimization",
                    title = "${highestCategory.name} Optimization",
                    description = "Reduce ${highestCategory.name} expenses by 15% this month",
                    targetAmount = highestCategory.amount * 0.15,
                    currentProgress = 0.0,
                    daysRemaining = 30,
                    reward = "Efficiency Master Badge",
                    difficulty = ChallengeDifficulty.HARD,
                    category = highestCategory.name
                )
            )
        }
    }

    private fun setupUI() {
        updateSummaryCards()

        if (totalEarnings == 0.0 && totalExpenses == 0.0) {
            pieChart.clear()
        } else {
            setupPieChart()
        }

        if (monthlyData.isEmpty()) {
            lineChart.clear()
            barChart.clear()
        } else {
            setupLineChart()
            setupBarChart()
        }

        setupStaffChart()

        if (topCategories.isEmpty()) {
            recyclerViewCategories.visibility = View.GONE
        } else {
            recyclerViewCategories.visibility = View.VISIBLE
            categoryAdapter.notifyDataSetChanged()
        }
    }

    private fun setupAIFeatures(root: View) {
        setupBusinessHealthScore()
        setupAIInsights()
        setupAnomalyDetection()
        setupCashFlowPrediction()
        setupSavingsChallenges()
        setupSmartFilters() // pass root
    }


    private fun setupBusinessHealthScore() {
        businessHealthMetrics?.let { metrics ->
            tvBusinessHealthScore.text = "${metrics.overallScore.toInt()}/100"

            // Animate progress bar
            ObjectAnimator.ofInt(progressBusinessHealth, "progress", 0, metrics.overallScore.toInt())
                .setDuration(1500)
                .start()

            // Set color based on score
            val color = when {
                metrics.overallScore >= 80 -> Color.parseColor("#4CAF50") // Green
                metrics.overallScore >= 60 -> Color.parseColor("#FF9800") // Orange
                else -> Color.parseColor("#F44336") // Red
            }

            progressBusinessHealth.progressDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            tvBusinessHealthScore.setTextColor(color)

            // Add health insights
            metrics.riskFactors.forEach { risk ->
                aiInsights.add(
                    AIInsight(
                        title = "Risk Factor",
                        description = risk,
                        type = AIInsightType.RISK_ALERT,
                        priority = Priority.HIGH,
                        actionable = true,
                        action = "Address this risk to improve business health"
                    )
                )
            }

            metrics.opportunities.forEach { opportunity ->
                aiInsights.add(
                    AIInsight(
                        title = "Growth Opportunity",
                        description = opportunity,
                        type = AIInsightType.GROWTH_OPPORTUNITY,
                        priority = Priority.MEDIUM,
                        actionable = true,
                        action = "Consider implementing this opportunity"
                    )
                )
            }
        }
    }

    private fun setupAIInsights() {
        if (aiInsights.isNotEmpty()) {
            aiInsightsCard.visibility = View.VISIBLE
            insightsAdapter.notifyDataSetChanged()
        } else {
            aiInsightsCard.visibility = View.GONE
        }
    }

    private fun setupAnomalyDetection() {
        if (detectedAnomalies.isNotEmpty()) {
            recyclerViewAnomalies.visibility = View.VISIBLE
            anomaliesAdapter.notifyDataSetChanged()
        } else {
            recyclerViewAnomalies.visibility = View.GONE
        }
    }

    private fun setupCashFlowPrediction() {
        if (cashFlowPredictions.isNotEmpty()) {
            // Setup prediction chart
            val entries = ArrayList<Entry>()
            val labels = mutableListOf<String>()

            cashFlowPredictions.forEachIndexed { index, prediction ->
                entries.add(Entry(index.toFloat(), prediction.predictedBalance.toFloat()))
                labels.add(prediction.month)
            }

            val dataSet = LineDataSet(entries, "Predicted Cash Flow")
            dataSet.color = Color.parseColor("#2196F3")
            dataSet.setCircleColor(Color.parseColor("#2196F3"))
            dataSet.lineWidth = 3f
            dataSet.circleRadius = 5f
            dataSet.setDrawFilled(true)
            dataSet.fillColor = Color.parseColor("#2196F3")
            dataSet.fillAlpha = 30
            dataSet.enableDashedLine(10f, 5f, 0f) // Dashed line for predictions

            val lineData = LineData(dataSet)
            cashFlowPredictionChart.data = lineData
            cashFlowPredictionChart.description.isEnabled = false
            cashFlowPredictionChart.legend.isEnabled = true

            // Customize X-axis
            val xAxis = cashFlowPredictionChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)

            cashFlowPredictionChart.axisRight.isEnabled = false
            cashFlowPredictionChart.animateX(1000)
            cashFlowPredictionChart.invalidate()

            // Update predicted balance text
            val nextMonthPrediction = cashFlowPredictions.firstOrNull()
            nextMonthPrediction?.let {
                tvPredictedBalance.text = "Next Month: ${formatCurrency(it.predictedBalance)}"
                tvPredictedBalance.setTextColor(
                    if (it.predictedBalance >= 0) Color.parseColor("#4CAF50")
                    else Color.parseColor("#F44336")
                )
            }
        }
    }

    private fun setupSavingsChallenges() {
        if (savingsChallenges.isNotEmpty()) {
            recyclerViewChallenges.visibility = View.VISIBLE
            challengesAdapter.notifyDataSetChanged()
        } else {
            recyclerViewChallenges.visibility = View.GONE
        }
    }

    private fun setupSmartFilters() {
        // Add filter chips
        val filters = listOf("All Insights", "High Priority", "Actionable", "Trends", "Risks")

        filters.forEach { filter ->
            val chip = Chip(context)
            chip.text = filter
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    filterInsights(filter)
                }
            }
            chipGroupFilters.addView(chip)
        }

        // Set default selection
        (chipGroupFilters.getChildAt(0) as Chip).isChecked = true
    }

    // AI Helper Methods
    private fun startVoiceRecognition() {
        // Implement voice recognition for financial queries
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN") // Hindi support
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about your finances...")
        }

        // Handle the voice input and generate AI response
        // This would integrate with your voice processing logic
    }

    private fun filterInsights(filter: String) {
        val filteredInsights = when (filter) {
            "High Priority" -> aiInsights.filter { it.priority == Priority.HIGH }
            "Actionable" -> aiInsights.filter { it.actionable }
            "Trends" -> aiInsights.filter { it.type == AIInsightType.POSITIVE_TREND || it.type == AIInsightType.NEGATIVE_TREND }
            "Risks" -> aiInsights.filter { it.type == AIInsightType.RISK_ALERT || it.type == AIInsightType.EXPENSE_ALERT }
            else -> aiInsights
        }

        insightsAdapter.updateData(filteredInsights)
    }

    private fun showInsightDetails(insight: AIInsight) {
        // Show detailed explanation of the insight
        // Could open a dialog or navigate to a detailed view
    }

    private fun handleAnomalyAction(anomaly: FinancialAnomaly) {
        // Handle user action on anomaly (dismiss, fix, investigate)
        when (anomaly.type) {
            AnomalyType.DUPLICATE_TRANSACTION -> {
                // Show options to merge or delete duplicates
            }
            AnomalyType.UNUSUAL_EXPENSE -> {
                // Show transaction details for verification
            }
            else -> {
                // Handle other anomaly types
            }
        }
    }

    private fun handleChallengeAction(challenge: SavingsChallenge) {
        // Handle challenge interaction (accept, track progress, etc.)
        when (challenge.id) {
            "weekly_reduction" -> {
                // Start tracking weekly expenses
            }
            "category_optimization" -> {
                // Navigate to category details
            }
        }
    }

    // Utility Methods
    private fun getCategoryIcon(category: String): String {
        return when (category.lowercase()) {
            "food", "restaurant" -> "🍽️"
            "transport", "fuel" -> "🚗"
            "utilities" -> "⚡"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "health", "medical" -> "🏥"
            "education" -> "📚"
            "business" -> "💼"
            else -> "📊"
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("hi", "IN")).format(amount)
    }

    private fun processStaffData(staff: List<StaffEntry>) {
        val monthFormatter = SimpleDateFormat("MMM", Locale.getDefault())
        val inputFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

        val grouped = staff.groupBy { entry ->
            try {
                val date = inputFormatter.parse(entry.created_at ?: "")
                monthFormatter.format(date ?: Date())
            } catch (e: Exception) {
                "Unknown"
            }
        }

        grouped.forEach { (month, list) ->
            staffMonthlyData.add(
                StaffMonthlyData(
                    month = month,
                    staffCount = list.size,
                    totalSalary = list.sumOf { it.salary_amount }
                )
            )
        }

        // Add staff-related insights
        val totalStaffCost = staff.sumOf { it.salary_amount }
        if (totalStaffCost > totalEarnings * 0.4) { // Staff cost > 40% of earnings
            aiInsights.add(
                AIInsight(
                    title = "High Staff Costs",
                    description = "Staff salaries represent ${String.format("%.1f", (totalStaffCost / totalEarnings) * 100)}% of your earnings",
                    type = AIInsightType.COST_OPTIMIZATION,
                    priority = Priority.HIGH,
                    actionable = true,
                    action = "Consider optimizing staff allocation or increasing revenue"
                )
            )
        }
    }

    private fun analyzeVendorPerformance() {
        // This would analyze vendor data if available
        // For now, just a placeholder for future implementation
    }

    // Keep all your existing methods (setupPieChart, setupLineChart, etc.)
    private fun updateSummaryCards() {
        val formatter = NumberFormat.getCurrencyInstance(Locale("hi", "IN"))

        tvTotalEarnings.text = formatter.format(totalEarnings)
        tvTotalExpenses.text = formatter.format(totalExpenses)
        tvNetBalance.text = formatter.format(netBalance)

        val balanceColor = if (netBalance >= 0)
            Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        tvNetBalance.setTextColor(balanceColor)
    }

    private fun setupPieChart() {
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(totalExpenses.toFloat(), "Expenses"))
        entries.add(PieEntry(totalEarnings.toFloat(), "Earnings"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#FF5722"),
            Color.parseColor("#4CAF50")
        )
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.WHITE

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = true
        pieChart.setDrawEntryLabels(false)
        pieChart.animateY(1000)
        pieChart.invalidate()
    }

    private fun setupLineChart() {
        val earningsEntries = ArrayList<Entry>()
        val expenseEntries = ArrayList<Entry>()

        monthlyData.forEachIndexed { index, data ->
            earningsEntries.add(Entry(index.toFloat(), data.earnings.toFloat()))
            expenseEntries.add(Entry(index.toFloat(), data.expenses.toFloat()))
        }

        val earningsDataSet = LineDataSet(earningsEntries, "Earnings")
        earningsDataSet.color = Color.parseColor("#4CAF50")
        earningsDataSet.setCircleColor(Color.parseColor("#4CAF50"))
        earningsDataSet.lineWidth = 2f
        earningsDataSet.circleRadius = 4f
        earningsDataSet.setDrawFilled(true)
        earningsDataSet.fillColor = Color.parseColor("#4CAF50")
        earningsDataSet.fillAlpha = 50

        val expenseDataSet = LineDataSet(expenseEntries, "Expenses")
        expenseDataSet.color = Color.parseColor("#FF5722")
        expenseDataSet.setCircleColor(Color.parseColor("#FF5722"))
        expenseDataSet.lineWidth = 2f
        expenseDataSet.circleRadius = 4f
        expenseDataSet.setDrawFilled(true)
        expenseDataSet.fillColor = Color.parseColor("#FF5722")
        expenseDataSet.fillAlpha = 50

        val lineData = LineData(earningsDataSet, expenseDataSet)
        lineChart.data = lineData
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(monthlyData.map { it.month })
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        lineChart.axisRight.isEnabled = false
        lineChart.animateX(1000)
        lineChart.invalidate()
    }

    private fun setupBarChart() {
        val earningsEntries = ArrayList<BarEntry>()
        val expenseEntries = ArrayList<BarEntry>()

        monthlyData.forEachIndexed { index, data ->
            earningsEntries.add(BarEntry(index.toFloat(), data.earnings.toFloat()))
            expenseEntries.add(BarEntry(index.toFloat(), data.expenses.toFloat()))
        }

        val earningsDataSet = BarDataSet(earningsEntries, "Earnings")
        earningsDataSet.color = Color.parseColor("#4CAF50")

        val expenseDataSet = BarDataSet(expenseEntries, "Expenses")
        expenseDataSet.color = Color.parseColor("#FF5722")

        val barData = BarData(earningsDataSet, expenseDataSet)
        barData.barWidth = 0.35f

        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = true
        barChart.setDrawGridBackground(false)

        val groupSpace = 0.3f
        val barSpace = 0.0f
        barChart.groupBars(0f, groupSpace, barSpace)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(monthlyData.map { it.month })
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        barChart.axisRight.isEnabled = false
        barChart.animateY(1000)
        barChart.invalidate()
    }

    private fun setupStaffChart() {
        if (staffMonthlyData.isEmpty()) {
            staffChartCard.visibility = View.GONE
            return
        }
        staffChartCard.visibility = View.VISIBLE

        val staffCountEntries = ArrayList<BarEntry>()
        val salaryEntries = ArrayList<BarEntry>()
        val months = staffMonthlyData.map { it.month }

        staffMonthlyData.forEachIndexed { index, data ->
            staffCountEntries.add(BarEntry(index.toFloat(), data.staffCount.toFloat()))
            salaryEntries.add(BarEntry(index.toFloat(), (data.totalSalary / 1000).toFloat()))
        }

        val staffCountSet = BarDataSet(staffCountEntries, "Staff Count").apply {
            color = Color.parseColor("#2196F3")
        }

        val salarySet = BarDataSet(salaryEntries, "Total Salary (in K)").apply {
            color = Color.parseColor("#FF9800")
        }

        val barData = BarData(staffCountSet, salarySet)
        val groupSpace = 0.4f
        val barSpace = 0.05f
        val barWidth = 0.25f

        barData.barWidth = barWidth
        staffBarChart.data = barData

        staffBarChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = IndexAxisValueFormatter(months)
            granularity = 1f
            setDrawGridLines(false)
            axisMinimum = 0f
            axisMaximum = months.size.toFloat()
        }

        staffBarChart.axisLeft.axisMinimum = 0f
        staffBarChart.axisRight.isEnabled = false

        staffBarChart.setFitBars(true)
        staffBarChart.groupBars(0f, groupSpace, barSpace)

        staffBarChart.description.isEnabled = false
        staffBarChart.legend.isEnabled = true
        staffBarChart.animateY(1000)
        staffBarChart.invalidate()
    }

    // Data classes (existing)
    data class MonthlyData(val month: String, val earnings: Double, val expenses: Double)
    data class CategoryData(val name: String, val amount: Double, val icon: String)

    // AI Data Classes
    data class AIInsight(
        val title: String,
        val description: String,
        val type: AIInsightType,
        val priority: Priority,
        val actionable: Boolean,
        val action: String?
    )

    data class FinancialAnomaly(
        val id: String,
        val type: AnomalyType,
        val severity: AnomalySeverity,
        val description: String,
        val suggestedAction: String,
        val detectedAt: Long,
        val affectedAmount: Double,
        val transactionIds: List<String>
    )

    data class BusinessHealthMetrics(
        val overallScore: Float,
        val cashFlowHealth: Float,
        val expenseEfficiency: Float,
        val growthTrend: Float,
        val riskFactors: List<String>,
        val opportunities: List<String>
    )

    data class CashFlowPrediction(
        val month: String,
        val predictedBalance: Double,
        val confidence: Float,
        val factors: List<String>
    )

    data class SavingsChallenge(
        val id: String,
        val title: String,
        val description: String,
        val targetAmount: Double,
        val currentProgress: Double,
        val daysRemaining: Int,
        val reward: String,
        val difficulty: ChallengeDifficulty,
        val category: String
    )

    // Enums
    enum class AIInsightType {
        POSITIVE_TREND, NEGATIVE_TREND, COST_OPTIMIZATION,
        DEBT_MANAGEMENT, GROWTH_OPPORTUNITY, RISK_ALERT, EXPENSE_ALERT
    }

    enum class Priority { LOW, MEDIUM, HIGH }

    enum class AnomalyType {
        DUPLICATE_TRANSACTION, UNUSUAL_EXPENSE,
        SUSPICIOUS_VENDOR, IRREGULAR_PATTERN
    }

    enum class AnomalySeverity { LOW, MEDIUM, HIGH }

    enum class ChallengeDifficulty { EASY, MEDIUM, HARD }

    // Existing Adapter
    class TopCategoryAdapter(private val categories: List<CategoryData>) :
        RecyclerView.Adapter<TopCategoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(R.id.tvCategoryIcon)
            val tvName: TextView = view.findViewById(R.id.tvCategoryName)
            val tvAmount: TextView = view.findViewById(R.id.tvCategoryAmount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            val formatter = NumberFormat.getCurrencyInstance(Locale("hi", "IN"))

            holder.tvIcon.text = category.icon
            holder.tvName.text = category.name
            holder.tvAmount.text = formatter.format(category.amount)
        }

        override fun getItemCount() = categories.size
    }

    // AI Adapters
    class AIInsightsAdapter(
        private var insights: List<AIInsight>,
        private val onInsightClick: (AIInsight) -> Unit
    ) : RecyclerView.Adapter<AIInsightsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvInsightTitle)
            val tvDescription: TextView = view.findViewById(R.id.tvInsightDescription)
            val tvPriority: TextView = view.findViewById(R.id.tvInsightPriority)
            val cardInsight: CardView = view.findViewById(R.id.cardInsight)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_insight, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val insight = insights[position]

            holder.tvTitle.text = insight.title
            holder.tvDescription.text = insight.description
            holder.tvPriority.text = insight.priority.name

            // Set priority color
            val priorityColor = when (insight.priority) {
                Priority.HIGH -> Color.parseColor("#F44336")
                Priority.MEDIUM -> Color.parseColor("#FF9800")
                Priority.LOW -> Color.parseColor("#4CAF50")
            }
            holder.tvPriority.setTextColor(priorityColor)

            // Set card border color based on type
            val borderColor = when (insight.type) {
                AIInsightType.POSITIVE_TREND -> Color.parseColor("#4CAF50")
                AIInsightType.RISK_ALERT, AIInsightType.EXPENSE_ALERT -> Color.parseColor("#F44336")
                AIInsightType.GROWTH_OPPORTUNITY -> Color.parseColor("#2196F3")
                else -> Color.parseColor("#FF9800")
            }
            holder.cardInsight.setCardBackgroundColor(Color.parseColor("#F5F5F5"))

            holder.cardInsight.setOnClickListener {
                onInsightClick(insight)
            }
        }

        override fun getItemCount() = insights.size

        fun updateData(newInsights: List<AIInsight>) {
            insights = newInsights
            notifyDataSetChanged()
        }
    }

    class AnomaliesAdapter(
        private val anomalies: List<FinancialAnomaly>,
        private val onAnomalyClick: (FinancialAnomaly) -> Unit
    ) : RecyclerView.Adapter<AnomaliesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDescription: TextView = view.findViewById(R.id.tvAnomalyDescription)
            val tvSeverity: TextView = view.findViewById(R.id.tvAnomalySeverity)
            val tvAction: TextView = view.findViewById(R.id.tvAnomalySuggestedAction)
            val cardAnomaly: CardView = view.findViewById(R.id.cardAnomaly)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_anomaly, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val anomaly = anomalies[position]

            holder.tvDescription.text = anomaly.description
            holder.tvSeverity.text = anomaly.severity.name
            holder.tvAction.text = anomaly.suggestedAction

            val severityColor = when (anomaly.severity) {
                AnomalySeverity.HIGH -> Color.parseColor("#F44336")
                AnomalySeverity.MEDIUM -> Color.parseColor("#FF9800")
                AnomalySeverity.LOW -> Color.parseColor("#4CAF50")
            }
            holder.tvSeverity.setTextColor(severityColor)

            holder.cardAnomaly.setOnClickListener {
                onAnomalyClick(anomaly)
            }
        }

        override fun getItemCount() = anomalies.size
    }

    class SavingsChallengesAdapter(
        private val challenges: List<SavingsChallenge>,
        private val onChallengeClick: (SavingsChallenge) -> Unit
    ) : RecyclerView.Adapter<SavingsChallengesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvChallengeTitle)
            val tvDescription: TextView = view.findViewById(R.id.tvChallengeDescription)
            val tvTarget: TextView = view.findViewById(R.id.tvChallengeTarget)
            val tvDaysRemaining: TextView = view.findViewById(R.id.tvDaysRemaining)
            val tvReward: TextView = view.findViewById(R.id.tvReward)
            val progressChallenge: ProgressBar = view.findViewById(R.id.progressChallenge)
            val cardChallenge: CardView = view.findViewById(R.id.cardChallenge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_savings_challenge, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val challenge = challenges[position]
            val formatter = NumberFormat.getCurrencyInstance(Locale("hi", "IN"))

            holder.tvTitle.text = challenge.title
            holder.tvDescription.text = challenge.description
            holder.tvTarget.text = "Target: ${formatter.format(challenge.targetAmount)}"
            holder.tvDaysRemaining.text = "${challenge.daysRemaining} days left"
            holder.tvReward.text = "🏆 ${challenge.reward}"

            val progress = ((challenge.currentProgress / challenge.targetAmount) * 100).toInt()
            holder.progressChallenge.progress = progress

            val difficultyColor = when (challenge.difficulty) {
                ChallengeDifficulty.EASY -> Color.parseColor("#4CAF50")
                ChallengeDifficulty.MEDIUM -> Color.parseColor("#FF9800")
                ChallengeDifficulty.HARD -> Color.parseColor("#F44336")
            }

            holder.cardChallenge.setOnClickListener {
                onChallengeClick(challenge)
            }
        }

        override fun getItemCount() = challenges.size
    }

    companion object {
        @JvmStatic
        fun newInstance() = StatisticsFragment()
    }
}
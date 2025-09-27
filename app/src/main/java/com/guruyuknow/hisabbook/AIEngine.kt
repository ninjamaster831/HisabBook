package com.guruyuknow.hisabbook

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// Main AI Engine
object AIEngine {

    fun calculateBusinessHealth(
        totalEarnings: Double,
        totalExpenses: Double,
        monthlyData: List<StatisticsFragment.MonthlyData>,
        categories: List<StatisticsFragment.CategoryData>,
        staffData: List<StaffMonthlyData>
    ): StatisticsFragment.BusinessHealthMetrics {

        var score = 50f // Base score
        val riskFactors = mutableListOf<String>()
        val opportunities = mutableListOf<String>()

        // Cash Flow Health (30% weight)
        val netBalance = totalEarnings - totalExpenses
        val cashFlowHealth = when {
            netBalance > totalEarnings * 0.2 -> {
                score += 20f
                90f
            }
            netBalance > 0 -> {
                score += 10f
                70f
            }
            netBalance > -totalEarnings * 0.1 -> {
                score -= 10f
                riskFactors.add("Negative cash flow detected")
                40f
            }
            else -> {
                score -= 20f
                riskFactors.add("Severe cash flow crisis")
                20f
            }
        }

        // Expense Efficiency (25% weight)
        val expenseEfficiency = if (totalEarnings > 0) {
            val expenseRatio = totalExpenses / totalEarnings
            when {
                expenseRatio < 0.6 -> {
                    score += 15f
                    opportunities.add("Excellent expense management - consider growth investments")
                    85f
                }
                expenseRatio < 0.8 -> {
                    score += 10f
                    75f
                }
                expenseRatio < 1.0 -> {
                    score -= 5f
                    riskFactors.add("High expense ratio - review spending")
                    50f
                }
                else -> {
                    score -= 15f
                    riskFactors.add("Expenses exceed earnings")
                    25f
                }
            }
        } else 50f

        // Growth Trend (25% weight)
        val growthTrend = if (monthlyData.size >= 3) {
            val recentMonths = monthlyData.takeLast(3)
            val earningsTrend = calculateTrend(recentMonths.map { it.earnings })
            when {
                earningsTrend > 0.1 -> {
                    score += 15f
                    opportunities.add("Strong growth trend - consider scaling operations")
                    90f
                }
                earningsTrend > 0.05 -> {
                    score += 10f
                    80f
                }
                earningsTrend > -0.05 -> {
                    60f
                }
                else -> {
                    score -= 10f
                    riskFactors.add("Declining earnings trend")
                    40f
                }
            }
        } else 60f

        // Staff Cost Analysis (20% weight)
        if (staffData.isNotEmpty()) {
            val totalStaffCost = staffData.sumOf { it.totalSalary }
            val staffCostRatio = if (totalEarnings > 0) totalStaffCost / totalEarnings else 0.0

            when {
                staffCostRatio < 0.3 -> {
                    score += 10f
                    opportunities.add("Efficient staff utilization")
                }
                staffCostRatio > 0.5 -> {
                    score -= 10f
                    riskFactors.add("High staff costs relative to earnings")
                }
            }
        }

        // Additional opportunities based on data
        if (categories.isNotEmpty()) {
            val topCategory = categories.maxByOrNull { it.amount }
            if (topCategory != null && topCategory.amount > totalExpenses * 0.3) {
                opportunities.add("Focus on optimizing ${topCategory.name} expenses for maximum impact")
            }
        }

        return StatisticsFragment.BusinessHealthMetrics(
            overallScore = score.coerceIn(0f, 100f),
            cashFlowHealth = cashFlowHealth,
            expenseEfficiency = expenseEfficiency,
            growthTrend = growthTrend,
            riskFactors = riskFactors,
            opportunities = opportunities
        )
    }

    fun predictCashFlow(
        monthlyData: List<StatisticsFragment.MonthlyData>,
        staffData: List<StaffMonthlyData>
    ): List<StatisticsFragment.CashFlowPrediction> {
        if (monthlyData.size < 2) return emptyList()

        val predictions = mutableListOf<StatisticsFragment.CashFlowPrediction>()
        val calendar = Calendar.getInstance()

        // Simple trend-based prediction for next 3 months
        val recentData = monthlyData.takeLast(3)
        val avgEarnings = recentData.map { it.earnings }.average()
        val avgExpenses = recentData.map { it.expenses }.average()
        val earningsTrend = calculateTrend(recentData.map { it.earnings })
        val expensesTrend = calculateTrend(recentData.map { it.expenses })

        for (i in 1..3) {
            calendar.add(Calendar.MONTH, 1)
            val monthName = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(calendar.time)

            val predictedEarnings = avgEarnings * (1 + earningsTrend * i)
            val predictedExpenses = avgExpenses * (1 + expensesTrend * i)
            val predictedBalance = predictedEarnings - predictedExpenses

            val factors = mutableListOf<String>()
            if (earningsTrend > 0.05) factors.add("Growing earnings trend")
            if (expensesTrend > 0.05) factors.add("Rising expense trend")
            if (staffData.isNotEmpty()) factors.add("Staff salary commitments")

            val confidence = when {
                monthlyData.size >= 6 -> 0.8f
                monthlyData.size >= 3 -> 0.6f
                else -> 0.4f
            }

            predictions.add(
                StatisticsFragment.CashFlowPrediction(
                    month = monthName,
                    predictedBalance = predictedBalance,
                    confidence = confidence,
                    factors = factors
                )
            )
        }

        return predictions
    }

    fun calculateTrend(values: List<Double>): Double {
        if (values.size < 2) return 0.0

        val n = values.size
        val x = (0 until n).map { it.toDouble() }
        val y = values

        val xMean = x.average()
        val yMean = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - xMean) * (yi - yMean) }
        val denominator = x.sumOf { (it - xMean).pow(2) }

        return if (denominator != 0.0) numerator / denominator else 0.0
    }

    fun getCategoryBenchmark(category: String, totalExpenses: Double): Double {
        // Industry benchmarks (percentage of total expenses)
        val benchmarks = mapOf(
            "food" to 0.15,
            "transport" to 0.10,
            "utilities" to 0.08,
            "rent" to 0.25,
            "staff" to 0.30,
            "marketing" to 0.05,
            "supplies" to 0.12,
            "others" to 0.10
        )

        val benchmark = benchmarks[category.lowercase()] ?: 0.10
        return totalExpenses * benchmark
    }
}
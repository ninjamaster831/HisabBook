package com.guruyuknow.hisabbook

import android.util.Log
import com.guruyuknow.hisabbook.HomeFragment.DashboardData
import com.guruyuknow.hisabbook.Shop.Purchase
import com.guruyuknow.hisabbook.Staff.AttendanceStatus
import com.guruyuknow.hisabbook.Staff.SalaryType
import com.guruyuknow.hisabbook.Staff.Staff
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class HomeDatabase {

    companion object {
        private const val TAG = "HomeDatabase"

        // Parse "yyyy-MM-dd" (your cashbook date field) into epoch millis
        private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        private fun parseDateMillis(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
            return try {
                ymd.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (_: ParseException) {
                System.currentTimeMillis()
            }
        }

        // Produce a stable Long id from a String (for Transaction.id which is Long)
        private fun stableIdFromString(s: String?): Long {
            if (s.isNullOrBlank()) return System.currentTimeMillis()
            var h = 1125899906842597L
            for (ch in s) h = 31L * h + ch.code
            return h
        }

        private fun mapTypeToTransactionType(type: String?): TransactionType {
            return when (type?.uppercase(Locale.ROOT)) {
                "IN", "INCOME" -> TransactionType.INCOME
                "OUT", "EXPENSE" -> TransactionType.EXPENSE
                "SALE" -> TransactionType.SALE
                "PURCHASE" -> TransactionType.PURCHASE
                "LOAN_GIVEN" -> TransactionType.LOAN_GIVEN
                "LOAN_RECEIVED" -> TransactionType.LOAN_RECEIVED
                else -> TransactionType.EXPENSE // safe default if unknown
            }
        }
    }

    // ==================== DASHBOARD DATA LOADING ====================

    suspend fun loadDashboardData(userId: String): Result<DashboardData> {
        return try {
            withContext(Dispatchers.IO) {
                // Use string dates (yyyy-MM-dd) everywhere for cashbook queries
                val today = LocalDate.now()
                val todayStr = today.format(DateTimeFormatter.ISO_DATE) // e.g. "2025-10-02"
                val startOfMonthStr = today.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                val endDateStr = today.format(DateTimeFormatter.ISO_DATE) // up to today

                Log.d(TAG, "loadDashboardData(): today=$todayStr, month=$startOfMonthStr..$endDateStr")

                val dashboard = coroutineScope {
                    val todayIncomeDef            = async { getTodayIncome(userId, todayStr) }
                    val todayExpensesDef          = async { getTodayExpenses(userId, todayStr) }
                    val monthlyIncomeDef          = async { getMonthlyIncome(userId, startOfMonthStr, endDateStr) }
                    val monthlyExpensesDef        = async { getMonthlyExpenses(userId, startOfMonthStr, endDateStr) }
                    val todaySalesDef             = async { getTodaySales(userId, todayStr) }
                    val monthlySalesDef           = async { getMonthlySales(userId, startOfMonthStr, endDateStr) }
                    val monthlyPurchasesDef       = async { getMonthlyPurchases(userId, startOfMonthStr, endDateStr) }
                    val activeStaffCountDef       = async { getActiveStaffCount(userId) }
                    val monthlyStaffExpensesDef   = async { getMonthlyStaffExpenses(userId, startOfMonthStr, endDateStr) }
                    val totalLoansGivenDef        = async { getTotalLoansGiven(userId) }
                    val totalLoansReceivedDef     = async { getTotalLoansReceived(userId) }
                    val pendingCollectionsDef     = async { getPendingCollections(userId) }
                    val recentTransactionsDef     = async { getRecentTransactions(userId, 10) } // List<Transaction>

                    DashboardData(
                        todayIncome = todayIncomeDef.await(),
                        todayExpenses = todayExpensesDef.await(),
                        monthlyIncome = monthlyIncomeDef.await(),
                        monthlyExpenses = monthlyExpensesDef.await(),
                        todaySales = todaySalesDef.await(),
                        monthlySales = monthlySalesDef.await(),
                        monthlyPurchases = monthlyPurchasesDef.await(),
                        totalStaff = activeStaffCountDef.await(),
                        monthlyStaffExpenses = monthlyStaffExpensesDef.await(),
                        totalLoansGiven = totalLoansGivenDef.await(),
                        totalLoansReceived = totalLoansReceivedDef.await(),
                        pendingCollections = pendingCollectionsDef.await(),
                        recentTransactions = recentTransactionsDef.await()
                    )
                }

                Log.d(TAG, "Dashboard data loaded successfully")
                Result.success(dashboard)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dashboard data: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== CASHBOOK SUMMARIES ====================

    // Uses date = "yyyy-MM-dd" and type "IN"
    private suspend fun getTodayIncome(userId: String, todayDateStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("cashbook_entries")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("type", "IN")               // IMPORTANT: match your stored value
                        eq("date", todayDateStr)       // "2025-10-02"
                    }
                }
                .decodeList<CashbookEntry>()

            val total = rows.sumOf { it.amount ?: 0.0 }
            Log.d(TAG, "getTodayIncome($todayDateStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's income: ${e.message}", e)
            0.0
        }
    }

    // Uses date = "yyyy-MM-dd" and type "OUT"
    private suspend fun getTodayExpenses(userId: String, todayDateStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("cashbook_entries")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("type", "OUT")              // IMPORTANT: match your stored value
                        eq("date", todayDateStr)
                    }
                }
                .decodeList<CashbookEntry>()

            val total = rows.sumOf { it.amount ?: 0.0 }
            Log.d(TAG, "getTodayExpenses($todayDateStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's expenses: ${e.message}", e)
            0.0
        }
    }

    // Monthly: from first day of month to today (inclusive) using string dates
    private suspend fun getMonthlyIncome(userId: String, startDateStr: String, endDateStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("cashbook_entries")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("type", "IN")
                        gte("date", startDateStr)      // "2025-10-01"
                        lte("date", endDateStr)        // "2025-10-02"
                    }
                }
                .decodeList<CashbookEntry>()

            val total = rows.sumOf { it.amount ?: 0.0 }
            Log.d(TAG, "getMonthlyIncome($startDateStr..$endDateStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly income: ${e.message}", e)
            0.0
        }
    }

    private suspend fun getMonthlyExpenses(userId: String, startDateStr: String, endDateStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("cashbook_entries")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("type", "OUT")
                        gte("date", startDateStr)
                        lte("date", endDateStr)
                    }
                }
                .decodeList<CashbookEntry>()

            val total = rows.sumOf { it.amount ?: 0.0 }
            Log.d(TAG, "getMonthlyExpenses($startDateStr..$endDateStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly expenses: ${e.message}", e)
            0.0
        }
    }

    /**
     * NOW RETURNS List<Transaction>
     * Orders by the ISO "date" column so newest appears first.
     */
    private suspend fun getRecentTransactions(userId: String, limit: Int): List<Transaction> {
        return try {
            val rows = SupabaseManager.client.from("cashbook_entries")
                .select {
                    filter { eq("user_id", userId) }
                    order("date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<CashbookEntry>()

            Log.d(TAG, "getRecentTransactions() -> ${rows.size} rows")
            rows.map { it.toTransaction() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent transactions: ${e.message}", e)
            emptyList()
        }
    }

    // ==================== PURCHASES (left as-is; ensure your schema matches) ====================

    // If your purchases table stores "purchase_date" as string "yyyy-MM-dd",
    // change filters to eq("purchase_date", todayStr) / gte-lte on those strings.
    private suspend fun getTodaySales(userId: String, todayStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("purchases")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("purchase_date", todayStr) // <-- adjust if your schema differs
                    }
                }
                .decodeList<Purchase>()

            val total = rows.sumOf { it.totalAmount ?: 0.0 }
            Log.d(TAG, "getTodaySales($todayStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's sales: ${e.message}", e)
            0.0
        }
    }

    private suspend fun getMonthlySales(userId: String, startDateStr: String, endDateStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("purchases")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("purchase_date", startDateStr)
                        lte("purchase_date", endDateStr)
                    }
                }
                .decodeList<Purchase>()

            val total = rows.sumOf { it.totalAmount ?: 0.0 }
            Log.d(TAG, "getMonthlySales($startDateStr..$endDateStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly sales: ${e.message}", e)
            0.0
        }
    }

    private suspend fun getMonthlyPurchases(userId: String, startDateStr: String, endDateStr: String): Double {
        return try {
            val rows = SupabaseManager.client.from("purchases")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("purchase_date", startDateStr)
                        lte("purchase_date", endDateStr)
                    }
                }
                .decodeList<Purchase>()

            val total = rows.sumOf { it.totalAmount ?: 0.0 }
            Log.d(TAG, "getMonthlyPurchases($startDateStr..$endDateStr): $total from ${rows.size} rows")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly purchases: ${e.message}", e)
            0.0
        }
    }

    // ==================== STAFF ====================

    private suspend fun getActiveStaffCount(userId: String): Int {
        return try {
            val result = SupabaseManager.client.from("staff")
                .select {
                    filter {
                        eq("business_owner_id", userId)
                        eq("is_active", true)
                    }
                }
                .decodeList<Staff>()

            result.size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active staff count: ${e.message}", e)
            0
        }
    }

    private suspend fun getMonthlyStaffExpenses(userId: String, startDateStr: String, endDateStr: String): Double {
        return try {
            val staffResult = SupabaseManager.client.from("staff")
                .select {
                    filter {
                        eq("business_owner_id", userId)
                        eq("is_active", true)
                    }
                }
                .decodeList<Staff>()

            val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            var totalExpenses = 0.0

            for (staff in staffResult) {
                when (staff.salaryType) {
                    SalaryType.MONTHLY -> {
                        totalExpenses += staff.salaryAmount ?: 0.0
                    }
                    SalaryType.DAILY -> {
                        val attendanceResult = SupabaseManager.getAttendanceByStaffAndMonth(staff.id, currentMonth)
                        val attendance = attendanceResult.getOrNull() ?: emptyList()
                        val presentDays = attendance.count {
                            it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                        }
                        val halfDays = attendance.count { it.status == AttendanceStatus.HALF_DAY }
                        val workingDays = presentDays + (halfDays * 0.5)
                        totalExpenses += (staff.salaryAmount ?: 0.0) * workingDays
                    }
                }
            }

            totalExpenses
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly staff expenses: ${e.message}", e)
            0.0
        }
    }

    // ==================== LOANS ====================

    private suspend fun getTotalLoansGiven(userId: String): Double {
        return try {
            val result = SupabaseManager.client.from("loans")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_paid", false)
                    }
                }
                .decodeList<Loan>()

            result.sumOf { it.amount ?: 0.0 }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total loans given: ${e.message}", e)
            0.0
        }
    }

    private suspend fun getTotalLoansReceived(userId: String): Double {
        return try {
            // Implement when you have a "received" type or separate table
            0.0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total loans received: ${e.message}", e)
            0.0
        }
    }

    private suspend fun getPendingCollections(userId: String): Double {
        return try {
            val result = SupabaseManager.client.from("loans")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_paid", false)
                    }
                }
                .decodeList<Loan>()

            result.sumOf { it.amount ?: 0.0 }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending collections: ${e.message}", e)
            0.0
        }
    }

    // ==================== CASHBOOK ENTRY MANAGEMENT ====================

    suspend fun addCashbookEntry(entry: CashbookEntry): Result<CashbookEntry> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Adding cashbook entry: ${entry.type} - ${entry.amount}")

                val result = SupabaseManager.client.from("cashbook_entries")
                    .insert(entry) { select() }
                    .decodeSingle<CashbookEntry>()

                Log.d(TAG, "Cashbook entry added successfully: ${result.id}")
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding cashbook entry: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCashbookEntry(entryId: String): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Deleting cashbook entry: $entryId")

                SupabaseManager.client.from("cashbook_entries")
                    .delete { filter { eq("id", entryId) } }

                Log.d(TAG, "Cashbook entry deleted successfully")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cashbook entry: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== PURCHASE MANAGEMENT ====================

    suspend fun addPurchase(purchase: Purchase): Result<Purchase> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Adding purchase: ${purchase.totalAmount}")

                val result = SupabaseManager.client.from("purchases")
                    .insert(purchase) { select() }
                    .decodeSingle<Purchase>()

                Log.d(TAG, "Purchase added successfully: ${result.id}")
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding purchase: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPurchasesByUser(userId: String, limit: Int = 50): Result<List<Purchase>> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Fetching purchases for user: $userId")

                val result = SupabaseManager.client.from("purchases")
                    .select {
                        filter { eq("user_id", userId) }
                        order("purchase_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<Purchase>()

                Log.d(TAG, "Found ${result.size} purchases")
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching purchases: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateLoanStatus(loanId: String, isPaid: Boolean): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Updating loan status: $loanId - paid: $isPaid")

                SupabaseManager.client.from("loans")
                    .update(mapOf("is_paid" to isPaid)) { filter { eq("id", loanId) } }

                Log.d(TAG, "Loan status updated successfully")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating loan status: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getLoansByUser(userId: String): Result<List<Loan>> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Fetching loans for user: $userId")

                val result = SupabaseManager.client.from("loans")
                    .select {
                        filter { eq("user_id", userId) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<Loan>()

                Log.d(TAG, "Found ${result.size} loans")
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching loans: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ---------- Mapping: CashbookEntry -> Transaction ----------

    private fun CashbookEntry.toTransaction(): Transaction {
        val idLong = stableIdFromString(this.id)
        val amt = this.amount ?: 0.0
        val tType = mapTypeToTransactionType(this.type?.toString())
        val title = when {
            !this.description.isNullOrBlank() -> this.description!!
            !this.category.isNullOrBlank() -> this.category!!
            else -> "Cashbook Entry"
        }
        val ts = parseDateMillis(this.date) // from "yyyy-MM-dd"; falls back to now

        return Transaction(
            id = idLong,
            description = title,
            amount = amt,
            type = tType,
            category = this.category ?: "GENERAL",
            timestamp = ts,
            notes = null
        )
    }
}

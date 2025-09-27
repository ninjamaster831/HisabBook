package com.guruyuknow.hisabbook

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

object StatsDatabase {

    // Fetch cashbook entries for a user
    suspend fun getCashbookEntries(userId: String): Result<List<CashbookEntry>> {
        return try {
            withContext(Dispatchers.IO) {
                val entries = SupabaseManager.client.from("cashbook_entries")
                    .select(Columns.ALL) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<CashbookEntry>()
                Result.success(entries)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error fetching cashbook entries", e)
            Result.failure(e)
        }
    }

    // Fetch loans for a user
    suspend fun getLoans(userId: String): Result<List<LoanEntry>> {
        return try {
            withContext(Dispatchers.IO) {
                val loans = SupabaseManager.client.from("loans")
                    .select(Columns.ALL) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<LoanEntry>()
                Result.success(loans)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error fetching loans", e)
            Result.failure(e)
        }
    }

    // Fetch purchases for a user
    suspend fun getPurchases(userId: String): Result<List<PurchaseEntry>> {
        return try {
            withContext(Dispatchers.IO) {
                val purchases = SupabaseManager.client.from("purchases")
                    .select(Columns.ALL) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<PurchaseEntry>()
                Result.success(purchases)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error fetching purchases", e)
            Result.failure(e)
        }
    }

    // Fetch staff entries for a user
    suspend fun getStaff(userId: String): Result<List<StaffEntry>> {
        return try {
            withContext(Dispatchers.IO) {
                val staff = SupabaseManager.client.from("staff")
                    .select(Columns.ALL) {
                        filter {
                            eq("business_owner_id", userId)
                        }
                    }
                    .decodeList<StaffEntry>()
                Result.success(staff)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error fetching staff", e)
            Result.failure(e)
        }
    }

    // Fetch user challenges for a user
    suspend fun getUserChallenges(userId: String): Result<List<UserChallenge>> {
        return try {
            withContext(Dispatchers.IO) {
                val challenges = SupabaseManager.client.from("user_challenges")
                    .select(Columns.ALL) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<UserChallenge>()
                Result.success(challenges)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error fetching user challenges", e)
            Result.failure(e)
        }
    }

    // Insert a new user challenge
    suspend fun insertUserChallenge(challenge: UserChallenge): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseManager.client.from("user_challenges")
                    .insert(challenge)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error inserting user challenge", e)
            Result.failure(e)
        }
    }

    // Update an existing user challenge
    suspend fun updateUserChallenge(challenge: UserChallenge): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseManager.client.from("user_challenges")
                    .update(challenge) {
                        filter {
                            eq("id", challenge.id)
                        }
                    }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error updating user challenge", e)
            Result.failure(e)
        }
    }

    // Fetch user coins
    suspend fun getUserCoins(userId: String): Result<Int> {
        return try {
            withContext(Dispatchers.IO) {
                val user = SupabaseManager.client.from("users")
                    .select(Columns.list("coins")) {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingle<UserCoins>()
                Result.success(user.coins)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error fetching user coins", e)
            Result.failure(e)
        }
    }

    // Update user coins
    suspend fun updateUserCoins(userId: String, coins: Int): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseManager.client.from("users")
                    .update(mapOf("coins" to coins)) {
                        filter {
                            eq("id", userId)
                        }
                    }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("StatsDatabase", "Error updating user coins", e)
            Result.failure(e)
        }
    }
}

@Serializable
data class LoanEntry(
    val id: String,
    val user_id: String,
    val amount: Double,
    val is_paid: Boolean = false,
    val friend_name: String? = null,
    val phone_number: String? = null,
    val notes: String? = null
)

@Serializable
data class PurchaseEntry(
    val id: Long,
    val user_id: String,
    val total_amount: Double,
    val purchase_date: Long,
    val customer_id: Long? = null,
    val notes: String? = null
)

@Serializable
data class StaffEntry(
    val id: String,
    val business_owner_id: String,
    val salary_amount: Double,
    val name: String? = null,
    val phone_number: String? = null,
    val email: String? = null,
    val created_at: String? = null
)

// New data classes for challenges and coins
@Serializable
data class UserChallenge(
    val id: String,
    val challengeId: String,
    val user_id: String,
    val start_date: Long,
    val target_amount: Double,
    var current_progress: Double = 0.0,
    var is_completed: Boolean = false,
    var completed_date: Long? = null,
    val coin_reward: Int,
    val base_amount: Double,
    val category: String? = null
)

@Serializable
data class UserCoins(
    val coins: Int
)
data class StaffMonthlyData(
    val month: String,
    val staffCount: Int,
    val totalSalary: Double
)
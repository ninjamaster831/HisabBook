package com.guruyuknow.hisabbook

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LoanDatabaseManager {

    private const val TAG = "LoanDatabaseManager"

    /**
     * Inserts a loan and returns the inserted row.
     * Make sure your RLS policies allow insert + return.
     */
    suspend fun insertLoan(loan: Loan): Result<Loan> = withContext(Dispatchers.IO) {
        return@withContext try {
            val inserted = SupabaseManager.client
                .from("loans")
                .insert(loan) {
                    // Ask PostgREST to return the inserted row
                    select()
                }
                .decodeSingle<Loan>()

            Result.success(inserted)
        } catch (e: Exception) {
            Log.e(TAG, "insertLoan error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch all loans for the current signed-in user (by user_id).
     */
    suspend fun getAllLoans(): List<Loan> = withContext(Dispatchers.IO) {
        try {
            val currentUser = SupabaseManager.getCurrentUser()
            if (currentUser == null) {
                Log.w(TAG, "getAllLoans: no authenticated user")
                return@withContext emptyList()
            }

            SupabaseManager.client
                .from("loans")
                .select {
                    filter { eq("user_id", currentUser.id ?: "") }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Loan>()
        } catch (e: Exception) {
            Log.e(TAG, "getAllLoans error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Updates a loan (matched by id) and returns the updated row.
     * NOTE: Ensure Loan includes the primary key `id`.
     */
    suspend fun updateLoan(loan: Loan): Result<Loan> = withContext(Dispatchers.IO) {
        return@withContext try {
            val updated = SupabaseManager.client
                .from("loans")
                .update(loan) {
                    filter { eq("id", loan.id) }
                    select() // return updated row
                }
                .decodeSingle<Loan>()

            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "updateLoan error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a loan by id.
     * Returns success if PostgREST completes without throwing.
     */
    suspend fun deleteLoan(loanId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            SupabaseManager.client
                .from("loans")
                .delete {
                    filter { eq("id", loanId) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteLoan error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch a single loan by id (or null if not found).
     */
    suspend fun getLoanById(loanId: String): Loan? = withContext(Dispatchers.IO) {
        try {
            SupabaseManager.client
                .from("loans")
                .select {
                    filter { eq("id", loanId) }
                    limit(1)
                }
                .decodeSingleOrNull<Loan>()
        } catch (e: Exception) {
            Log.e(TAG, "getLoanById error: ${e.message}", e)
            null
        }
    }
}

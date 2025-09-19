package com.guruyuknow.hisabbook.Bills

import android.annotation.SuppressLint
import com.guruyuknow.hisabbook.CashbookEntry
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal

object SupabaseCashbook {

    // reuse the same client with auth/session
    private val client = SupabaseManager.client

    private fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun getEntriesByType(type: String): List<CashbookEntry> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("cashbook_entries")
            .select {
                filter {
                    eq("user_id", uid)   // ✅ only this user’s entries
                    eq("type", type)
                }
                order("date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    suspend fun getCategories(): List<String> {
        return try {
            client.from("categories")
                .select { }
                .decodeList<CategoryRow>()
                .map { it.name }
        } catch (_: Exception) {
            listOf("Utilities","Groceries","Inventory","Supplies","Fuel","Rent","Maintenance","Other")
        }
    }

    suspend fun insertEntry(
        amount: BigDecimal,
        date: String,
        type: String,              // expected "IN" or "OUT"
        paymentMethod: String,     // expected "CASH" or "ONLINE"
        description: String,
        categoryName: String?
    ): Boolean {
        val uid = currentUserId() ?: return false  // ❌ no session → don’t insert

        val safeType = when (type.uppercase()) {
            "IN" -> "IN"
            "OUT" -> "OUT"
            else -> "OUT"
        }

        val safePayment = when (paymentMethod.uppercase()) {
            "CASH" -> "CASH"
            "ONLINE" -> "ONLINE"
            else -> "CASH"
        }

        val body = buildJsonObject {
            put("user_id", uid)  // ✅ send the user id
            put("amount", amount.toPlainString())
            put("date", date)
            put("type", safeType)
            put("payment_method", safePayment)
            if (description.isNotEmpty()) put("description", description)
            if (!categoryName.isNullOrEmpty()) put("category", categoryName)
        }

        return try {
            client.from("cashbook_entries").insert(body)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    private data class CategoryRow(val id: String, val name: String)
}

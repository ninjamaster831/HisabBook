package com.guruyuknow.hisabbook.Bills

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.guruyuknow.hisabbook.CashbookEntry
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.util.UUID

object SupabaseCashbook {

    private const val TAG = "SupabaseCashbook"
    private val client = SupabaseManager.client
    private fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    // ---------------- Existing public APIs (kept) ----------------

    suspend fun getEntriesByType(type: String): List<CashbookEntry> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("cashbook_entries")
            .select {
                filter {
                    eq("user_id", uid)
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

    suspend fun insertEntryWithBill(
        amount: BigDecimal,
        date: String,
        type: String,
        paymentMethod: String,
        description: String,
        categoryName: String?,
        context: Context,
        imageUri: Uri? = null,
        extractedText: String? = null,
        extractedAmount: Double? = null,
        confidenceScore: Double? = null
    ): Boolean {
        val uid = currentUserId() ?: return false

        val safeType = when (type.uppercase()) { "IN" -> "IN"; "OUT" -> "OUT"; else -> "OUT" }
        val safePayment = when (paymentMethod.uppercase()) {
            "CASH","UPI","CARD","WALLET","BANK" -> paymentMethod.uppercase()
            else -> "CASH"
        }

        val chosenCategoryName = categoryName?.trim().orEmpty()
        if (safeType == "OUT" && chosenCategoryName.isEmpty()) {
            throw IllegalArgumentException("Please select a category for expense entries.")
        }
        val categoryIdResolved = if (chosenCategoryName.isNotEmpty()) {
            resolveCategoryIdByName(chosenCategoryName)
        } else null

        return try {
            // Insert into cashbook_entries (select() to surface errors)
            val cashbookBody = buildJsonObject {
                put("user_id", uid)
                put("amount", amount.toPlainString())
                put("date", date)
                put("type", safeType)
                put("payment_method", safePayment)
                if (description.isNotEmpty()) put("description", description)
                if (categoryIdResolved != null) put("category_id", categoryIdResolved)
                else if (chosenCategoryName.isNotEmpty()) put("category", chosenCategoryName)
            }

            val insertedEntries = client.from("cashbook_entries")
                .insert(cashbookBody) { select() }
                .decodeList<CashbookEntry>()

            val cashbookEntryId = insertedEntries.firstOrNull()?.id
            if (cashbookEntryId == null) {
                Log.w(TAG, "cashbook_entries insert returned no rows")
                return false
            }

            // If image provided → upload to storage + insert into bills
            if (imageUri != null) {
                val ok = uploadBillImageAndInsertRow(
                    context = context,
                    imageUri = imageUri,
                    cashbookEntryId = cashbookEntryId,
                    extractedText = extractedText,
                    extractedAmount = extractedAmount,
                    confidenceScore = confidenceScore
                )
                if (!ok) return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "insertEntryWithBill failed", e)
            false
        }
    }

    suspend fun insertEntry(
        amount: BigDecimal,
        date: String,
        type: String,
        paymentMethod: String,
        description: String,
        categoryName: String?
    ): Boolean {
        val uid = currentUserId() ?: return false

        val safeType = when (type.uppercase()) { "IN" -> "IN"; "OUT" -> "OUT"; else -> "OUT" }
        val safePayment = when (paymentMethod.uppercase()) {
            "CASH","UPI","CARD","WALLET","BANK" -> paymentMethod.uppercase()
            else -> "CASH"
        }

        val chosenCategoryName = categoryName?.trim().orEmpty()
        if (safeType == "OUT" && chosenCategoryName.isEmpty()) {
            throw IllegalArgumentException("Please select a category for expense entries.")
        }
        val categoryIdResolved = if (chosenCategoryName.isNotEmpty()) {
            resolveCategoryIdByName(chosenCategoryName)
        } else null

        val body = buildJsonObject {
            put("user_id", uid)
            put("amount", amount.toPlainString())
            put("date", date)
            put("type", safeType)
            put("payment_method", safePayment)
            if (description.isNotEmpty()) put("description", description)
            if (categoryIdResolved != null) put("category_id", categoryIdResolved)
            else if (chosenCategoryName.isNotEmpty()) put("category", chosenCategoryName)
        }

        return try {
            client.from("cashbook_entries").insert(body) { select() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "insertEntry failed", e)
            false
        }
    }

    suspend fun getBillsForEntry(cashbookEntryId: String): List<Bill> {
        val uid = currentUserId() ?: return emptyList()
        return try {
            client.from("bills")
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("cashbook_entry_id", cashbookEntryId)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList()
        } catch (e: Exception) {
            Log.e(TAG, "getBillsForEntry failed", e)
            emptyList()
        }
    }

    suspend fun getAllBills(): List<Bill> {
        val uid = currentUserId() ?: return emptyList()
        return try {
            client.from("bills")
                .select {
                    filter { eq("user_id", uid) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList()
        } catch (e: Exception) {
            Log.e(TAG, "getAllBills failed", e)
            emptyList()
        }
    }

    // ---------------- NEW: Bills listing with join ----------------

    @Serializable
    data class CashbookEntryPartial(
        val id: String? = null,
        @SerialName("amount")
        val amount: Double? = null,  // Changed: Now accepts Double directly from DB
        val type: String? = null,
        @SerialName("payment_method") val paymentMethod: String? = null,
        val date: String? = null,
        val category: String? = null,
        val description: String? = null
    )

    @Serializable
    data class BillWithEntry(
        val id: String,
        @SerialName("image_url") val imageUrl: String? = null,
        @SerialName("image_name") val imageName: String? = null,
        @SerialName("extracted_amount") val extractedAmount: Double? = null,
        @SerialName("extracted_text") val extractedText: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("cashbook_entries") val entry: CashbookEntryPartial? = null
    )

    /**
     * Returns bills joined with cashbook_entries and filtered by entry type (IN/OUT).
     */
    suspend fun getBillsByType(type: String): List<BillWithEntry> {
        val uid = currentUserId() ?: return emptyList()
        return try {
            val result = client.from("bills").select(
                columns = Columns.raw(
                    "id,image_url,image_name,extracted_amount,extracted_text,created_at," +
                            "cashbook_entries!inner(id,amount,type,payment_method,date,category,description)"
                )
            ) {
                filter {
                    eq("user_id", uid)
                    eq("cashbook_entries.type", type.uppercase())
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<BillWithEntry>()

            // Log for debugging
            Log.d(TAG, "getBillsByType($type): Found ${result.size} bills")
            result.firstOrNull()?.let {
                Log.d(TAG, "First bill amount: ${it.entry?.amount}")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "getBillsByType failed", e)
            emptyList()
        }
    }

    suspend fun getAllBillsWithEntry(): List<BillWithEntry> {
        val uid = currentUserId() ?: return emptyList()
        return try {
            client.from("bills").select(
                columns = Columns.raw(
                    "id,image_url,image_name,extracted_amount,extracted_text,created_at," +
                            "cashbook_entries!inner(id,amount,type,payment_method,date,category,description)"
                )
            ) {
                filter { eq("user_id", uid) }
                order("created_at", Order.DESCENDING)
            }.decodeList()
        } catch (e: Exception) {
            Log.e(TAG, "getAllBillsWithEntry failed", e)
            emptyList()
        }
    }

    // ---------------- Private helpers ----------------

    private suspend fun uploadBillImageAndInsertRow(
        context: Context,
        imageUri: Uri,
        cashbookEntryId: String,
        extractedText: String?,
        extractedAmount: Double?,
        confidenceScore: Double?
    ): Boolean {
        return try {
            val uid = currentUserId() ?: return false
            val fileName = "${UUID.randomUUID()}.jpg"
            val filePath = "$uid/$fileName"

            val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: return false

            val bucket = client.storage.from("bills")
            bucket.upload(filePath, imageBytes, upsert = false)
            val imageUrl = bucket.publicUrl(filePath)

            val billBody = buildJsonObject {
                put("user_id", uid)
                put("cashbook_entry_id", cashbookEntryId)
                put("image_url", imageUrl)
                put("image_name", fileName)
                extractedText?.let { put("extracted_text", it) }
                extractedAmount?.let { put("extracted_amount", it) }
                confidenceScore?.let { put("confidence_score", it) }
            }

            val inserted = client.from("bills")
                .insert(billBody) { select() }
                .decodeList<Bill>()
            val ok = inserted.isNotEmpty()
            if (!ok) Log.w(TAG, "bills insert returned empty list")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "uploadBillImageAndInsertRow failed", e)
            false
        }
    }

    private suspend fun resolveCategoryIdByName(name: String): String? {
        return try {
            client.from("categories")
                .select {
                    filter { ilike("name", name) }
                    limit(1)
                }
                .decodeList<CategoryRow>()
                .firstOrNull()
                ?.id
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Serializable private data class CategoryRow(val id: String, val name: String)
}
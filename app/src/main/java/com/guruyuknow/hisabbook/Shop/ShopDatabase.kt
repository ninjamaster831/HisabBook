package com.guruyuknow.hisabbook.Shop

import android.util.Log
import com.guruyuknow.hisabbook.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerInsert(
    val name: String,
    val phone: String? = null,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("user_id")
    val userId: String
)

@Serializable
data class PurchaseInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("customer_id") val customerId: Long,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("purchase_date") val purchaseDate: Long,
    val notes: String? = null
)

@Serializable
data class PurchaseItemInsert(
    @SerialName("purchase_id") val purchaseId: Long,
    @SerialName("item_name") val itemName: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double
)

@Serializable
data class PaymentInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("customer_id") val customerId: Long,
    val amount: Double,
    @SerialName("payment_date") val paymentDate: Long,
    @SerialName("payment_method") val paymentMethod: String,
    val notes: String? = null
)

object ShopDatabase {
    private const val TAG = "ShopDatabase"

    // ---- Helpers ----
    private fun currentUserIdOrThrow(): String {
        return try {
            val userId = SupabaseManager.client.auth.currentUserOrNull()?.id
            Log.d(TAG, "Current user ID: $userId")
            userId ?: error("No authenticated user")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}", e)
            throw e
        }
    }

    // ---- Inserts ----

    suspend fun insertCustomer(customer: Customer): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting insertCustomer for: ${customer.name}")
            val userId = currentUserIdOrThrow()

            Log.d(TAG, "Customer data - name: ${customer.name}, phone: ${customer.phone}, createdAt: ${customer.createdAt}")

            val insertData = CustomerInsert(
                name = customer.name,
                phone = customer.phone,
                createdAt = customer.createdAt,
                userId = userId
            )
            Log.d(TAG, "Insert data prepared: $insertData")

            Log.d(TAG, "Calling Supabase insert...")
            val inserted = SupabaseManager.client.from("customers")
                .insert(insertData) {
                    select() // return inserted row
                }
                .decodeSingle<Customer>()

            Log.d(TAG, "Customer inserted successfully - ID: ${inserted.id}, name: ${inserted.name}")
            inserted.id

        } catch (e: Exception) {
            Log.e(TAG, "Error inserting customer: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            if (e.cause != null) {
                Log.e(TAG, "Caused by: ${e.cause?.message}", e.cause)
            }
            throw e
        }
    }
// ---- Deletes ----

    suspend fun deleteCustomer(customerId: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting deleteCustomer - customerId: $customerId")
            val userId = currentUserIdOrThrow()

            // 1) Get all purchase IDs for this customer (scoped to current user)
            val purchaseIds: List<Long> = SupabaseManager.client.from("purchases")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                    filter {
                        eq("user_id", userId)
                        eq("customer_id", customerId)
                    }
                }
                .decodeList<Map<String, Long>>()
                .mapNotNull { it["id"] }

            Log.d(TAG, "Found ${purchaseIds.size} purchases for deletion")

            // 2) Delete purchase_items for each purchase (loop; no 'in_' / 'or' helpers needed)
            for ((index, pid) in purchaseIds.withIndex()) {
                Log.d(TAG, "Deleting purchase_items for purchase[$index]=$pid")
                SupabaseManager.client.from("purchase_items")
                    .delete {
                        filter { eq("purchase_id", pid) }
                    }
            }

            // 3) Delete payments for this customer
            Log.d(TAG, "Deleting payments for customerId=$customerId")
            SupabaseManager.client.from("payments")
                .delete {
                    filter {
                        eq("user_id", userId)
                        eq("customer_id", customerId)
                    }
                }

            // 4) Delete purchases for this customer
            Log.d(TAG, "Deleting purchases for customerId=$customerId")
            SupabaseManager.client.from("purchases")
                .delete {
                    filter {
                        eq("user_id", userId)
                        eq("customer_id", customerId)
                    }
                }

            // 5) Finally, delete the customer row
            Log.d(TAG, "Deleting customer row id=$customerId")
            SupabaseManager.client.from("customers")
                .delete {
                    filter {
                        eq("user_id", userId)
                        eq("id", customerId)
                    }
                }

            Log.d(TAG, "deleteCustomer completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting customer: ${e.message}", e)
            throw e
        }
    }

    suspend fun insertPurchase(purchase: Purchase): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting insertPurchase - customerId: ${purchase.customerId}, totalAmount: ${purchase.totalAmount}")
            val userId = currentUserIdOrThrow()

            // 1) Insert purchase, read back id
            Log.d(TAG, "Inserting purchase record...")
            val purchaseData = PurchaseInsert(
                userId = userId,
                customerId = purchase.customerId,
                totalAmount = purchase.totalAmount,
                purchaseDate = purchase.purchaseDate,
                notes = purchase.notes
            )
            Log.d(TAG, "Purchase insert data: $purchaseData")

            val insertedPurchase = SupabaseManager.client.from("purchases")
                .insert(purchaseData) {
                    select()
                }
                .decodeSingle<Purchase>()

            val purchaseId = insertedPurchase.id
            Log.d(TAG, "Purchase inserted successfully - ID: $purchaseId")

            // 2) Insert items (one by one to keep it simple & safe)
            Log.d(TAG, "Inserting ${purchase.items.size} purchase items...")
            purchase.items.forEachIndexed { index, item ->
                try {
                    Log.d(TAG, "Inserting item $index: ${item.itemName} (qty: ${item.quantity})")
                    val itemData = PurchaseItemInsert(
                        purchaseId = purchaseId,
                        itemName = item.itemName,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice
                    )
                    Log.d(TAG, "Item insert data: $itemData")

                    SupabaseManager.client.from("purchase_items")
                        .insert(itemData)

                    Log.d(TAG, "Item $index inserted successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error inserting item $index (${item.itemName}): ${e.message}", e)
                    throw e
                }
            }

            Log.d(TAG, "Purchase and all items inserted successfully - Purchase ID: $purchaseId")
            purchaseId

        } catch (e: Exception) {
            Log.e(TAG, "Error inserting purchase: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    suspend fun insertPayment(payment: Payment): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting insertPayment - customerId: ${payment.customerId}, amount: ${payment.amount}")
            val userId = currentUserIdOrThrow()

            val paymentData = PaymentInsert(
                userId = userId,
                customerId = payment.customerId,
                amount = payment.amount,
                paymentDate = payment.paymentDate,
                paymentMethod = payment.paymentMethod.name, // This becomes a string in the database
                notes = payment.notes
            )
            Log.d(TAG, "Payment insert data: $paymentData")

            val inserted = SupabaseManager.client.from("payments")
                .insert(paymentData) {
                    select()
                }
                .decodeSingle<Payment>()

            Log.d(TAG, "Payment inserted successfully - ID: ${inserted.id}")
            inserted.id

        } catch (e: Exception) {
            Log.e(TAG, "Error inserting payment: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    // ---- Reads ----

    suspend fun getCustomers(): List<Customer> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting getCustomers")
            val userId = currentUserIdOrThrow()

            Log.d(TAG, "Querying customers for user: $userId")
            val customers = SupabaseManager.client.from("customers")
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Customer>()

            Log.d(TAG, "Retrieved ${customers.size} customers")
            customers.forEachIndexed { index, customer ->
                Log.d(TAG, "Customer $index: ID=${customer.id}, name=${customer.name}, phone=${customer.phone}")
            }
            customers

        } catch (e: Exception) {
            Log.e(TAG, "Error getting customers: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    suspend fun getCustomerById(customerId: Long): Customer? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting getCustomerById - ID: $customerId")
            val userId = currentUserIdOrThrow()

            Log.d(TAG, "Querying customer by ID: $customerId for user: $userId")
            val customer = SupabaseManager.client.from("customers")
                .select {
                    filter {
                        eq("id", customerId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<Customer>()

            if (customer != null) {
                Log.d(TAG, "Customer found: ID=${customer.id}, name=${customer.name}")
            } else {
                Log.d(TAG, "No customer found with ID: $customerId")
            }
            customer

        } catch (e: Exception) {
            Log.e(TAG, "Error getting customer by ID: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    suspend fun getPurchasesByCustomer(customerId: Long): List<Purchase> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting getPurchasesByCustomer - customerId: $customerId")
            val userId = currentUserIdOrThrow()

            // 1) Fetch purchases
            Log.d(TAG, "Fetching purchases for customer: $customerId")
            val purchases = SupabaseManager.client.from("purchases")
                .select {
                    filter {
                        eq("customer_id", customerId)
                        eq("user_id", userId)
                    }
                    order("purchase_date", Order.DESCENDING)
                }
                .decodeList<Purchase>()
                .toMutableList() // so we can reassign items via copy()

            Log.d(TAG, "Found ${purchases.size} purchases for customer $customerId")

            // 2) For each purchase, fetch its items and REPLACE the list
            purchases.forEachIndexed { idx, p ->
                try {
                    Log.d(TAG, "Fetching items for purchase ${p.id} (index $idx)")
                    val items = SupabaseManager.client.from("purchase_items")
                        .select {
                            filter { eq("purchase_id", p.id) }
                            order("id", Order.ASCENDING)
                        }
                        .decodeList<PurchaseItem>()

                    Log.d(TAG, "Found ${items.size} items for purchase ${p.id}")
                    purchases[idx] = p.copy(items = items)   // <-- key change (no clear/addAll)

                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching items for purchase ${p.id}: ${e.message}", e)
                    throw e
                }
            }

            Log.d(TAG, "Successfully loaded ${purchases.size} purchases with items")
            purchases

        } catch (e: Exception) {
            Log.e(TAG, "Error getting purchases by customer: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    suspend fun getPaymentsByCustomer(customerId: Long): List<Payment> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting getPaymentsByCustomer - customerId: $customerId")
            val userId = currentUserIdOrThrow()

            Log.d(TAG, "Querying payments for customer: $customerId")
            val payments = SupabaseManager.client.from("payments")
                .select {
                    filter {
                        eq("customer_id", customerId)
                        eq("user_id", userId)
                    }
                    order("payment_date", Order.DESCENDING)
                }
                .decodeList<Payment>()

            Log.d(TAG, "Found ${payments.size} payments for customer $customerId")
            payments.forEachIndexed { index, payment ->
                Log.d(TAG, "Payment $index: ID=${payment.id}, amount=${payment.amount}, method=${payment.paymentMethod}")
            }
            payments

        } catch (e: Exception) {
            Log.e(TAG, "Error getting payments by customer: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    // ---- Aggregated/Summary APIs ----

    suspend fun getCustomerSummaries(): List<CustomerSummary> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting getCustomerSummaries")
            val customers = getCustomers()
            val result = mutableListOf<CustomerSummary>()

            Log.d(TAG, "Processing summaries for ${customers.size} customers")
            for (c in customers) {
                try {
                    Log.d(TAG, "Processing summary for customer: ${c.name} (ID: ${c.id})")
                    val purchases = getPurchasesByCustomer(c.id)
                    val payments = getPaymentsByCustomer(c.id)

                    val totalPurchased = purchases.sumOf { it.totalAmount }
                    val totalPaid = payments.sumOf { it.amount }
                    val pendingBalance = totalPurchased - totalPaid
                    val lastPurchaseDate = purchases.maxOfOrNull { it.purchaseDate }

                    Log.d(TAG, "Customer ${c.name}: purchased=₹$totalPurchased, paid=₹$totalPaid, balance=₹$pendingBalance")

                    result.add(
                        CustomerSummary(
                            customerId = c.id,
                            customerName = c.name,
                            customerPhone = c.phone,
                            totalPurchased = totalPurchased,
                            totalPaid = totalPaid,
                            pendingBalance = pendingBalance,
                            lastPurchaseDate = lastPurchaseDate,
                            purchaseCount = purchases.size
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing summary for customer ${c.name}: ${e.message}", e)
                    throw e
                }
            }

            // Highest pending balance first (your original behavior)
            val sortedResult = result.sortedByDescending { it.pendingBalance }
            Log.d(TAG, "Customer summaries completed - ${sortedResult.size} summaries generated")
            sortedResult

        } catch (e: Exception) {
            Log.e(TAG, "Error getting customer summaries: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }

    suspend fun getCustomerDetail(customerId: Long): CustomerDetail? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting getCustomerDetail - customerId: $customerId")

            val customer = getCustomerById(customerId) ?: run {
                Log.d(TAG, "Customer not found with ID: $customerId")
                return@withContext null
            }

            Log.d(TAG, "Customer found: ${customer.name}, fetching purchases and payments...")
            val purchases = getPurchasesByCustomer(customerId)
            val payments = getPaymentsByCustomer(customerId)

            val totalPurchased = purchases.sumOf { it.totalAmount }
            val totalPaid = payments.sumOf { it.amount }
            val pendingBalance = totalPurchased - totalPaid
            val lastPurchaseDate = purchases.maxOfOrNull { it.purchaseDate }

            Log.d(TAG, "Customer detail calculated - purchases: ${purchases.size}, payments: ${payments.size}")
            Log.d(TAG, "Totals - purchased: ₹$totalPurchased, paid: ₹$totalPaid, balance: ₹$pendingBalance")

            val summary = CustomerSummary(
                customerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phone,
                totalPurchased = totalPurchased,
                totalPaid = totalPaid,
                pendingBalance = pendingBalance,
                lastPurchaseDate = lastPurchaseDate,
                purchaseCount = purchases.size
            )

            val customerDetail = CustomerDetail(customer, purchases, payments, summary)
            Log.d(TAG, "Customer detail completed successfully")
            customerDetail

        } catch (e: Exception) {
            Log.e(TAG, "Error getting customer detail: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            throw e
        }
    }
}
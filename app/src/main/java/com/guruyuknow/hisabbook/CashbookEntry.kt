package com.guruyuknow.hisabbook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CashbookEntry(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val type: String, // "IN" or "OUT"
    @SerialName("payment_method") val paymentMethod: String, // "CASH" or "ONLINE"
    val description: String? = null,
    val category: String? = null, // allow null safely
    val date: String, // ISO date string yyyy-MM-dd
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("payment_method_id") val paymentMethodId: String? = null
) {
    init {
        require(amount > 0) { "Amount must be greater than 0" }
        require(type in listOf("IN", "OUT")) { "Type must be 'IN' or 'OUT'" }
        // No require() on category/categoryId because your UI allows it to be empty.
    }

    companion object {
        const val TYPE_IN = "IN"
        const val TYPE_OUT = "OUT"
        const val PAYMENT_METHOD_CASH = "CASH"
        const val PAYMENT_METHOD_ONLINE = "ONLINE"
    }
}

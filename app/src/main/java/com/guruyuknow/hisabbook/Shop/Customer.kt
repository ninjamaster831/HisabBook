package com.guruyuknow.hisabbook.Shop

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Customer(
    val id: Long = 0,
    val name: String,
    val phone: String? = null,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("user_id")
    val userId: String = "", // This matches your database schema
    @SerialName("updated_at")
    val updatedAt: String? = null // Add this field from your schema
) {
    fun getFormattedCreatedDate(): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return dateFormat.format(Date(createdAt))
    }
}

@Serializable
data class Purchase(
    val id: Long = 0,
    @SerialName("user_id")
    val userId: String = "",
    @SerialName("customer_id")
    val customerId: Long,
    val items: List<PurchaseItem> = emptyList(), // Default to empty list - not stored in purchases table
    @SerialName("total_amount")
    val totalAmount: Double, // DECIMAL(12,2) in database
    @SerialName("purchase_date")
    val purchaseDate: Long = System.currentTimeMillis(),
    val notes: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null, // TIMESTAMP from database
    @SerialName("updated_at")
    val updatedAt: String? = null // TIMESTAMP from database
) {
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return dateFormat.format(Date(purchaseDate))
    }

    fun getFormattedAmount(): String {
        return "₹${String.format("%.2f", totalAmount)}"
    }
}

@Serializable
data class PurchaseItem(
    val id: Long = 0,
    @SerialName("purchase_id")
    val purchaseId: Long = 0,
    @SerialName("item_name")
    val itemName: String,
    val quantity: Int,
    @SerialName("unit_price")
    val unitPrice: Double, // DECIMAL(10,2) in database
    @SerialName("total_price")
    val totalPrice: Double, // DECIMAL(12,2) in database - calculated by trigger
    @SerialName("created_at")
    val createdAt: String? = null // TIMESTAMP from database
) {
    fun getFormattedUnitPrice(): String {
        return "₹${String.format("%.2f", unitPrice)}"
    }

    fun getFormattedTotalPrice(): String {
        return "₹${String.format("%.2f", totalPrice)}"
    }
}

@Serializable
data class Payment(
    val id: Long = 0,
    @SerialName("user_id")
    val userId: String = "",
    @SerialName("customer_id")
    val customerId: Long,
    val amount: Double, // DECIMAL(12,2) in database
    @SerialName("payment_date")
    val paymentDate: Long = System.currentTimeMillis(),
    @SerialName("payment_method")
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null, // TIMESTAMP from database
    @SerialName("updated_at")
    val updatedAt: String? = null // TIMESTAMP from database
) {
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return dateFormat.format(Date(paymentDate))
    }

    fun getFormattedAmount(): String {
        return "₹${String.format("%.2f", amount)}"
    }
}

@Serializable
enum class PaymentMethod(val displayName: String) {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card"),
    BANK_TRANSFER("Bank Transfer"),
    CHEQUE("Cheque")
}

@Serializable
data class CustomerSummary(
    val customerId: Long,
    val customerName: String,
    val customerPhone: String? = null,
    val totalPurchased: Double,
    val totalPaid: Double,
    val pendingBalance: Double,
    val lastPurchaseDate: Long? = null,
    val purchaseCount: Int
) {
    fun getFormattedTotalPurchased(): String {
        return "₹${String.format("%.2f", totalPurchased)}"
    }

    fun getFormattedTotalPaid(): String {
        return "₹${String.format("%.2f", totalPaid)}"
    }

    fun getFormattedPendingBalance(): String {
        return "₹${String.format("%.2f", pendingBalance)}"
    }

    fun getLastPurchaseFormatted(): String {
        return if (lastPurchaseDate != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            dateFormat.format(Date(lastPurchaseDate))
        } else {
            "No purchases"
        }
    }

    fun getBalanceStatus(): BalanceStatus {
        return when {
            pendingBalance > 0 -> BalanceStatus.PENDING
            pendingBalance < 0 -> BalanceStatus.OVERPAID
            else -> BalanceStatus.CLEARED
        }
    }
}

@Serializable
enum class BalanceStatus(val displayName: String, val colorRes: Int) {
    PENDING("Pending", android.graphics.Color.parseColor("#F44336")),
    CLEARED("Cleared", android.graphics.Color.parseColor("#4CAF50")),
    OVERPAID("Overpaid", android.graphics.Color.parseColor("#2196F3"))
}

@Serializable
data class CustomerDetail(
    val customer: Customer,
    val purchases: List<Purchase>,
    val payments: List<Payment>,
    val summary: CustomerSummary
)
package com.guruyuknow.hisabbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.Shop.Payment
import com.guruyuknow.hisabbook.Shop.Purchase
import com.guruyuknow.hisabbook.Shop.PurchaseItem

class PurchaseItemsAdapter(
    private val items: MutableList<PurchaseItem>,
    private val onRemoveClick: (PurchaseItem) -> Unit
) : RecyclerView.Adapter<PurchaseItemsAdapter.ItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_purchase_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemName: TextView = itemView.findViewById(R.id.itemName)
        private val itemQuantity: TextView = itemView.findViewById(R.id.itemQuantity)
        private val itemUnitPrice: TextView = itemView.findViewById(R.id.itemUnitPrice)
        private val itemTotalPrice: TextView = itemView.findViewById(R.id.itemTotalPrice)
        private val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)

        fun bind(item: PurchaseItem) {
            itemName.text = item.itemName
            itemQuantity.text = "Qty: ${item.quantity}"
            itemUnitPrice.text = "@ ${item.getFormattedUnitPrice()}"
            itemTotalPrice.text = item.getFormattedTotalPrice()

            removeButton.setOnClickListener {
                onRemoveClick(item)
            }
        }
    }
}

class PurchasesAdapter(
    private var purchases: List<Purchase>
) : RecyclerView.Adapter<PurchasesAdapter.PurchaseViewHolder>() {

    fun updateData(newPurchases: List<Purchase>) {
        purchases = newPurchases
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurchaseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_purchase, parent, false)
        return PurchaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PurchaseViewHolder, position: Int) {
        holder.bind(purchases[position])
    }

    override fun getItemCount(): Int = purchases.size

    inner class PurchaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val purchaseDate: TextView = itemView.findViewById(R.id.purchaseDate)
        private val purchaseAmount: TextView = itemView.findViewById(R.id.purchaseAmount)
        private val itemsCount: TextView = itemView.findViewById(R.id.itemsCount)
        private val itemsList: TextView = itemView.findViewById(R.id.itemsList)
        private val purchaseNotes: TextView = itemView.findViewById(R.id.purchaseNotes)

        fun bind(purchase: Purchase) {
            purchaseDate.text = purchase.getFormattedDate()
            purchaseAmount.text = purchase.getFormattedAmount()
            itemsCount.text = "${purchase.items.size} items"

            // Show first few items
            val itemsText = purchase.items.take(3).joinToString(", ") {
                "${it.itemName} (${it.quantity})"
            }
            val moreItemsText = if (purchase.items.size > 3) " +${purchase.items.size - 3} more" else ""
            itemsList.text = itemsText + moreItemsText

            if (!purchase.notes.isNullOrEmpty()) {
                purchaseNotes.visibility = View.VISIBLE
                purchaseNotes.text = "Note: ${purchase.notes}"
            } else {
                purchaseNotes.visibility = View.GONE
            }
        }
    }
}

class PaymentsAdapter(
    private var payments: List<Payment>
) : RecyclerView.Adapter<PaymentsAdapter.PaymentViewHolder>() {

    fun updateData(newPayments: List<Payment>) {
        payments = newPayments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(payments[position])
    }

    override fun getItemCount(): Int = payments.size

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val paymentDate: TextView = itemView.findViewById(R.id.paymentDate)
        private val paymentAmount: TextView = itemView.findViewById(R.id.paymentAmount)
        private val paymentMethod: TextView = itemView.findViewById(R.id.paymentMethod)
        private val paymentNotes: TextView = itemView.findViewById(R.id.paymentNotes)

        fun bind(payment: Payment) {
            paymentDate.text = payment.getFormattedDate()
            paymentAmount.text = payment.getFormattedAmount()
            paymentMethod.text = payment.paymentMethod.displayName

            if (!payment.notes.isNullOrEmpty()) {
                paymentNotes.visibility = View.VISIBLE
                paymentNotes.text = "Note: ${payment.notes}"
            } else {
                paymentNotes.visibility = View.GONE
            }
        }
    }
}
package com.guruyuknow.hisabbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.guruyuknow.hisabbook.Shop.BalanceStatus
import com.guruyuknow.hisabbook.Shop.CustomerSummary

class CustomersAdapter(
    private val customers: List<CustomerSummary>,
    private val onCustomerClick: (CustomerSummary) -> Unit,
    private val onAddPurchaseClick: (CustomerSummary) -> Unit
) : RecyclerView.Adapter<CustomersAdapter.CustomerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        holder.bind(customers[position])
    }

    override fun getItemCount(): Int = customers.size

    inner class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val customerCard: MaterialCardView = itemView.findViewById(R.id.customerCard)
        private val customerName: TextView = itemView.findViewById(R.id.customerName)
        private val customerPhone: TextView = itemView.findViewById(R.id.customerPhone)
        private val totalPurchased: TextView = itemView.findViewById(R.id.totalPurchased)
        private val totalPaid: TextView = itemView.findViewById(R.id.totalPaid)
        private val pendingBalance: TextView = itemView.findViewById(R.id.pendingBalance)
        private val balanceStatus: TextView = itemView.findViewById(R.id.balanceStatus)
        private val lastPurchase: TextView = itemView.findViewById(R.id.lastPurchase)
        private val purchaseCount: TextView = itemView.findViewById(R.id.purchaseCount)
        private val addPurchaseButton: MaterialButton = itemView.findViewById(R.id.addPurchaseButton)

        fun bind(customer: CustomerSummary) {
            customerName.text = customer.customerName

            if (!customer.customerPhone.isNullOrEmpty()) {
                customerPhone.visibility = View.VISIBLE
                customerPhone.text = customer.customerPhone
            } else {
                customerPhone.visibility = View.GONE
            }

            totalPurchased.text = "Purchased: ${customer.getFormattedTotalPurchased()}"
            totalPaid.text = "Paid: ${customer.getFormattedTotalPaid()}"
            pendingBalance.text = customer.getFormattedPendingBalance()

            val status = customer.getBalanceStatus()
            balanceStatus.text = status.displayName
            balanceStatus.setTextColor(status.colorRes)
            pendingBalance.setTextColor(status.colorRes)

            lastPurchase.text = "Last purchase: ${customer.getLastPurchaseFormatted()}"
            purchaseCount.text = "${customer.purchaseCount} purchases"

            // Set card background based on balance status
            when (status) {
                BalanceStatus.PENDING -> {
                    customerCard.setCardBackgroundColor(
                        itemView.context.getColor(android.R.color.white)
                    )
                    customerCard.strokeColor = status.colorRes
                    customerCard.strokeWidth = 2
                }
                BalanceStatus.CLEARED -> {
                    customerCard.setCardBackgroundColor(
                        itemView.context.getColor(android.R.color.white)
                    )
                    customerCard.strokeColor = status.colorRes
                    customerCard.strokeWidth = 1
                }
                BalanceStatus.OVERPAID -> {
                    customerCard.setCardBackgroundColor(
                        itemView.context.getColor(android.R.color.white)
                    )
                    customerCard.strokeColor = status.colorRes
                    customerCard.strokeWidth = 2
                }
            }

            customerCard.setOnClickListener {
                onCustomerClick(customer)
            }

            addPurchaseButton.setOnClickListener {
                onAddPurchaseClick(customer)
            }
        }
    }
}
package com.guruyuknow.hisabbook

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.databinding.ItemRecentTransactionBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class RecentTransactionsAdapter(
    private val onItemClick: (Transaction) -> Unit
) : ListAdapter<Transaction, RecentTransactionsAdapter.TransactionViewHolder>(DiffCallback) {

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemRecentTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TransactionViewHolder(
        private val binding: ItemRecentTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            binding.apply {
                // Set transaction details
                transactionTitle.text = transaction.description
                transactionAmount.text = numberFormat.format(transaction.amount)
                transactionDate.text = dateFormat.format(Date(transaction.timestamp))
                transactionCategory.text = transaction.category

                // Set amount color and icon based on transaction type
                when (transaction.type) {
                    TransactionType.INCOME, TransactionType.SALE -> {
                        transactionAmount.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.success_green)
                        )
                        transactionIcon.setImageResource(R.drawable.ic_arrow_up)
                        transactionIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.success_green)
                        )
                    }
                    TransactionType.EXPENSE, TransactionType.PURCHASE -> {
                        transactionAmount.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.error_red)
                        )
                        transactionIcon.setImageResource(R.drawable.ic_arrow_down)
                        transactionIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.error_red)
                        )
                    }
                    TransactionType.LOAN_GIVEN -> {
                        transactionAmount.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.warning_orange)
                        )
                        transactionIcon.setImageResource(R.drawable.ic_arrow_up_right)
                        transactionIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.warning_orange)
                        )
                    }
                    TransactionType.LOAN_RECEIVED -> {
                        transactionAmount.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.info_blue)
                        )
                        transactionIcon.setImageResource(R.drawable.ic_arrow_down_left)
                        transactionIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, R.color.info_blue)
                        )
                    }
                }

                // Set click listener
                root.setOnClickListener {
                    onItemClick(transaction)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
            return oldItem == newItem
        }
    }
}

// Data classes for your transactions
data class Transaction(
    val id: Long,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val notes: String? = null
)

enum class TransactionType {
    INCOME,
    EXPENSE,
    SALE,
    PURCHASE,
    LOAN_GIVEN,
    LOAN_RECEIVED
}
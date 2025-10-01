package com.guruyuknow.hisabbook

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.databinding.ItemEventExpenseBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class EventExpenseAdapter(
    private val onDeleteClick: (EventExpense) -> Unit
) : ListAdapter<EventExpense, EventExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemEventExpenseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExpenseViewHolder(
        private val binding: ItemEventExpenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: EventExpense) {
            binding.tvExpenseTitle.text = expense.title
            binding.tvAmount.text = currencyFormat.format(expense.amount)
            binding.tvCategory.text = expense.category
            binding.tvDate.text = dateFormat.format(Date(expense.date))

            // Notes show/hide
            if (expense.notes.isNullOrBlank()) {
                binding.tvNotes.visibility = View.GONE
            } else {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = expense.notes
            }

            binding.btnDelete.setOnClickListener { onDeleteClick(expense) }

            // Category color
            val iconColor = when (expense.category) {
                "Food & Dining"     -> Color.parseColor("#FF6B6B")
                "Transportation"    -> Color.parseColor("#4ECDC4")
                "Accommodation"     -> Color.parseColor("#45B7D1")
                "Activities"        -> Color.parseColor("#FFA07A")
                "Shopping"          -> Color.parseColor("#98D8C8")
                "Entertainment"     -> Color.parseColor("#F7B731")
                "Health & Medical"  -> Color.parseColor("#5F27CD")
                else                -> Color.parseColor("#95A5A6")
            }
            // use tint list instead of legacy color filter
            binding.iconCategory.imageTintList = ColorStateList.valueOf(iconColor)
        }
    }

    private class ExpenseDiffCallback : DiffUtil.ItemCallback<EventExpense>() {
        override fun areItemsTheSame(oldItem: EventExpense, newItem: EventExpense): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EventExpense, newItem: EventExpense): Boolean =
            oldItem == newItem
    }
}

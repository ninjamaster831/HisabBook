package com.guruyuknow.hisabbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


// Expense Adapter
class ExpenseAdapter : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
    private var expenses = listOf<Expense>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val descriptionText: TextView = view.findViewById(R.id.tvDescription)
        val amountText: TextView = view.findViewById(R.id.tvAmount)
        val paidByText: TextView = view.findViewById(R.id.tvPaidBy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = expenses[position]
        holder.descriptionText.text = expense.description
        holder.amountText.text = "₹${String.format("%.2f", expense.amount)}"
        holder.paidByText.text = "Paid by ${expense.paidByName}"
    }

    override fun getItemCount() = expenses.size

    fun submitList(newExpenses: List<Expense>) {
        expenses = newExpenses
        notifyDataSetChanged()
    }
}
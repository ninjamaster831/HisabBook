package com.guruyuknow.hisabbook.group

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R

// Balance Adapter
class BalanceAdapter : RecyclerView.Adapter<BalanceAdapter.ViewHolder>() {
    private var balances = listOf<MemberBalance>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvMemberName)
        val paidText: TextView = view.findViewById(R.id.tvPaid)
        val balanceText: TextView = view.findViewById(R.id.tvBalance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_balance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val balance = balances[position]
        holder.nameText.text = balance.userName
        holder.paidText.text = "Paid: ₹${String.format("%.2f", balance.totalPaid)}"

        val balanceAmount = balance.balance
        holder.balanceText.text = when {
            balanceAmount > 0.01 -> "Gets back: ₹${String.format("%.2f", balanceAmount)}"
            balanceAmount < -0.01 -> "Owes: ₹${String.format("%.2f", -balanceAmount)}"
            else -> "Settled"
        }

        holder.balanceText.setTextColor(
            when {
                balanceAmount > 0.01 -> Color.GREEN
                balanceAmount < -0.01 -> Color.RED
                else -> Color.GRAY
            }
        )
    }

    override fun getItemCount() = balances.size

    fun submitList(newBalances: List<MemberBalance>) {
        balances = newBalances
        notifyDataSetChanged()
    }
}
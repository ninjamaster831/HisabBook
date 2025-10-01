package com.guruyuknow.hisabbook.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R


// Settlement Adapter
class SettlementAdapter : RecyclerView.Adapter<SettlementAdapter.ViewHolder>() {
    private var settlements = listOf<Settlement>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val settlementText: TextView = view.findViewById(R.id.tvSettlement)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_settlement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val settlement = settlements[position]
        holder.settlementText.text =
            "${settlement.fromUserName} pays ₹${String.format("%.2f", settlement.amount)} to ${settlement.toUserName}"
    }

    override fun getItemCount() = settlements.size

    fun submitList(newSettlements: List<Settlement>) {
        settlements = newSettlements
        notifyDataSetChanged()
    }
}
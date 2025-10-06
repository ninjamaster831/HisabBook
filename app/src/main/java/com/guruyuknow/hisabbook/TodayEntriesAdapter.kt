package com.guruyuknow.hisabbook

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Locale

class TodayEntriesAdapter : RecyclerView.Adapter<TodayEntriesAdapter.VH>() {

    private val data = mutableListOf<TodayEntryUi>()
    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun submit(list: List<TodayEntryUi>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
        Log.d("TodayEntriesAdapter", "submit size=${data.size}")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_today_entry, parent, false) // ✅ card layout
        Log.d("TodayEntriesAdapter", "Inflating item_today_entry_card")
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]

        holder.title.text = item.title
        holder.subtitle.text = item.subtitle

        // Amount with +/– prefix and color
        val prefix = if (item.isIncome) "+" else "–"
        holder.amount.text = prefix + currency.format(item.amount)
        val amountColor = ContextCompat.getColor(
            holder.itemView.context,
            if (item.isIncome) R.color.success_green else R.color.error_red
        )
        holder.amount.setTextColor(amountColor)

        // Accent bar + card stroke tint by type
        val accentColor = amountColor
        holder.accent.setBackgroundColor(accentColor)
        (holder.card as MaterialCardView).setStrokeColor(accentColor)
    }

    override fun getItemCount() = data.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView.findViewById(R.id.entryCard)
        val accent: View = itemView.findViewById(R.id.accentBar)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        val amount: TextView = itemView.findViewById(R.id.tvAmount)
    }
}

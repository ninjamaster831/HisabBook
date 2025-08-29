package com.guruyuknow.hisabbook


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CashbookReportAdapter(
    private val entries: MutableList<CashbookEntry>,
    private val onEntryClick: (CashbookEntry) -> Unit
) : RecyclerView.Adapter<CashbookReportAdapter.ReportViewHolder>() {

    private val displayDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_entry, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(entries[position], onEntryClick, displayDateFormatter, timeFormatter)
    }

    override fun getItemCount() = entries.size

    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: ImageView = itemView.findViewById(R.id.typeIcon)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val categoryText: TextView = itemView.findViewById(R.id.categoryText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val paymentMethodIcon: ImageView = itemView.findViewById(R.id.paymentMethodIcon)

        fun bind(
            entry: CashbookEntry,
            onEntryClick: (CashbookEntry) -> Unit,
            displayDateFormatter: SimpleDateFormat,
            timeFormatter: SimpleDateFormat
        ) {
            // Type icon
            typeIcon.setImageResource(
                if (entry.type == "IN") R.drawable.ic_trending_up
                else R.drawable.ic_trending_down
            )

            typeIcon.setColorFilter(
                ContextCompat.getColor(
                    itemView.context,
                    if (entry.type == "IN") R.color.colorGreen else R.color.colorRed
                )
            )

            // Description
            descriptionText.text = entry.description?.takeIf { it.isNotBlank() }
                ?: if (entry.type == "IN") "Money In" else "Money Out"

            // Category
            categoryText.text = entry.category?.takeIf { it.isNotBlank() } ?: "General"

            // Amount with proper sign
            val sign = if (entry.type == "IN") "+" else "-"
            amountText.text = "$sign₹ ${String.format("%.0f", entry.amount)}"
            amountText.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (entry.type == "IN") R.color.colorGreen else R.color.colorRed
                )
            )

            // Date
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(entry.date)
                dateText.text = displayDateFormatter.format(date ?: Date())
            } catch (e: Exception) {
                dateText.text = entry.date
            }

            // Time
            try {
                val createdAt = entry.createdAt
                if (createdAt != null) {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = isoFormat.parse(createdAt)
                    timeText.text = timeFormatter.format(date ?: Date())
                } else {
                    timeText.text = timeFormatter.format(Date())
                }
            } catch (e: Exception) {
                timeText.text = timeFormatter.format(Date())
            }

            // Payment method icon
            paymentMethodIcon.setImageResource(
                if (entry.paymentMethod == "CASH") R.drawable.ic_cash else R.drawable.ic_credit_card
            )

            // Click listener
            itemView.setOnClickListener { onEntryClick(entry) }
        }
    }
}
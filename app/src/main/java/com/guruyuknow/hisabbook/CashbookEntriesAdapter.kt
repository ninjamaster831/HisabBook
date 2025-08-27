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

class CashbookEntriesAdapter(
    private val entries: MutableList<CashbookEntry>,
    private val onEntryClick: (CashbookEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    private val displayDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    // grouped list (headers + entries)
    private val groupedEntries = mutableListOf<Any>()

    init {
        groupEntriesByDate()
    }

    /** regroup entries into groupedEntries */
    private fun groupEntriesByDate() {
        groupedEntries.clear()
        val dateGroups = entries.groupBy { entry ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(entry.date)
                displayDateFormatter.format(date ?: Date())
            } catch (e: Exception) {
                entry.date
            }
        }

        dateGroups.entries.sortedByDescending { it.key }.forEach { (date, entriesForDate) ->
            groupedEntries.add(DateHeader(date, entriesForDate.size))
            groupedEntries.addAll(entriesForDate.sortedByDescending { it.createdAt })
        }
    }

    /** replace data and refresh UI */
    fun setEntries(newEntries: List<CashbookEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        groupEntriesByDate()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (groupedEntries[position]) {
            is DateHeader -> TYPE_DATE_HEADER
            else -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_cashbook_entry, parent, false)
                EntryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DateHeaderViewHolder -> {
                val header = groupedEntries[position] as DateHeader
                holder.bind(header)
            }
            is EntryViewHolder -> {
                val entry = groupedEntries[position] as CashbookEntry
                holder.bind(entry, onEntryClick, timeFormatter)
            }
        }
    }

    override fun getItemCount() = groupedEntries.size

    /** -------------------- ViewHolders -------------------- */

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val entryCountText: TextView = itemView.findViewById(R.id.entryCountText)

        fun bind(header: DateHeader) {
            dateText.text = header.date
            entryCountText.text =
                "${header.entryCount} ${if (header.entryCount == 1) "Entry" else "Entries"}"
        }
    }

    class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryIcon: ImageView = itemView.findViewById(R.id.categoryIcon)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val categoryText: TextView = itemView.findViewById(R.id.categoryText)
        private val paymentMethodText: TextView = itemView.findViewById(R.id.paymentMethodText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(
            entry: CashbookEntry,
            onEntryClick: (CashbookEntry) -> Unit,
            timeFormatter: SimpleDateFormat
        ) {
            // category icon
            categoryIcon.setImageResource(
                if (entry.type == "IN") R.drawable.ic_trending_up
                else R.drawable.ic_trending_down
            )

            // icon color
            categoryIcon.setColorFilter(
                ContextCompat.getColor(
                    itemView.context,
                    if (entry.type == "IN") R.color.colorGreen else R.color.colorRed
                )
            )

            // description
            descriptionText.text = entry.description?.takeIf { it.isNotBlank() }
                ?: if (entry.type == "IN") "Money In" else "Money Out"

            // category
            categoryText.text = entry.category?.takeIf { it.isNotBlank() } ?: "General"

            // payment method + icon
            paymentMethodText.text = entry.paymentMethod
            paymentMethodText.setCompoundDrawablesWithIntrinsicBounds(
                if (entry.paymentMethod == "CASH") R.drawable.ic_cash else R.drawable.ic_credit_card,
                0, 0, 0
            )

            // amount
            val formattedAmount = "₹ ${String.format("%.0f", entry.amount)}"
            amountText.text = formattedAmount
            amountText.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (entry.type == "IN") R.color.colorGreen else R.color.colorRed
                )
            )

            // time
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

            // click
            itemView.setOnClickListener { onEntryClick(entry) }

            itemView.isClickable = true
            itemView.isFocusable = true
        }
    }

    data class DateHeader(
        val date: String,
        val entryCount: Int
    )
}

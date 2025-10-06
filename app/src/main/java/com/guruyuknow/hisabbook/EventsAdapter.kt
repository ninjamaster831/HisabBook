package com.guruyuknow.hisabbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class EventsAdapter(
    private val onClick: (Event) -> Unit
) : ListAdapter<Event, EventsAdapter.VH>(DIFF) {

    // Force English months so you don’t get “सित”
    private val dayFmt = SimpleDateFormat("dd", Locale.ENGLISH)
    private val monthFmt = SimpleDateFormat("MMM", Locale.ENGLISH)
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)

        // Title
        holder.title.text = e.name

        // Dates
        val start = Date(e.startDate)
        val end = e.endDate?.let { Date(it) }

        val mainLine = when {
            end == null || sameDay(start, end) -> dateFmt.format(start)
            else -> "${dateFmt.format(start)} – ${dateFmt.format(end)}"
        }
        val locationPart = e.location?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        holder.date.text = mainLine + locationPart

        // Left pill
        holder.day.text = dayFmt.format(start)
        holder.month.text = monthFmt.format(start).uppercase(Locale.ENGLISH)

        // Click
        holder.itemView.setOnClickListener { onClick(e) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvEventName)
        val date: TextView = itemView.findViewById(R.id.tvEventDate)
        val location: TextView = itemView.findViewById(R.id.tvEventLocation)
        val day: TextView = itemView.findViewById(R.id.tvDay)
        val month: TextView = itemView.findViewById(R.id.tvMonth)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
        }

        private fun sameDay(a: Date, b: Date): Boolean {
            val c1 = Calendar.getInstance().apply { time = a }
            val c2 = Calendar.getInstance().apply { time = b }
            return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                    c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
        }
    }
}

package com.guruyuknow.hisabbook

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class EventsAdapter(
    private val onClick: (Event) -> Unit
) : ListAdapter<Event, EventsAdapter.VH>(DIFF) {

    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(android.R.layout.simple_list_item_2, parent, false) as ViewGroup
        return VH(root)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        // Force readable colors on dark background
        val titleColor = ctx.safeColor(R.color.white, Color.WHITE)
        val subtitleColor = ctx.safeColor(R.color.violet_light, 0xCCFFFFFF.toInt()) // 80% white fallback
        holder.title.setTextColor(titleColor)
        holder.subtitle.setTextColor(subtitleColor)

        holder.title.text = item.name

        val start = df.format(Date(item.startDate))
        val end = item.endDate?.let { " - ${df.format(Date(it))}" }.orEmpty()
        val loc = item.location?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        holder.subtitle.text = start + end + loc

        // Make row obviously tappable
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class VH(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(android.R.id.text1)
        val subtitle: TextView = root.findViewById(android.R.id.text2)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
        }
    }
}

private fun android.content.Context.safeColor(resId: Int, fallback: Int): Int =
    runCatching { ContextCompat.getColor(this, resId) }.getOrElse { fallback }

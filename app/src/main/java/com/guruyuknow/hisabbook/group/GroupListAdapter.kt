package com.guruyuknow.hisabbook.group

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.guruyuknow.hisabbook.R

class GroupListAdapter(
    private val onClick: (Group) -> Unit
) : ListAdapter<Group, GroupListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Group>() {
            override fun areItemsTheSame(oldItem: Group, newItem: Group) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Group, newItem: Group) =
                oldItem == newItem
        }

        // Vibrant color palette for avatars
        private val AVATAR_COLORS = listOf(
            "#6366F1", // Indigo
            "#8B5CF6", // Violet
            "#EC4899", // Pink
            "#F59E0B", // Amber
            "#10B981", // Emerald
            "#06B6D4", // Cyan
            "#EF4444", // Red
            "#3B82F6", // Blue
            "#14B8A6", // Teal
            "#F97316"  // Orange
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onClick: (Group) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cardGroup: MaterialCardView = itemView.findViewById(R.id.cardGroup)
        private val avatarCard: MaterialCardView = itemView.findViewById(R.id.avatarCard)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvGroupInitial: TextView = itemView.findViewById(R.id.tvGroupInitial)

        fun bind(item: Group) {
            // Set group name
            tvName.text = item.name

            // Set group initial (first letter of name)
            val initial = item.name.trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"
            tvGroupInitial.text = initial

            // Set avatar background color based on group name (consistent color for same name)
            val colorIndex = Math.abs(item.name.hashCode()) % AVATAR_COLORS.size
            avatarCard.setCardBackgroundColor(Color.parseColor(AVATAR_COLORS[colorIndex]))

            // Build subtitle with budget and date
            val budgetText = item.budget?.let { "₹${formatMoney(it)} budget" } ?: "No budget"
            val createdAtText = item.createdAt?.let { " • ${formatDate(it)}" } ?: ""
            tvSubtitle.text = budgetText + createdAtText

            // Set click listener on the entire card
            cardGroup.setOnClickListener {
                onClick(item)
            }

            // Ensure ripple effect works
            cardGroup.isClickable = true
            cardGroup.isFocusable = true
        }

        private fun formatMoney(value: Double): String {
            return when {
                value >= 10_000_000 -> {
                    val crores = value / 10_000_000
                    String.format("%.1fCr", crores)
                }
                value >= 100_000 -> {
                    val lakhs = value / 100_000
                    String.format("%.1fL", lakhs)
                }
                value >= 1_000 -> {
                    val thousands = value / 1_000
                    String.format("%.1fK", thousands)
                }
                value % 1.0 == 0.0 -> {
                    value.toInt().toString()
                }
                else -> {
                    String.format("%.2f", value)
                }
            }
        }

        /**
         * Formats date string to display only YYYY-MM-DD
         */
        private fun formatDate(dateStr: String): String {
            return dateStr.take(10)
        }
    }
}
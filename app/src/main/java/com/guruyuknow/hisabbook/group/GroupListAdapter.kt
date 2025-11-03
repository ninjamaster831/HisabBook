package com.guruyuknow.hisabbook.group

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.guruyuknow.hisabbook.R
import kotlin.math.abs

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

        private val AVATAR_COLORS = listOf(
            "#6366F1", "#8B5CF6", "#EC4899", "#F59E0B", "#10B981",
            "#06B6D4", "#EF4444", "#3B82F6", "#14B8A6", "#F97316"
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
        private val ivAvatar: ShapeableImageView = itemView.findViewById(R.id.ivGroupAvatar)
        private val tvInitial: TextView = itemView.findViewById(R.id.tvGroupInitial)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)

        fun bind(item: Group) {
            // Name
            tvName.text = item.name

            // Initial (for fallback)
            val initial = item.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            tvInitial.text = initial

            // Deterministic color per group
            val colorIndex = abs(item.name.hashCode()) % AVATAR_COLORS.size
            avatarCard.setCardBackgroundColor(Color.parseColor(AVATAR_COLORS[colorIndex]))

            // Load image if available, else show initial
            val url = item.imageUrl // <-- make sure your data class has this field
            if (!url.isNullOrBlank()) {
                ivAvatar.visibility = View.VISIBLE
                tvInitial.visibility = View.GONE
                // Using centerCrop; ShapeableImageView with circle shape masks it
                Glide.with(ivAvatar)
                    .load(url)
                    .centerCrop()
                    .placeholder(R.drawable.ic_group_chat) // optional
                    .error(R.drawable.ic_group_chat)
                    .into(ivAvatar)
            } else {
                ivAvatar.visibility = View.GONE
                tvInitial.visibility = View.VISIBLE
            }

            // Subtitle
            val budgetText = item.budget?.let { "₹${formatMoney(it)} budget" } ?: "No budget"
            val createdAtText = item.createdAt?.let { " • ${formatDate(it)}" } ?: ""
            tvSubtitle.text = budgetText + createdAtText

            // Click
            cardGroup.setOnClickListener { onClick(item) }
            cardGroup.isClickable = true
            cardGroup.isFocusable = true
        }

        private fun formatMoney(value: Double): String = when {
            value >= 10_000_000 -> String.format("%.1fCr", value / 10_000_000)
            value >= 100_000 -> String.format("%.1fL", value / 100_000)
            value >= 1_000 -> String.format("%.1fK", value / 1_000)
            value % 1.0 == 0.0 -> value.toInt().toString()
            else -> String.format("%.2f", value)
        }

        private fun formatDate(dateStr: String): String = dateStr.take(10)
    }
}

package com.guruyuknow.hisabbook.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R
import com.guruyuknow.hisabbook.models.NotificationItem
import java.util.concurrent.TimeUnit
import android.text.format.DateUtils
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView

class NotificationsAdapter(
    private val onClick: (NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleTv: TextView? = view.findViewById(R.id.titleTv)
        private val messageTv: TextView? = view.findViewById(R.id.messageTv)
        private val timeTv: TextView? = view.findViewById(R.id.timeTv)
        private val iconView: ImageView? = view.findViewById(R.id.icon)
        private val unreadDot: View? = view.findViewById(R.id.unreadDot)
        private val card: MaterialCardView? = view.findViewById(R.id.card)

        fun bind(item: NotificationItem) {
            titleTv?.text = item.title
            messageTv?.text = item.message
            timeTv?.text = relativeTime(item.createdAt)

            // Load icon based on notification type
            iconView?.let { iv ->
                val iconRes = when (item.type) {
                    "group_message" -> R.drawable.ic_message
                    "payment" -> R.drawable.ic_payment
                    "expense" -> R.drawable.ic_expense
                    "settlement" -> R.drawable.ic_settlement
                    "group_invite" -> R.drawable.ic_group
                    else -> R.drawable.ic_notifications
                }

                if (item.imageUrl != null) {
                    Glide.with(iv.context)
                        .load(item.imageUrl)
                        .placeholder(iconRes)
                        .error(iconRes)
                        .centerCrop()
                        .into(iv)
                } else {
                    iv.setImageResource(iconRes)
                }
            }

            // Unread indicator visibility
            unreadDot?.visibility = if (!item.isRead) View.VISIBLE else View.GONE

            // Card styling for unread vs read
            card?.let { c ->
                if (!item.isRead) {
                    val strokePx = dpToPx(c.context, 2)
                    c.strokeWidth = strokePx
                    val colorInt = ContextCompat.getColor(c.context, R.color.hsb_primary)
                    try {
                        c.strokeColor = colorInt
                    } catch (_: Exception) {
                        // Fallback if property type differs
                    }
                    c.alpha = 1.0f
                } else {
                    c.strokeWidth = 0
                    c.alpha = 0.7f
                }
            }

            // Click handling
            itemView.setOnClickListener {
                onClick(item)
            }
        }

        private fun relativeTime(epochMillis: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - epochMillis
            return when {
                diff < DateUtils.MINUTE_IN_MILLIS -> "Just now"
                diff < DateUtils.HOUR_IN_MILLIS -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
                diff < DateUtils.DAY_IN_MILLIS -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
                diff < DateUtils.WEEK_IN_MILLIS -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
                else -> DateUtils.getRelativeTimeSpanString(
                    epochMillis,
                    now,
                    DateUtils.DAY_IN_MILLIS
                ).toString()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean =
                oldItem == newItem
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
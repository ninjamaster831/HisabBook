package com.guruyuknow.hisabbook.group

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.guruyuknow.hisabbook.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChatMessageAdapter(
    private val myUserIdProvider: () -> String?
) : ListAdapter<GroupMessage, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2

        val DIFF = object : DiffUtil.ItemCallback<GroupMessage>() {
            override fun areItemsTheSame(old: GroupMessage, new: GroupMessage) =
                (old.id ?: 0L) == (new.id ?: 0L)
            override fun areContentsTheSame(old: GroupMessage, new: GroupMessage) = old == new
        }

        private fun formatTime(timestamp: String?): String {
            if (timestamp == null) return ""
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                val date = sdf.parse(timestamp) ?: return ""
                val msgTime = Calendar.getInstance().apply { time = date }
                val now = Calendar.getInstance()
                when {
                    isSameDay(msgTime, now) -> DateFormat.format("h:mm a", msgTime).toString()
                    isYesterday(msgTime, now) -> "Yesterday"
                    isSameWeek(msgTime, now) -> DateFormat.format("EEEE", msgTime).toString()
                    else -> DateFormat.format("MMM dd", msgTime).toString()
                }
            } catch (_: Exception) { "" }
        }

        private fun isSameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        private fun isYesterday(cal1: Calendar, cal2: Calendar): Boolean {
            val y = cal2.clone() as Calendar
            y.add(Calendar.DAY_OF_YEAR, -1)
            return isSameDay(cal1, y)
        }

        private fun isSameWeek(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)

        /** Shared binder for text vs image messages */
        fun bindTextOrImage(msg: GroupMessage, tvMessage: TextView, ivMedia: ImageView) {
            val isImage = msg.mediaType == "image" && !msg.mediaUrl.isNullOrBlank()

            if (isImage) {
                ivMedia.visibility = View.VISIBLE
                ivMedia.load(msg.mediaUrl) { crossfade(true) }

                val caption = msg.message.takeIf { it.isNotBlank() && it != "[image]" }
                tvMessage.text = caption ?: ""
                tvMessage.visibility = if (caption != null) View.VISIBLE else View.GONE

                ivMedia.setOnClickListener { v ->
                    val ctx = v.context
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(msg.mediaUrl), "image/*")
                    }
                    ctx.startActivity(intent)
                }
            } else {
                ivMedia.visibility = View.GONE
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = msg.message
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        val myId = myUserIdProvider()
        return if (msg.senderId == myId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentVH(inflater.inflate(R.layout.item_chat_right, parent, false))
            else -> ReceivedVH(inflater.inflate(R.layout.item_chat_left, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is SentVH -> holder.bind(msg)
            is ReceivedVH -> holder.bind(msg)
        }
    }

    class ReceivedVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvSender: TextView? = view.findViewById(R.id.tvSender)
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        private val ivAvatar: ShapeableImageView? = view.findViewById(R.id.ivAvatar)
        private val ivMedia: ImageView = view.findViewById(R.id.ivMedia)

        fun bind(msg: GroupMessage) {
            tvTimestamp.text = formatTime(msg.createdAt)
            tvSender?.text = msg.senderName ?: "Unknown"
            ivAvatar?.setImageResource(R.drawable.ic_person_modern)
            Companion.bindTextOrImage(msg, tvMessage, ivMedia)
        }
    }

    class SentVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        private val tvStatus: TextView? = view.findViewById(R.id.tvStatus)
        private val ivMedia: ImageView = view.findViewById(R.id.ivMedia)

        fun bind(msg: GroupMessage) {
            tvTimestamp.text = formatTime(msg.createdAt)
            Companion.bindTextOrImage(msg, tvMessage, ivMedia)
            tvStatus?.text = "✓✓"
            tvStatus?.visibility = View.VISIBLE
        }
    }
}

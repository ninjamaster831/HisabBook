package com.guruyuknow.hisabbook.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruyuknow.hisabbook.R

class ChatMessageAdapter(
    private val myUserIdProvider: () -> String?
) : ListAdapter<GroupMessage, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val LEFT = 1
        private const val RIGHT = 2

        val DIFF = object : DiffUtil.ItemCallback<GroupMessage>() {
            override fun areItemsTheSame(old: GroupMessage, new: GroupMessage) = old.id == new.id
            override fun areContentsTheSame(old: GroupMessage, new: GroupMessage) = old == new
        }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        val me = myUserIdProvider()
        return if (msg.senderId == me) RIGHT else LEFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == RIGHT) {
            RightVH(inflater.inflate(R.layout.item_chat_right, parent, false))
        } else {
            LeftVH(inflater.inflate(R.layout.item_chat_left, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is LeftVH -> holder.bind(msg)
            is RightVH -> holder.bind(msg)
        }
    }

    class LeftVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMsg)
        fun bind(m: GroupMessage) { tv.text = m.message }
    }
    class RightVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMsg)
        fun bind(m: GroupMessage) { tv.text = m.message }
    }
}

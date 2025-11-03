package com.guruyuknow.hisabbook.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.guruyuknow.hisabbook.R

class GroupMemberAdapter(
    private val onMemberClick: (GroupMemberWithUser) -> Unit,
    private val onRemoveMember: (GroupMemberWithUser) -> Unit,
    private val onMakeAdmin: (GroupMemberWithUser) -> Unit,
    private val currentUserId: String?,
    private var isCurrentUserAdmin: Boolean
) : ListAdapter<GroupMemberWithUser, GroupMemberAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivMemberAvatar)
        val tvName: TextView = view.findViewById(R.id.tvMemberName)
        val tvEmail: TextView = view.findViewById(R.id.tvMemberEmail)
        val tvRole: TextView = view.findViewById(R.id.tvMemberRole)
        val adminBadge: MaterialCardView = view.findViewById(R.id.adminBadge)
        val onlineIndicator: View = view.findViewById(R.id.onlineIndicator)
        val btnMakeAdmin: MaterialButton = view.findViewById(R.id.btnMakeAdmin)
        val btnMemberOptions: MaterialButton = view.findViewById(R.id.btnMemberOptions)
        val btnRemove: MaterialButton = view.findViewById(R.id.btnRemoveMember)
        val root: View = view
    }

    fun updateAdminStatus(isAdmin: Boolean) {
        isCurrentUserAdmin = isAdmin
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member_modern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position)
        val isCurrentUser = currentUserId == member.userId

        // Set member info
        holder.tvName.text = member.userName

        // Show email if available
        if (!member.userEmail.isNullOrBlank()) {
            holder.tvEmail.text = member.userEmail
            holder.tvEmail.visibility = View.VISIBLE
        } else {
            holder.tvEmail.visibility = View.GONE
        }

        // Show admin badge
        if (member.isAdmin) {
            holder.adminBadge.visibility = View.VISIBLE
            holder.tvRole.visibility = View.GONE
        } else {
            holder.adminBadge.visibility = View.GONE

            // Show joined date if available
            if (!member.joinedAt.isNullOrBlank()) {
                holder.tvRole.text = "Joined ${formatJoinedDate(member.joinedAt)}"
                holder.tvRole.visibility = View.VISIBLE
            } else {
                holder.tvRole.visibility = View.GONE
            }
        }

        // Show online indicator (optional - can be connected to real-time presence)
        holder.onlineIndicator.visibility = View.GONE

        // Handle action buttons
        if (isCurrentUserAdmin && !isCurrentUser) {
            // Show admin controls for other members
            holder.btnMemberOptions.visibility = View.VISIBLE

            // Show make/remove admin button
            if (member.isAdmin) {
                holder.btnMakeAdmin.visibility = View.VISIBLE
                holder.btnMakeAdmin.setIconResource(R.drawable.ic_star_filled)
                holder.btnMakeAdmin.setIconTintResource(R.color.amber_500)
            } else {
                holder.btnMakeAdmin.visibility = View.VISIBLE
                holder.btnMakeAdmin.setIconResource(R.drawable.ic_star)
                holder.btnMakeAdmin.setIconTintResource(R.color.slate_400)
            }

            // Show remove button
            holder.btnRemove.visibility = View.VISIBLE
        } else {
            // Hide all action buttons for non-admins or current user
            holder.btnMakeAdmin.visibility = View.GONE
            holder.btnMemberOptions.visibility = View.GONE
            holder.btnRemove.visibility = View.GONE
        }

        // Set click listeners
        holder.root.setOnClickListener {
            if (isCurrentUserAdmin) {
                onMemberClick(member)
            }
        }

        holder.btnMakeAdmin.setOnClickListener {
            onMakeAdmin(member)
        }

        holder.btnMemberOptions.setOnClickListener {
            onMemberClick(member)
        }

        holder.btnRemove.setOnClickListener {
            onRemoveMember(member)
        }

        // Load avatar if available
        // TODO: Use Glide or Coil for image loading
        // Glide.with(holder.itemView)
        //     .load(member.avatarUrl)
        //     .placeholder(R.drawable.ic_person)
        //     .circleCrop()
        //     .into(holder.ivAvatar)
    }

    private fun formatJoinedDate(timestamp: String): String {
        val inputs = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )

        val output = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
        output.timeZone = java.util.TimeZone.getDefault()

        for (pattern in inputs) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                if (pattern.endsWith("'Z'")) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(timestamp)
                if (date != null) return output.format(date)
            } catch (_: Exception) {
            }
        }
        return timestamp
    }

    class DiffCallback : DiffUtil.ItemCallback<GroupMemberWithUser>() {
        override fun areItemsTheSame(
            oldItem: GroupMemberWithUser,
            newItem: GroupMemberWithUser
        ) = oldItem.userId == newItem.userId

        override fun areContentsTheSame(
            oldItem: GroupMemberWithUser,
            newItem: GroupMemberWithUser
        ) = oldItem == newItem
    }
}
package com.guruyuknow.hisabbook

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruyuknow.hisabbook.adapters.NotificationsAdapter
import com.guruyuknow.hisabbook.databinding.ActivityNotificationsBinding
import com.guruyuknow.hisabbook.group.GroupChatFragment
import com.guruyuknow.hisabbook.models.NotificationItem
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Status bar padding for appBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = topInset)
            insets
        }

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Notifications"
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Get current user
        userId = SupabaseManager.client.auth.currentUserOrNull()?.id

        // RecyclerView + adapter
        adapter = NotificationsAdapter { item ->
            handleNotificationClick(item)
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadNotifications()
        }

        // Mark all read button
        binding.btnMarkAllRead.setOnClickListener {
            markAllAsRead()
        }

        // Initial load
        binding.swipeRefresh.isRefreshing = true
        loadNotifications()
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            try {
                val uid = userId
                if (!uid.isNullOrBlank()) {
                    val result = SupabaseManager.getNotificationsForUser(uid)
                    val list = result.getOrNull() ?: emptyList()

                    adapter.submitList(list)

                    // Show/hide empty view
                    binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvNotifications.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

                    // Show/hide mark all read button
                    val hasUnread = list.any { !it.isRead }
                    binding.btnMarkAllRead.visibility = if (hasUnread) View.VISIBLE else View.GONE
                } else {
                    adapter.submitList(emptyList())
                    binding.emptyView.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                    binding.btnMarkAllRead.visibility = View.GONE
                    Toast.makeText(this@NotificationsActivity, "Please sign in to view notifications", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NotificationsActivity, "Failed to load notifications", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun handleNotificationClick(item: NotificationItem) {
        lifecycleScope.launch {
            try {
                // Mark as read if not already
                if (!item.isRead) {
                    SupabaseManager.markNotificationRead(item.id)
                    loadNotifications() // Refresh list
                }

                // Navigate based on notification type
                when (item.type) {
                    "group_message" -> {
                        item.referenceId?.let { groupId ->
                            // Open GroupChatFragment
                            openGroupChatFragment(groupId)
                        }
                    }
                    "payment", "expense", "settlement" -> {
                        item.referenceId?.let { transactionId ->
                            // Open transaction details or group
                            Toast.makeText(this@NotificationsActivity, "Opening transaction details...", Toast.LENGTH_SHORT).show()
                            // TODO: Navigate to transaction details
                        }
                    }
                    "group_invite" -> {
                        item.referenceId?.let { groupId ->
                            // Open group details
                            Toast.makeText(this@NotificationsActivity, "Opening group...", Toast.LENGTH_SHORT).show()
                            // TODO: Navigate to group details
                        }
                    }
                    else -> {
                        Toast.makeText(this@NotificationsActivity, item.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NotificationsActivity, "Failed to open notification", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGroupChatFragment(groupId: Long) {
        // Create bundle with group ID
        val bundle = Bundle().apply {
            putLong("GROUP_ID", groupId)
        }

        // Create fragment instance
        val fragment = GroupChatFragment().apply {
            arguments = bundle
        }

        // Replace current activity with fragment container
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()

        // Finish this activity after a short delay to allow fragment transaction
        binding.root.postDelayed({
            finish()
        }, 100)
    }

    private fun markAllAsRead() {
        lifecycleScope.launch {
            try {
                val uid = userId
                if (uid != null) {
                    binding.swipeRefresh.isRefreshing = true
                    val result = SupabaseManager.markAllNotificationsRead(uid)
                    if (result.isSuccess) {
                        loadNotifications()
                        Toast.makeText(this@NotificationsActivity, "All notifications marked as read", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@NotificationsActivity, "Failed to mark as read", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@NotificationsActivity, "User not signed in", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NotificationsActivity, "Error marking notifications as read", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh notifications when returning to the activity
        loadNotifications()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
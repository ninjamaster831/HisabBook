package com.guruyuknow.hisabbook.models

data class NotificationItem(
    val id: String,
    val userId: String? = null,
    val title: String,
    val message: String,
    val type: String = "general", // "group_message", "payment", "expense", "settlement", "group_invite", "general"
    val referenceId: Long? = null, // group_id or transaction_id
    val referenceType: String? = null, // "group", "transaction"
    val createdAt: Long,
    val isRead: Boolean = false,
    val imageUrl: String? = null
)
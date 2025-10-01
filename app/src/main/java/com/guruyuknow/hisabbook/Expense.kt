package com.guruyuknow.hisabbook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model: Expense
@Serializable
data class Expense(
    val id: Long? = null,
    @SerialName("group_id")
    val groupId: Long,
    val amount: Double,
    val description: String,
    @SerialName("paid_by")
    val paidBy: String,
    @SerialName("paid_by_name")
    val paidByName: String,
    @SerialName("created_at")
    val createdAt: String? = null // ISO timestamp from Supabase
)
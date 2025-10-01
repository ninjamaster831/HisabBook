package com.guruyuknow.hisabbook.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model: Member Balance
@Serializable
data class MemberBalance(
    val id: Long? = null,
    @SerialName("group_id")
    val groupId: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("total_paid")
    val totalPaid: Double = 0.0,
    @SerialName("total_owed")
    val totalOwed: Double = 0.0,
    val balance: Double = 0.0,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

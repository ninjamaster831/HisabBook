package com.guruyuknow.hisabbook.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model: Group Member
@Serializable
data class GroupMember(
    val id: Long? = null,
    @SerialName("group_id")
    val groupId: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("joined_at")
    val joinedAt: String? = null // ISO timestamp from Supabase
)
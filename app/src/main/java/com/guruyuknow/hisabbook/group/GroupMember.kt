package com.guruyuknow.hisabbook.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model: Group Member
// REPLACE your existing GroupMember data class with this:

@Serializable
data class GroupMember(
    val id: Long? = null,
    @SerialName("group_id")
    val groupId: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String? = null,  // ✅ Made nullable for JOIN queries
    @SerialName("is_admin")
    val isAdmin: Boolean = false,  // ✅ NEW FIELD
    @SerialName("joined_at")
    val joinedAt: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,  // ✅ NEW FIELD
    @SerialName("user_email")
    val userEmail: String? = null  // ✅ NEW FIELD (optional, for fallback display)
)
package com.guruyuknow.hisabbook.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ---------- DB ROW MODELS (Serializable) ---------- */

// Row as stored in public.group_messages
@Serializable
data class GroupMessageRow(
    val id: Long? = null,
    @SerialName("group_id") val groupId: Long,
    @SerialName("sender_id") val senderId: String,
    val message: String,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)


// Row from public.user_profiles (minimal fields we need)
@Serializable
data class UserProfileRow(
    val id: String, // MUST be present so we can map; in your table it’s uuid
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

/** ---------- UI MODEL (Plain Kotlin) ---------- */

data class GroupMessage(
    val id: Long? = null,
    val groupId: Long,
    val senderId: String,
    val senderName: String?,
    val message: String,
    val createdAt: String? = null,
    val mediaType: String? = null,
    val mediaUrl: String? = null,
    val avatarUrl: String? = null
)

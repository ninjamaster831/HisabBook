// GroupMessageInsert.kt
package com.guruyuknow.hisabbook.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupMessageInsert(
    @SerialName("group_id") val groupId: Long,
    @SerialName("sender_id") val senderId: String,
    val message: String
)

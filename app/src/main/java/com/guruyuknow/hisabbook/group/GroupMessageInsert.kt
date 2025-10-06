// GroupMessageInsert.kt
package com.guruyuknow.hisabbook.group

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupMessageInsert(
    @SerialName("group_id") val groupId: Long,
    @SerialName("sender_id") val senderId: String,
    val message: String,
    @SerialName("media_type") val media_type: String? = null,
    @SerialName("media_url")  val media_url: String? = null,
    @SerialName("extra_json") val extra_json: Map<String, @Contextual Any?>? = null
)

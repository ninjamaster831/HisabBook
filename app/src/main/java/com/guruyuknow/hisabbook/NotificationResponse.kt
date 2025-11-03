package com.guruyuknow.hisabbook

import com.guruyuknow.hisabbook.models.NotificationItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class NotificationResponse(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val message: String,
    val type: String = "general",
    @SerialName("reference_id")
    val referenceId: Long? = null,
    @SerialName("reference_type")
    val referenceType: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("image_url")
    val imageUrl: String? = null
) {
    fun toNotificationItem(): NotificationItem {
        return NotificationItem(
            id = id,
            userId = userId,
            title = title,
            message = message,
            type = type,
            referenceId = referenceId,
            referenceType = referenceType,
            createdAt = parseTimestamp(createdAt),
            isRead = isRead,
            imageUrl = imageUrl
        )
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            e.printStackTrace()
            System.currentTimeMillis()
        }
    }
}
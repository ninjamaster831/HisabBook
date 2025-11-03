// Data Models for Group Expense Management with Supabase
package com.guruyuknow.hisabbook.group
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Model: Group
@Serializable
data class Group(
    val id: Long? = null,
    val name: String,
    val budget: Double? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("image_url")
    val imageUrl: String? = null,

    @SerialName("admin_only")
    val adminOnly: Boolean = false,

    @SerialName("created_by")
    val createdBy: String,

    @SerialName("created_at")
    val createdAt: String? = null, // ISO timestamp from Supabase

    @SerialName("is_active")
    val isActive: Boolean = true,

    @SerialName("join_code")
    val joinCode: String? = null
)

// Data class for settlement information
data class Settlement(
    val fromUser: String,
    val fromUserName: String,
    val toUser: String,
    val toUserName: String,
    val amount: Double
)

// Data class for group statistics
data class GroupStatistics(
    val totalExpenses: Double,
    val memberCount: Int,
    val perPersonShare: Double,
    val budget: Double?,
    val remainingBudget: Double?
)

// Request models for creating entities
data class CreateGroupRequest(
    val name: String,
    val budget: Double?,
    val createdBy: String
)

data class CreateMemberRequest(
    val groupId: Long,
    val userId: String,
    val userName: String
)

data class CreateExpenseRequest(
    val groupId: Long,
    val amount: Double,
    val description: String,
    val paidBy: String,
    val paidByName: String
)
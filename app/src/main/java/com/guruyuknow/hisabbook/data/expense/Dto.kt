package com.guruyuknow.hisabbook.data.expense

import kotlinx.serialization.Serializable

@Serializable
data class GroupInsert(
    val name: String,
    val budget: Double? = null,
    val created_by: String,
    val is_active: Boolean = true
)

@Serializable
data class GroupIdOnly(
    val id: Long
)

@Serializable
data class GroupMemberInsert(
    val group_id: Long,
    val user_id: String,
    val user_name: String
)

@Serializable
data class MemberBalanceInsert(
    val group_id: Long,
    val user_id: String,
    val user_name: String,
    val total_paid: Double = 0.0,
    val total_owed: Double = 0.0,
    val balance: Double = 0.0
)

@Serializable
data class MemberBalanceUpdate(
    val total_paid: Double,
    val total_owed: Double,
    val balance: Double
)

@Serializable
data class ExpenseInsert(
    val group_id: Long,
    val amount: Double,
    val description: String,
    val paid_by: String,
    val paid_by_name: String
)

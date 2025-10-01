package com.guruyuknow.hisabbook

import java.util.UUID

data class Event(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val location: String? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class EventExpense(
    val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val title: String,
    val amount: Double,
    val category: String,
    val notes: String? = null,
    val paidBy: String,
    val date: Long,
    val createdAt: Long = System.currentTimeMillis()
)
package com.guruyuknow.hisabbook.Staff

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Salary @RequiresApi(Build.VERSION_CODES.O) constructor(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("staff_id") val staffId: String,
    @SerialName("business_owner_id") val businessOwnerId: String,
    val month: String, // YYYY-MM format
    @SerialName("basic_salary") val basicSalary: Double,
    @SerialName("total_days") val totalDays: Int,
    @SerialName("present_days") val presentDays: Int,
    @SerialName("absent_days") val absentDays: Int,
    @SerialName("half_days") val halfDays: Int,
    @SerialName("late_days") val lateDays: Int,
    @SerialName("calculated_salary") val calculatedSalary: Double,
    val bonus: Double = 0.0,
    val deductions: Double = 0.0,
    @SerialName("final_salary") val finalSalary: Double,
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("paid_date") val paidDate: String? = null,
    @SerialName("created_at") val createdAt: String = Date().toInstant().toString()
)
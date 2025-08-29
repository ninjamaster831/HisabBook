package com.guruyuknow.hisabbook.Staff

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Staff @RequiresApi(Build.VERSION_CODES.O) constructor(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("business_owner_id") val businessOwnerId: String,
    val name: String,
    @SerialName("phone_number") val phoneNumber: String,
    val email: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,  // Added SerialName
    @SerialName("salary_type") val salaryType: SalaryType,  // Added SerialName
    @SerialName("salary_amount") val salaryAmount: Double,  // Added SerialName
    @SerialName("salary_start_date") val salaryStartDate: String, // Added SerialName, ISO date format
    @SerialName("has_attendance_permission") val hasAttendancePermission: Boolean = false,  // Added SerialName
    @SerialName("has_salary_permission") val hasSalaryPermission: Boolean = false,  // Added SerialName
    @SerialName("has_business_permission") val hasBusinessPermission: Boolean = false,  // Added SerialName
    @SerialName("is_active") val isActive: Boolean = true,  // Added SerialName
    @SerialName("created_at") val createdAt: String = Date().toInstant().toString(),  // Added SerialName
    @SerialName("updated_at") val updatedAt: String = Date().toInstant().toString()  // Added SerialName
)

enum class SalaryType {
    MONTHLY, DAILY
}
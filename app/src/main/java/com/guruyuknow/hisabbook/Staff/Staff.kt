package com.guruyuknow.hisabbook.Staff

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Staff(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("business_owner_id") val businessOwnerId: String,
    val name: String,
    @SerialName("phone_number") val phoneNumber: String,
    val email: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    @SerialName("salary_type") val salaryType: SalaryType,
    @SerialName("salary_amount") val salaryAmount: Double,
    @SerialName("salary_start_date") val salaryStartDate: String,
    @SerialName("has_attendance_permission") val hasAttendancePermission: Boolean = false,
    @SerialName("has_salary_permission") val hasSalaryPermission: Boolean = false,
    @SerialName("has_business_permission") val hasBusinessPermission: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = getCurrentTimestamp(),
    @SerialName("updated_at") val updatedAt: String = getCurrentTimestamp()
)

private fun getCurrentTimestamp(): String {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.now().toString()
        } else {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }
    } catch (e: Exception) {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}
package com.guruyuknow.hisabbook.Staff

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Attendance @RequiresApi(Build.VERSION_CODES.O) constructor(
    val id: String = UUID.randomUUID().toString(),
    @SerialName("staff_id") val staffId: String,
    @SerialName("business_owner_id") val businessOwnerId: String,
    val date: String, // YYYY-MM-DD format
    val status: AttendanceStatus,
    @SerialName("check_in_time") val checkInTime: String? = null,
    @SerialName("check_out_time") val checkOutTime: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String = Date().toInstant().toString()
)

enum class AttendanceStatus {
    PRESENT, ABSENT, HALF_DAY, LATE
}
@RequiresApi(Build.VERSION_CODES.O)
private fun getCurrentTimestamp(): String {
    return java.time.Instant.now().toString()
}
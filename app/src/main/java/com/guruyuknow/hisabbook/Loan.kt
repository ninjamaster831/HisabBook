package com.guruyuknow.hisabbook

import kotlinx.serialization.Serializable

@Serializable
data class Loan(
    val id: String,
    val user_id: String? = null,
    val friend_name: String,
    val phone_number: String,
    val amount: Double,
    val notes: String = "",
    val date_created: Long,
    val reminder_date_time: Long? = null,
    val is_paid: Boolean = false,

    // Add these to match what PostgREST returns:
    val created_at: String? = null,   // timestamptz → ISO string (or use Instant with a serializer)
    val updated_at: String? = null
)

// File: com/guruyuknow/hisabbook/Bills/Bill.kt
package com.guruyuknow.hisabbook.Bills

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bill(
    @SerialName("id")
    val id: String? = null,

    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("cashbook_entry_id")
    val cashbookEntryId: String? = null,

    @SerialName("confidence_score")
    val confidenceScore: Double? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("extracted_amount")
    val extractedAmount: Double? = null,

    @SerialName("image_url")
    val imageUrl: String? = null,

    @SerialName("image_name")
    val imageName: String? = null,

    @SerialName("extracted_text")
    val extractedText: String? = null
)

package com.guruyuknow.hisabbook.Staff

enum class SalaryType {
    MONTHLY, DAILY;

    // Custom method to get a display-friendly name
    fun getDisplayName(): String {
        return when (this) {
            MONTHLY -> "Monthly"
            DAILY -> "Daily"

        }
    }
}

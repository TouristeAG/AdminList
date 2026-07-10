package com.eventmanager.app.ui.components

enum class TimePeriod(val displayName: String, val days: Long, val unitLabel: String) {
    ONE_WEEK("1 Week", 7, "Day"),
    TWO_WEEKS("2 Weeks", 14, "Day"),
    ONE_MONTH("1 Month", 30, "Day"),
    SIX_MONTHS("6 Months", 180, "Week"),
    ONE_YEAR("1 Year", 365, "Month"),
    MAX("All Time", 0, "Dynamic")
}

data class DataPoint(
    val label: String,
    val value: Float,
    val timestamp: Long
)

data class AnnouncementDisplay(
    val venueName: String,
    val title: String,
    val message: String,
    val sentAt: Long,
    val venueKey: String
)

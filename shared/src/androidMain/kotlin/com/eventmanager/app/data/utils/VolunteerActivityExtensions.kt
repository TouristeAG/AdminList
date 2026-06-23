package com.eventmanager.app.data.utils

import android.content.Context
import com.eventmanager.app.data.models.Volunteer

fun VolunteerActivityManager.getActivityStatusText(volunteer: Volunteer, @Suppress("UNUSED_PARAMETER") context: Context): String {
    val daysSince = getDaysSinceLastShift(volunteer) ?: return "Never worked"
    return when {
        daysSince == 0L -> "Active today"
        daysSince < 30 -> "Active ${daysSince}d ago"
        daysSince < 365 -> "Active ${daysSince / 30}mo ago"
        else -> "Inactive ${daysSince / 365}y ago"
    }
}

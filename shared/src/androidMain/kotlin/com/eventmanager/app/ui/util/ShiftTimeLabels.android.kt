package com.eventmanager.app.ui.util

import android.content.Context
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.ShiftTime
import com.eventmanager.app.R

fun Context.shiftTimeLabel(shiftTime: ShiftTime): String = getString(
    when (shiftTime) {
        ShiftTime.BEFORE_MIDNIGHT -> R.string.shift_time_evening_profited
        ShiftTime.AFTER_MIDNIGHT -> R.string.shift_time_evening_non_profited
    }
)

fun Context.shiftTimeLabelIfRelevant(job: Job, jobTypeConfigs: List<JobTypeConfig>): String? {
    return if (isShiftTimeRelevantForJob(job, jobTypeConfigs)) shiftTimeLabel(job.shiftTime) else null
}
